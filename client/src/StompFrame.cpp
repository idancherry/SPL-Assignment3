#include "StompFrame.h"
#include <string>
#include <sstream>


StompFrame::StompFrame(std::string command) :
    _command(command),
    _headers(),
    _body("") {}

  
void StompFrame::addHeader(std::string key, std::string value) {
    _headers[key] = value;
}

void StompFrame::setBody(std::string body) {
    _body = body;
}
    
std::string StompFrame::toString() {
    std::string result = _command + "\n";
    
    for (auto const& [key, value] : _headers) {
        if (key.empty()) continue; 
        
        result += key + ":" + value + "\n";
    }
    
    result += "\n"; 
    result += _body;
    
    return result;
}
  

StompFrame StompFrame::parse(std::string rawFrame) {
    std::stringstream ss(rawFrame);
    std::string line;

    if (!std::getline(ss, line, '\n')) {
        return StompFrame("ERROR"); 
    }
    
    //in windows it add /r at the end
        if (!line.empty() && line.back() == '\r') 
        line.pop_back();
    
    StompFrame frame(line); //line=command

    //getting the headers
    while (std::getline(ss, line, '\n') && !line.empty() && line != "\r") {

        if (line.back() == '\r')
            line.pop_back();

        //getting the headeres
        size_t colonPos = line.find(':');
        if (colonPos != std::string::npos) {        //if there is :
            std::string key = line.substr(0, colonPos);
            std::string value = line.substr(colonPos + 1);
            frame.addHeader(key, value);
        }else 
          break;
    }
    // getting the body
    std::string body;
    
    if (std::getline(ss, body, '\0')) {
        frame._body = body;
    }

    return frame;
}

// Getters

std::string StompFrame::getCommand() const{
    return _command;
}

std::string StompFrame::getHeader(std::string key) const{
    auto it = _headers.find(key);
    if (it != _headers.end()) {    
        return it->second;        
    }
    return ""; 
}

std::string StompFrame::getBody() const{
    return _body;
}




        
