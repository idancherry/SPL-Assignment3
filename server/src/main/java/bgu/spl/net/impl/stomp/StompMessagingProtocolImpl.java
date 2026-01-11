package bgu.spl.net.impl.stomp;
import bgu.spl.net.api.StompMessagingProtocol;
import bgu.spl.net.srv.Connections;

public class StompMessagingProtocolImpl implements StompMessagingProtocol<String> {
    

    @Override
    public void start(int connectionId, Connections<String> connections) {
        // Implementation goes here
    }

    @Override
    public void process(String message) {
        // Implementation goes here
    }

    @Override
    public boolean shouldTerminate() {
        // Implementation goes here
        
        return false; //placeholder
    }
}