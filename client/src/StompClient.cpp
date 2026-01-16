#include <stdlib.h>
#include "../include/ConnectionHandler.h"
#include "StompProtocol.h"
#include <sstream>
#include <iostream>
using namespace std;


int main(int argc, char *argv[]) {
	if (argc < 3) {
        std::cerr << "Usage: " << argv[0] << " host port" << std::endl << std::endl;
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
	
	
	// user input and sending thread	
	std::thread inputThread([&connectionHandler, &protocol](){
		
		while (!protocol.shouldTerminate()) {
			
		
			std::string line;
			std::getline(cin, line);

			std::string frameToSend = protocol.processKeyboardInput(line);
			if(!frameToSend.empty()){
				if (!connectionHandler.sendFrameAscii(frameToSend, '\0')) {
					std::cout << "Disconnected. Exiting...\n" << std::endl;
					break;
				}
			}
		}
		
	});	
		
	// receiving thread
	std::thread receiveThread([&connectionHandler, &protocol](){

		while (!protocol.shouldTerminate()) {
			
			std::string answer;

			if (!connectionHandler.getFrameAscii(answer, '\0')) {
				std::cout << "Disconnected. Exiting...\n" << std::endl;
				break;
			}
			protocol.processServerResponse(answer);
		}
	});

	inputThread.join();
	receiveThread.join();

	return 0;
}