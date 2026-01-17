#pragma once

#include "../include/ConnectionHandler.h"
#include "../include/StompFrame.h"
#include "../include/event.h"
#include <map>
#include <string>
#include <unordered_map>
#include <vector>
#include <mutex>

// TODO: implement the STOMP protocol
class StompProtocol{
private:
    bool _isLoggedIn;
    bool _shouldTerminate;
    int _subIdCounter;
    int _receiptIdCounter;
    std::unordered_map<int, std::string> _subscriptions;
    std::map<int, std::string> _receiptToActions;
    std::map<std::string, std::map<std::string, std::vector<Event>>> _gameReports;
    std::string _currentUserName;
    int _disconnectReceiptId;   

    std::mutex _lock;

    void handleConnected(const StompFrame& frame);
    void handleMessage(const StompFrame& frame);
    void handleReceipt(const StompFrame& frame);
    void handleError(const StompFrame& frame);

    std::string createConnectFrame(std::stringstream& ss);
    std::string createSubscribeFrame(std::stringstream& ss);
    std::string createUnsubscribeFrame(std::stringstream& ss);
    std::string createSendFrame(std::stringstream& ss);
    std::string createDisconnectFrame(std::stringstream& ss);
    std::string createSummary(std::stringstream& ss);

    void parseEventFromBody(const std::string &body, std::string &reportingUser, Event& outEvent);
    bool isCommandValid(const std::string& command, std::stringstream& ss);
    int safeStoi(const std::string& str);

public:
    StompProtocol();
    std::string processKeyboardInput(std::string input);
    void processServerResponse(std::string frame);
    bool shouldTerminate();
};
