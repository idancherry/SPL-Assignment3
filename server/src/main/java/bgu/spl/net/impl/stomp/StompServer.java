package bgu.spl.net.impl.stomp;

import bgu.spl.net.srv.Server;
import bgu.spl.net.impl.stomp.StompMessagingProtocolImpl; 
import bgu.spl.net.impl.stomp.StompMessageEncoderDecoder;

public class StompServer {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Please provide port and server type (tpc/reactor)");
            return;
        }

        int port = Integer.parseInt(args[0]);
        String serverType = args[1];
    
    }
}
