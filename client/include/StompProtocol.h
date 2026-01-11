#pragma once

#include "../include/ConnectionHandler.h"

// TODO: implement the STOMP protocol
class StompProtocol{
private:
    bool _isLoggedIn;
    bool _shouldTerminate;
    int _subIdCounter;
    int _receiptIdCounter;
    std::map<std::string, int> _channelToSubId;

public:
    StompProtocol();
    void processResponse(std::string message);
    std::string processKeyboardInput(std::string input);
    void processServerResponse(std::string frame);
    bool shouldTerminate();
};
