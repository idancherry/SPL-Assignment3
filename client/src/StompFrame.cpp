#include "StompFrame.h"




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
        result += key + ":" + value + "\n";
    }
    result += "\n"; // שורה ריקה בין הדרים לבאדי
    result += _body;
    result += "\0"; // תו ה-Null המפורסם של STOMP
    return result;
}
  

static StompFrame parse(std::string rawFrame){

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




        
