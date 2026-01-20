#include "../include/StompProtocol.h"
#include <sstream>
#include <iostream>
#include "../include/event.h" 
#include <fstream>
#include <algorithm>
#include <map>

using namespace std;

StompProtocol::StompProtocol() : 
    _isLoggedIn(false),           
    _shouldTerminate(false),      
    _subIdCounter(0),             
    _receiptIdCounter(0),
    _disconnectReceiptId(-1) {}

namespace {
string normalizeTopic(string topic) {
    if (!topic.empty() && topic[0] == '/') {
        topic.erase(0, 1);
    }
    return topic;
}
}

bool StompProtocol::shouldTerminate(){
    return _shouldTerminate;
}

string StompProtocol::processKeyboardInput(string message) {
    std::stringstream ss(message);
    string command;
    ss >> command;
    
    if (!isCommandValid(command, message)) {
        std::cout << "DEBUG: Command validation failed for: " << command << std::endl;
        return ""; 
    }
    
    std::stringstream ss2(message);
    ss2 >> command;  
    
    
    if (command == "login")  return createConnectFrame(ss2);
    if (command == "join")   return createSubscribeFrame(ss2);
    if (command == "exit")   return createUnsubscribeFrame(ss2);
    if (command == "report") return createSendFrame(ss2);
    if (command == "logout") return createDisconnectFrame(ss2);
    if (command == "summary") return createSummary(ss2);

    std::cout << "Unknown command: " << command << std::endl;
    return ""; 
}

void StompProtocol::processServerResponse(string message) {

   // std::lock_guard<std::mutex> lock(_lock);

    StompFrame frame = StompFrame::parse(message);
    string command = frame.getCommand();

    if (command == "CONNECTED") handleConnected();
    else if (command == "MESSAGE")   handleMessage(frame);
    else if (command == "RECEIPT")   handleReceipt(frame);
    else if (command == "ERROR")     handleError(frame);
}

// --- Handlers ---

void StompProtocol::handleConnected() {
    std::lock_guard<std::mutex> lock(_lock);
    _isLoggedIn = true;
    cout << "Login successful" << endl;
}

void StompProtocol::handleMessage(const StompFrame& frame) {
    std::string dest = frame.getHeader("destination");
    if (!dest.empty() && dest[0] == '/') {
        dest = dest.substr(1);
    }
    std::string reportingUser;
   
    Event event("", "", "", 0, {}, {}, {}, ""); 
    
    parseEventFromBody(frame.getBody(), reportingUser, event);

    {
        std::lock_guard<std::mutex> lock(_lock);
        if (reportingUser != _currentUserName) {
            _gameReports[dest][reportingUser].push_back(event);
        }
      //  _gameReports[dest][reportingUser].push_back(event);
    }
 //   _gameReports[dest][reportingUser].push_back(event);

    std::cout << "[" << dest << "]: " << frame.getBody() << std::endl;
}

void StompProtocol::handleReceipt(const StompFrame& frame) {
    std::lock_guard<std::mutex> lock(_lock);
    std::string receiptIdStr = frame.getHeader("receipt-id");
    int rId = safeStoi(receiptIdStr);

    if (_receiptToActions.count(rId) && _receiptToActions[rId] == "DISCONNECT") {
        std::cout << "Logout successful. Closing connection..." << std::endl;
        _isLoggedIn = false;
        _shouldTerminate = true; 
        
        std::exit(0);
        _receiptToActions.erase(rId);
    } else {
        if (_receiptToActions.count(rId)) {
            std::cout << "Receipt received: " << _receiptToActions[rId] << std::endl;
            _receiptToActions.erase(rId);
        }
    }
}

// void StompProtocol::handleError(const StompFrame& frame) {
//     std::string errorMsg = frame.getHeader("message");
//     std::cout << "\n--- SERVER ERROR RECEIVED ---" << std::endl;
//     std::cout << "Message: " << errorMsg << std::endl;
//     if (!frame.getBody().empty()) {
//         std::cout << "Details: " << frame.getBody() << std::endl;
//     }
    
//     _shouldTerminate = true; 
//     _isLoggedIn = false;
// }
void StompProtocol::handleError(const StompFrame& frame) {
    std::lock_guard<std::mutex> lock(_lock);
    std::string errorMsg = frame.getHeader("message");

    std::cout << "Message: " << frame.getHeader("message") << std::endl;
    std::cout << "--- SERVER ERROR RECEIVED ---" << std::endl;
    std::cout << "Message: " << errorMsg << std::endl;
    if (!frame.getBody().empty()) {
         std::cout << "Details: " << frame.getBody() << std::endl;
    }

    _isPendingDisconnect = true; 
}

string StompProtocol::createConnectFrame(stringstream& ss) {

    std::lock_guard<std::mutex> lock(_lock);

    if (_isLoggedIn) {
        cout << "The client is already logged in" << endl;
        return "";
    }
    string hostPort, username, password;
    ss >> hostPort >> username >> password;

    _currentUserName = username;

    StompFrame frame("CONNECT");
    frame.addHeader("accept-version", "1.2");
    frame.addHeader("host", "stomp.cs.bgu.ac.il"); 
    frame.addHeader("login", username);
    frame.addHeader("passcode", password);

    return frame.toString();
}

string StompProtocol::createSubscribeFrame(stringstream& ss) {

    std::lock_guard<std::mutex> lock(_lock);

    string topic;
    ss >> topic;

    topic = normalizeTopic(topic);

    int subId = _subIdCounter++;
    _subscriptions[subId] = topic; 

    int rId = _receiptIdCounter++;
    _receiptToActions[rId] = "Joined channel " + topic;

    StompFrame frame("SUBSCRIBE");
    frame.addHeader("destination", "/" + topic);
    frame.addHeader("id", to_string(subId));
    frame.addHeader("receipt", to_string(rId));

    return frame.toString();
}

string StompProtocol::createUnsubscribeFrame(stringstream& ss) {

    std::lock_guard<std::mutex> lock(_lock);

    string topic;
    ss >> topic;
    topic = normalizeTopic(topic);
    int subId = -1;

    for (auto const& [id, g] : _subscriptions) {
        if (g == topic) {
            subId = id;
            break;
        }
    }

    if (subId == -1) {
        cout << "Error: Not subscribed to " << topic << endl;
        return "";
    }

    int rId = _receiptIdCounter++;
    _receiptToActions[rId] = "Exited channel " + topic;

    StompFrame frame("UNSUBSCRIBE");
    frame.addHeader("id", to_string(subId));
    frame.addHeader("receipt", to_string(rId));

    _subscriptions.erase(subId); 
    return frame.toString();
}


std::string StompProtocol::createSendFrame(std::stringstream& ss) {

    std::lock_guard<std::mutex> lock(_lock);

    std::string fileName;
    if (!(ss >> fileName)) return ""; 

    names_and_events parsedData;
    try {
        parsedData = parseEventsFile(fileName);
    } catch (...) {
        std::cout << "Error: Could not parse file " << fileName << std::endl;
        return "";
    }

    std::string game_name = normalizeTopic(parsedData.team_a_name + "_" + parsedData.team_b_name); 
    std::string allFrames = "";
    bool firstFrame = true;

    for (const Event& e : parsedData.events) { 
        _gameReports[game_name][_currentUserName].push_back(e); 

        StompFrame frame("SEND");
        frame.addHeader("destination", "/" + game_name);
        if (firstFrame) {
            frame.addHeader("filename", fileName);
        }
        
        std::string body = "user: " + _currentUserName + "\n"; 
        body += "team a: " + parsedData.team_a_name + "\n"; 
        body += "team b: " + parsedData.team_b_name + "\n"; 
        body += "event name: " + e.get_name() + "\n"; 
        body += "time: " + std::to_string(e.get_time()) + "\n"; 
        
        body += "general game updates:\n"; 
        for (auto const& [key, value] : e.get_game_updates()) { 
            body += "    " + key + ": " + value + "\n"; 
        }
        
        body += "team a updates:\n"; 
        for (auto const& [key, value] : e.get_team_a_updates()) {
            body += "    " + key + ": " + value + "\n";
        }

        body += "team b updates:\n"; 
        for (auto const& [key, value] : e.get_team_b_updates()) {
            body += "    " + key + ": " + value + "\n";
        }

        body += "description:\n" + e.get_discription(); 

        frame.setBody(body);

        if (!firstFrame) {
            allFrames.push_back('\0');
        }
        firstFrame = false;
        allFrames += frame.toString();
    }
    return allFrames;
}

string StompProtocol::createDisconnectFrame(stringstream& ss) {

    std::lock_guard<std::mutex> lock(_lock);

    int rId = _receiptIdCounter++;
    _receiptToActions[rId] = "DISCONNECT";

    StompFrame frame("DISCONNECT");
    frame.addHeader("receipt", to_string(rId));
    return frame.toString();
}

void StompProtocol::parseEventFromBody(const std::string& body, std::string& reportingUser, Event& outEvent) {
    std::stringstream ss(body);
    std::string line;
    
    std::string teamA = "", teamB = "", eventName = "", description = "";
    int time = 0;
    std::map<std::string, std::string> genUpdates, teamAUpdates, teamBUpdates;
    std::string currentSection = "";
    
    while (std::getline(ss, line)) {
        if (line.empty()) continue;
        
        if (line == "general game updates:") { currentSection = "gen"; continue; }
        if (line == "team a updates:") { currentSection = "teamA"; continue; }
        if (line == "team b updates:") { currentSection = "teamB"; continue; }
        if (line == "description:") {
            std::string desc;
            while (std::getline(ss, line)) { desc += line + "\n"; }
            if (!desc.empty() && desc.back() == '\n') desc.pop_back();
            description = desc;
            break; 
        }
        
        size_t colonPos = line.find(": ");
        if (colonPos != std::string::npos) {
            std::string key = line;
            key.erase(0, key.find_first_not_of(" "));
            colonPos = key.find(": "); 
            
            std::string val = key.substr(colonPos + 2);
            key = key.substr(0, colonPos);

            if (currentSection == "") { 
                if (key == "user") reportingUser = val;
                else if (key == "team a") teamA = val;
                else if (key == "team b") teamB = val;
                else if (key == "event name") eventName = val;
                else if (key == "time") time = safeStoi(val);
            } else { 
                if (currentSection == "gen") genUpdates[key] = val;
                else if (currentSection == "teamA") teamAUpdates[key] = val;
                else if (currentSection == "teamB") teamBUpdates[key] = val;
            }
        }
    }
    outEvent = Event(teamA, teamB, eventName, time, genUpdates, teamAUpdates, teamBUpdates, description);
}

std::string StompProtocol::createSummary(std::stringstream& ss) {

    std::string game_name, user, fileName;
    if (!(ss >> game_name >> user >> fileName)) {
        std::cout << "Error: Could not parse summary arguments" << std::endl;
        return "";
    }

    std::cout << "Attempting to create summary for: " << game_name << " by " << user << std::endl;
    std::string output = "";
    {
        std::lock_guard<std::mutex> lock(_lock);

        if (_gameReports.find(game_name) == _gameReports.end() || 
            _gameReports[game_name].find(user) == _gameReports[game_name].end()) {
            std::cout << "No reports found for user " << user << " in game " << game_name << std::endl;
            return "";
        }
        std::vector<Event>& events = _gameReports[game_name][user];
        
        struct EventWithOrder {
            const Event* event;
            bool isSecondHalf;
        };
    
        std::vector<EventWithOrder> orderedEvents;
        bool foundHalfTime = false;
    
        for (const auto& e : events) {
            orderedEvents.push_back({&e, foundHalfTime});
            if (e.get_name() == "half time") foundHalfTime = true;
        }
    
        std::stable_sort(orderedEvents.begin(), orderedEvents.end(), [](const EventWithOrder& a, const EventWithOrder& b) {
            if (a.isSecondHalf != b.isSecondHalf) return !a.isSecondHalf; 
            return a.event->get_time() < b.event->get_time();
        });
    
        std::map<std::string, std::string> generalStats, teamAStats, teamBStats;
        std::string teamA = "", teamB = "";
    
        for (const auto& eo : orderedEvents) {
            const Event& e = *(eo.event);
            if (teamA.empty()) { teamA = e.get_team_a_name(); teamB = e.get_team_b_name(); }
            for (auto const& [k, v] : e.get_game_updates()) generalStats[k] = v;
            for (auto const& [k, v] : e.get_team_a_updates()) teamAStats[k] = v;
            for (auto const& [k, v] : e.get_team_b_updates()) teamBStats[k] = v;
        }
    
        output = teamA + " vs " + teamB + "\n"; 
        output += "Game stats:\nGeneral stats:\n"; 
        for (auto const& [k, v] : generalStats) output += k + ": " + v + "\n"; 
        output += teamA + " stats:\n"; 
        for (auto const& [k, v] : teamAStats) output += k + ": " + v + "\n"; 
        output += teamB + " stats:\n"; 
        for (auto const& [k, v] : teamBStats) output += k + ": " + v + "\n"; 
    
        output += "Game event reports:\n"; 
        for (const auto& eo : orderedEvents) {
            output += std::to_string(eo.event->get_time()) + " - " + eo.event->get_name() + ":\n"; 
            output += eo.event->get_discription() + "\n\n"; 
        }

    }


    if (fileName.empty()) {
        std::cerr << "Error: No file name provided!" << std::endl;
        return "";
    }

    std::ofstream outFile(fileName);
    if (outFile.is_open()) {
        outFile << output;
        outFile.close();
        std::cout << "Summary created in " << fileName << std::endl;
    } else {
        std::cerr << "Error: Could not open file for writing: " << fileName << std::endl;
    }
    return ""; 
}

bool StompProtocol::isCommandValid(const std::string& command, const std::string& message) {
    
    if (command != "login" && !_isLoggedIn) {
        std::cout << "Error: You must be logged in to perform this action." << std::endl;
        return false;
    }

    std::stringstream ss(message);
    std::string cmd;
    ss >> cmd;  
    
    std::string temp;
    if (command == "login") {
        
        std::string host, user, pass;
        if (!(ss >> host >> user >> pass)) {
            std::cout << "Usage: login {host:port} {username} {password}" << std::endl;
            return false;
        }
    } else if (command == "join" || command == "exit") {
        if (!(ss >> temp)) {
            std::cout << "Usage: " << command << " {genre/game_name}" << std::endl;
            return false;
        }
    } else if (command == "summary") {
        std::string game, user, file;
        if (!(ss >> game >> user >> file)) {
            std::cout << "Usage: summary {game_name} {user} {file_path}" << std::endl;
            return false;
        }
    }
    
    return true;
}

int StompProtocol::safeStoi(const std::string& str) {
    try {
        if (str.empty()) return 0;
        return std::stoi(str);
    } catch (const std::exception& e) {
        std::cerr << "Warning: Invalid number format received: " << str << std::endl;
        return 0;
    }
}

bool StompProtocol::isPendingDisconnect() {
    return _isPendingDisconnect;
}

std::string StompProtocol::getDisconnectFrame() {
    _isPendingDisconnect = false; 
    _shouldTerminate = true;      
    
    return "DISCONNECT\nreceipt:logout_after_error\n\n\0";
}

void StompProtocol::terminate(){
    _shouldTerminate=true;
}