#include "../include/StompProtocol.h"
#include <sstream>
#include <iostream>

using namespace std;

StompProtocol::StompProtocol() : 
    _isLoggedIn(false),           
    _shouldTerminate(false),      
    _subIdCounter(0),             
    _receiptIdCounter(0) {}

bool StompProtocol::shouldTerminate(){
    return _shouldTerminate;
}


// --- Keyboard Input ---
string StompProtocol::processKeyboardInput(string message) {
    stringstream ss(message);
    string command;
    ss >> command;

    if (command == "login")  return createConnectFrame(ss);
    if (command == "join")   return createSubscribeFrame(ss);
    if (command == "exit")   return createUnsubscribeFrame(ss);
    if (command == "report") return createSendFrame(ss);
    if (command == "logout") return createDisconnectFrame(ss);

    return ""; 
}

// --- Server Response ---
void StompProtocol::processServerResponse(string message) {
    StompFrame frame = StompFrame::parse(message);
    string command = frame.getCommand();

    if (command == "CONNECTED") handleConnected(frame);
    else if (command == "MESSAGE")   handleMessage(frame);
    else if (command == "RECEIPT")   handleReceipt(frame);
    else if (command == "ERROR")     handleError(frame);
}

// --- Handlers ---

void StompProtocol::handleConnected(const StompFrame& frame) {
    _isLoggedIn = true;
    cout << "Login successful" << endl;
}

void StompProtocol::handleMessage(const StompFrame& frame) {
    int subId = stoi(frame.getHeader("subscription"));
    string genre = _subscriptions[subId];
    cout << "[" << genre << "]: " << frame.getBody() << endl;
}

void StompProtocol::handleReceipt(const StompFrame& frame) {
    int rId = stoi(frame.getHeader("receipt-id"));

    if (_receiptToActions.count(rId)) {
        string action = _receiptToActions[rId];
        
        
        if (action == "DISCONNECT") {
            _shouldTerminate = true;
            cout << "Disconnected successfully." << endl;
        } 
        else {
            cout << action << endl; 
        }
        
        _receiptToActions.erase(rId); 
    }
}

void StompProtocol::handleError(const StompFrame& frame) {
    cout << "Error from server: " << frame.getHeader("message") << endl;
    _shouldTerminate = true;
}

// --- Frame Creators ---

string StompProtocol::createConnectFrame(stringstream& ss) {
    if (_isLoggedIn) {
        cout << "The client is already logged in, log out before trying again" << endl;
        return "";
    }
    string hostPort, username, password;
    ss >> hostPort >> username >> password;

    StompFrame frame("CONNECT");
    frame.addHeader("accept-version", "1.2");
    frame.addHeader("host", "stomp.cs.bgu.ac.il"); 
    frame.addHeader("login", username);
    frame.addHeader("passcode", password);

    return frame.toString();
}

string StompProtocol::createSubscribeFrame(stringstream& ss) {
    string genre;
    ss >> genre;

    int subId = _subIdCounter++;
    _subscriptions[subId] = genre; 

    int rId = _receiptIdCounter++;
    _receiptToActions[rId] = "Joined genre " + genre;

    StompFrame frame("SUBSCRIBE");
    frame.addHeader("destination", genre);
    frame.addHeader("id", to_string(subId));
    frame.addHeader("receipt", to_string(rId));

    return frame.toString();
}

string StompProtocol::createUnsubscribeFrame(stringstream& ss) {
    string genre;
    ss >> genre;
    int subId = -1;

    for (auto const& [id, g] : _subscriptions) {
        if (g == genre) {
            subId = id;
            break;
        }
    }

    if (subId == -1) {
        cout << "Error: Not subscribed to " << genre << endl;
        return "";
    }

    int rId = _receiptIdCounter++;
    _receiptToActions[rId] = "Exited genre " + genre;

    StompFrame frame("UNSUBSCRIBE");
    frame.addHeader("id", to_string(subId));
    frame.addHeader("receipt", to_string(rId));

    _subscriptions.erase(subId); 
    return frame.toString();
}

string StompProtocol::createSendFrame(stringstream& ss) {
    string genre;
    ss >> genre;
    string body;
    getline(ss, body);
    if (!body.empty() && body[0] == ' ') body.erase(0, 1);

    StompFrame frame("SEND");
    frame.addHeader("destination", genre);
    frame.setBody(body);
    return frame.toString();
}

string StompProtocol::createDisconnectFrame(stringstream& ss) {
    int rId = _receiptIdCounter++;
    _receiptToActions[rId] = "DISCONNECT";

    StompFrame frame("DISCONNECT");
    frame.addHeader("receipt", to_string(rId));
    return frame.toString();
}