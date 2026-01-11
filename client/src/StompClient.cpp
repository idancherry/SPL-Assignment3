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

	std::cin.getline(buf, bufsize);
	
	std::string line(buf);
	int len=line.length();
	std::cout << "Sent " << len+1 << " bytes to server" << std::endl;
	if (!connectionHandler.sendFrameAscii(line, '\0')) 
		std::cout << "Disconnected. Exiting...\n" << std::endl;
		

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