package bgu.spl.net.srv;

public class ConnectionsImpl<T> implements Connections<T> {
    private final java.util.concurrent.ConcurrentHashMap<Integer, ConnectionHandler<T>> connections;

    public ConnectionsImpl() {
        connections = new java.util.concurrent.ConcurrentHashMap<>();
    }

    @Override
    public boolean send(int connectionId, T msg) {
        ConnectionHandler<T> handler = connections.get(connectionId);
        if (handler != null) {
            handler.send(msg);
            return true;
        }
        return false;
    }

    @Override
    public void send(String channel, T msg) {
        // Implementation goes here
        
    }

    @Override
    public void disconnect(int connectionId) {
        connections.remove(connectionId);
    }
    
}
