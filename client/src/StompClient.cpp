#include <stdlib.h>
#include "../include/ConnectionHandler.h"
#include "StompProtocol.h"
#include <sstream>
#include <iostream>
using namespace std;


int main(int argc, char *argv[]) {
    if (argc < 3) {
        std::cerr << "Usage: " << argv[0] << " host port" << std::endl;
        return -1;
    }
    std::string host = argv[1];
    short port = atoi(argv[2]);

    StompProtocol protocol;
    ConnectionHandler connectionHandler(host, port);

    if (!connectionHandler.connect()) {
        std::cerr << "Cannot connect to " << host << ":" << port << std::endl;
        return 1;
    }
    
    std::thread inputThread([&connectionHandler, &protocol](){
        while (!protocol.shouldTerminate()) {
            std::string line;
            if (!std::getline(cin, line)){
                break;
            } 
            if (protocol.shouldTerminate()) {
                break;
            }

            std::string frameToSend = protocol.processKeyboardInput(line);
            if (!frameToSend.empty()) {
                if (!connectionHandler.sendBytes(frameToSend.c_str(), frameToSend.length() + 1)) {
                    protocol.terminate();
                    connectionHandler.close();
                    std::cout << "Disconnected from server." << std::endl;
                    break;
                }
            }
        }
    }); 
        
    std::thread receiveThread([&connectionHandler, &protocol](){
    while (!protocol.shouldTerminate()) {
        std::string answer;

        if (!connectionHandler.getFrameAscii(answer, '\0')) {
            if (!protocol.shouldTerminate()) {
                protocol.terminate();
                std::cout << "Server disconnected unexpectedly." << std::endl;
            }
            connectionHandler.close();
            break;
        }

        protocol.processServerResponse(answer);
        if (protocol.shouldTerminate()) {
            connectionHandler.close();
            break;
        }
    }
});

    inputThread.join();
    receiveThread.join();

    return 0;
}
