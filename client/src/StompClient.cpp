#include <stdlib.h>
#include "../include/ConnectionHandler.h"

int main(int argc, char *argv[]) {
	if (argc < 3) {
        std::cerr << "Usage: " << argv[0] << " host port" << std::endl << std::endl;
        return -1;
    }
    std::string host = argv[1];
    short port = atoi(argv[2]);
    
    ConnectionHandler connectionHandler(host, port);

    if (!connectionHandler.connect()) {
        std::cerr << "Cannot connect to " << host << ":" << port << std::endl;
        return 1;
    }
	
	//From here we will see the rest of the ehco client implementation:
	// StompProtocol packet;
	
	
	const short bufsize = 1024;
	char buf[bufsize];
	std::string command;
	std::cin.getline(buf, bufsize);

	std::string line(buf);
	if (!connectionHandler.sendFrameAscii(line, '\0')) 
		std::cout << "Disconnected. Exiting...\n" << std::endl;


	// user input and sending thread	
	std::thread socketThred([&connectionHandler](){

		while (1) {
			
			std::string command;
			std::cin.getline(buf, bufsize);

			std::string line(buf);
			
			int len=line.length();
			if (!connectionHandler.getFrameAscii(answer, '\0')) {
				std::cout << "Disconnected. Exiting...\n" << std::endl;
				break;
			}
		}
		
    });	
		
	// receiving thread
	std::thread socketThred([&connectionHandler](){

		while (1) {
			
			std::string answer;
			
			if (!connectionHandler.getFrameAscii(answer, '\0')) {
				std::cout << "Disconnected. Exiting...\n" << std::endl;
				break;
			}
		}
		
    });

    return 0;
}