#ifndef FRAME_H
#define FRAME_H

#include <string>
#include <map>
#include <vector>

class StompFrame {
private:
    std::string _command;
    std::map<std::string, std::string> _headers;
    std::string _body;

public:
    StompFrame(std::string command);
    void addHeader(std::string key, std::string value);
    void setBody(std::string body);
    
    std::string toString();

    static StompFrame parse(std::string rawFrame);
    
    // Getters
    std::string getCommand() const;
    std::string getHeader(std::string key) const;
    std::string getBody() const;
};

#endif