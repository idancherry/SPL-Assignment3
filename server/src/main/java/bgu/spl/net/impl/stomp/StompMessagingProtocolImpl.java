package bgu.spl.net.impl.stomp;
import java.util.Set;

import bgu.spl.net.api.StompMessagingProtocol;
import bgu.spl.net.impl.data.Database;
import bgu.spl.net.impl.data.LoginStatus;
import bgu.spl.net.srv.Connections;

public class StompMessagingProtocolImpl implements StompMessagingProtocol<String> {
    private int connectionId;
    private Connections<String> connections;
    private boolean shouldTerminate;
    final private String version= "1.2";
    final private String host = "stomp.cs.bgu.ac.il";
    private boolean isConnected;
    private boolean started = false;



    @Override
    public void start(int connectionId, Connections<String> connections) {
        this.connectionId = connectionId;
        this.connections = connections;
        this.shouldTerminate = false;
        this.isConnected = false;
        this.started = true;
    }

    @Override
    public void process(String message) {
        if (!started) {
            sendError("Protocol not initialized");
            connections.disconnect(connectionId);
            shouldTerminate = true;
            return;
        }

        String[] parts = message.split("\n");
        String command = parts[0];
        int headerEndIndex = 0;
        boolean headerEndFound = false;
        for (int i=1; i<parts.length; i++){
            if (parts[i].isEmpty()){
                headerEndIndex = i;
                headerEndFound = true;
                break;
            }
        }
        if (!headerEndFound){
            sendError("Invalid frame format");
            return;
        }
        String[] headers;
        if (headerEndIndex>0){
            headers = new String[headerEndIndex -1];
            System.arraycopy(parts, 1, headers, 0, headerEndIndex -1);
        }
        else{
            headers = new String[0];
        }
        
        
        String body="";
        if (headerEndIndex +1 < parts.length){
            StringBuilder bodyBuilder = new StringBuilder();
            for (int i=headerEndIndex +1; i<parts.length; i++){
                bodyBuilder.append(parts[i]);
                if (i != parts.length -1){
                    bodyBuilder.append("\n");
                }
            }
            body = bodyBuilder.toString();
        }

        Database db =Database.getInstance();
        switch (command) {
            case "CONNECT":
                if (isConnected) {
                    sendError("Client already connected");
                    break;
                }
                if (body.length() != 0) {
                    sendError("CONNECT frame should not have a body");
                    break;
                }
                if (headers.length < 4) {
                    sendError("Invalid CONNECT frame");
                    break;
                }
                boolean[] validated = new boolean[4];
                int[] idx = new int[4];
                for (int i=0; i<headers.length; i++) {
                    switch (headers[i].split(":")[0]){
                        case "accept-version":
                            if (validated[0]){
                                sendError("Multiple accept-version headers");
                                return;
                            }
                            validated[0] = headers[i].equals("accept-version:"+version);
                            idx[0] = i;
                            break;
                        case "host":
                            if (validated[1]){
                                sendError("Multiple host headers");
                                return;
                            }
                            validated[1] = headers[i].equals("host:"+host);
                            idx[1] = i;
                            break;
                        case "login":
                            if (validated[2]){
                                sendError("Multiple login headers");
                                return;
                            }
                            validated[2] = headers[i].startsWith("login:");
                            idx[2] = i;
                            break;
                        case "passcode":
                            if (validated[3]){
                                sendError("Multiple passcode headers");
                                return;
                            }
                            validated[3] = headers[i].startsWith("passcode:");
                            idx[3] = i;
                            break;
                    }                        
                }
                if (!(validated[0] && validated[1] && validated[2] && validated[3])) {
                    sendError("Invalid CONNECT frame");
                    break;
                }
                String username = headers[idx[2]].split(":")[1];
                String passcode = headers[idx[3]].split(":")[1];
                LoginStatus loginStatus = db.login(connectionId, username, passcode);
                switch (loginStatus) {
                    case CLIENT_ALREADY_CONNECTED:
                        sendError("Client already connected");
                        break;
                    case WRONG_PASSWORD:
                        sendError("Wrong username or password");
                        break;
                    case ALREADY_LOGGED_IN:
                        sendError("User already logged in");
                        break;
                    default:
                        isConnected = true;
                        connections.send(connectionId, "CONNECTED\nversion:"+version+"\n\n\u0000");
                        break;
                }
                break;


            case "DISCONNECT":
                if (!isConnected) {
                    sendError("User not connected");
                    break;
                }
                boolean receiptFound = false;
                for (String header : headers) {
                    if (header.startsWith("receipt:")) {
                        receiptFound = true;
                        String receiptId = header.split(":")[1];
                        connections.send(connectionId, "RECEIPT\nreceipt-id:"+receiptId+"\n\n\u0000");
                        break;
                    }
                }
                if (!receiptFound) {
                    sendError("Missing receipt header");
                    break;
                }
                db.logout(connectionId);
                shouldTerminate = true;
                isConnected = false;
                break;

            case "SUBSCRIBE":
                if (!isConnected) {
                    sendError("User not connected");
                    break;
                }
                boolean idFound = false;
                boolean destinationFound = false;
                String subscriptionId = "";
                String destination = "";
                for (String header : headers) {
                    if (header.startsWith("id:")) {
                        if (idFound) {
                            sendError("Multiple id headers");
                            return;
                        }
                        idFound = true;
                        subscriptionId = header.split(":")[1];
                    } else if (header.startsWith("destination:")) {
                        if (destinationFound) {
                            sendError("Multiple destination headers");
                            return;
                        }
                        destinationFound = true;
                        destination = header.split(":")[1];
                    }
                }
                if (!(idFound && destinationFound)) {
                    sendError("Missing id or destination header");
                    break;
                }

                db.subscribe(connectionId, destination, subscriptionId);
                connections.send(connectionId, "RECEIPT\nreceipt-id:sub-"+subscriptionId+"\ndestination:"+destination+"\n\n\u0000");
                break;

            case "UNSUBSCRIBE":
                if (!isConnected) {
                    sendError("User not connected");
                    break;
                }
                boolean unsubFound = false;
                String unsubId = "";
                for (String header : headers) {
                    if (header.startsWith("id:")) {
                        unsubFound = true;
                        unsubId = header.split(":")[1];
                        break;
                    }
                }
                if (!unsubFound) {
                    sendError("Missing id header");
                    break;
                }
                db.unsubscribe(connectionId, unsubId);
                connections.send(connectionId, "RECEIPT\nreceipt-id:unsub-"+unsubId+"\n\n\u0000");
                break;
            
            case "SEND":
                if (!isConnected) {
                    sendError("User not connected");
                    break;
                }
                boolean destFound = false;
                String dest = "";
                for (String header : headers) {
                    if (header.startsWith("destination:")) {
                        destFound = true;
                        dest = header.split(":")[1];
                        break;
                    }
                }
                if (!destFound) {
                    sendError("Missing destination header");
                    break;
                }
                Set<Integer> subscribers = db.getChannelSubscribers(dest);
                for (int subConnectionId : subscribers) {
                    String subId = db.getSubscription(subConnectionId, dest);
                    if (subId == null) {
                        continue;
                    }
                    String msgToSend = "MESSAGE\nsubscription:"+subId+"\nmessage-id:"+db.getNextMessageId()+
                                      "\ndestination:"+dest+"\n\n"+body+"\u0000";
                    connections.send(subConnectionId, msgToSend);
                }
                break;
            default:
                sendError("Unknown command");
                break;
        }
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }

    private void sendError(String msg) {
    String frame = "ERROR\nmessage:" + msg + "\n\n\u0000";
    connections.send(connectionId, frame);
    }
}
