#pragma once

#include "../include/ConnectionHandler.h"
#include "../include/StompFrame.h"
#include <unordered_map>

// TODO: implement the STOMP protocol
class StompProtocol{
private:
    bool _isLoggedIn;
    bool _shouldTerminate;
    int _subIdCounter;
    int _receiptIdCounter;

    std::unordered_map<int, std::string> _subscriptions;

    std::map<int, std::string> _receiptToActions;

    void handleConnected(const StompFrame& frame);
    void handleMessage(const StompFrame& frame);
    void handleReceipt(const StompFrame& frame);
    void handleError(const StompFrame& frame);

    std::string createConnectFrame(std::stringstream& ss);
    std::string createSubscribeFrame(std::stringstream& ss);
    std::string createUnsubscribeFrame(std::stringstream& ss);
    std::string createSendFrame(std::stringstream& ss);
    std::string createDisconnectFrame(std::stringstream& ss);

public:
    StompProtocol();
    std::string processKeyboardInput(std::string input);
    void processServerResponse(std::string frame);
    bool shouldTerminate();
};
