package bgu.spl.net.srv;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import bgu.spl.net.impl.data.Database;

public class ConnectionsImpl<T> implements Connections<T> {
    private final ConcurrentHashMap<Integer, ConnectionHandler<T>> connections;

    public ConnectionsImpl() {
        connections = new ConcurrentHashMap<>();
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
        Database db = Database.getInstance();
        Set<Integer> subscribers = db.getChannelSubscribers(channel);
        for (int connectionId : subscribers) {
            send(connectionId, msg);
        }        
    }

    @Override
    public void disconnect(int connectionId) {
        Database db = Database.getInstance();
        db.disconnect(connectionId);
        ConnectionHandler<T> handler = connections.remove(connectionId);
        if (handler != null) {
            try{
                handler.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    void addConnection(int connectionId, ConnectionHandler<T> handler) {
        if (handler == null)
            throw new IllegalArgumentException("ConnectionHandler cannot be null");
        if (connections.containsKey(connectionId))
            throw new IllegalArgumentException("Connection ID already exists");
        connections.put(connectionId, handler);
    }
    
}
