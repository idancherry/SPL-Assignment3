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


    @Override
    public void start(int connectionId, Connections<String> connections) {
        this.connectionId = connectionId;
        this.connections = connections;
        this.shouldTerminate = false;
        this.isConnected = false;
    }

    @Override
    public void process(String message) {
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
            connections.send(connectionId, "ERROR\nmessage:Invalid frame format\n\n\u0000");
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
                    connections.send(connectionId, "ERROR\nmessage:Client already connected\n\n\u0000");
                    break;
                }
                if (body.length() != 0) {
                    connections.send(connectionId, "ERROR\nmessage:CONNECT frame should not have a body\n\n\u0000");
                    break;
                }
                if (headers.length < 4) {
                    connections.send(connectionId, "ERROR\nmessage:Invalid CONNECT frame\n\n\u0000");
                    break;
                }
                boolean[] validated = new boolean[4];
                int[] idx = new int[4];
                for (int i=0; i<headers.length; i++) {
                    switch (headers[i].split(":")[0]){
                        case "accept-version":
                            if (validated[0]){
                                connections.send(connectionId, "ERROR\nmessage:Multiple accept-version headers\n\n\u0000");
                                return;
                            }
                            validated[0] = headers[i].equals("accept-version:"+version);
                            idx[0] = i;
                            break;
                        case "host":
                            if (validated[1]){
                                connections.send(connectionId, "ERROR\nmessage:Multiple host headers\n\n\u0000");
                                return;
                            }
                            validated[1] = headers[i].equals("host:"+host);
                            idx[1] = i;
                            break;
                        case "login":
                            if (validated[2]){
                                connections.send(connectionId, "ERROR\nmessage:Multiple login headers\n\n\u0000");
                                return;
                            }
                            validated[2] = headers[i].startsWith("login:");
                            idx[2] = i;
                            break;
                        case "passcode":
                            if (validated[3]){
                                connections.send(connectionId, "ERROR\nmessage:Multiple passcode headers\n\n\u0000");
                                return;
                            }
                            validated[3] = headers[i].startsWith("passcode:");
                            idx[3] = i;
                            break;
                    }                        
                }
                if (!(validated[0] && validated[1] && validated[2] && validated[3])) {
                    connections.send(connectionId, "ERROR\nmessage:Invalid CONNECT frame\n\n\u0000");
                    break;
                }
                String username = headers[idx[2]].split(":")[1];
                String passcode = headers[idx[3]].split(":")[1];
                LoginStatus loginStatus = db.login(connectionId, username, passcode);
                switch (loginStatus) {
                    case CLIENT_ALREADY_CONNECTED:
                        connections.send(connectionId, "ERROR\nmessage:Client already connected\n\n\u0000");
                        break;
                    case WRONG_PASSWORD:
                        connections.send(connectionId, "ERROR\nmessage:Wrong password or username\n\n\u0000");
                        break;
                    case ALREADY_LOGGED_IN:
                        connections.send(connectionId, "ERROR\nmessage:User already logged in\n\n\u0000");
                        break;
                    default:
                        isConnected = true;
                        connections.send(connectionId, "CONNECTED\nversion:"+version+"\n\n\u0000");
                        break;
                }
                break;


            case "DISCONNECT":
                if (!isConnected) {
                    connections.send(connectionId, "ERROR\nmessage:User not connected\n\n\u0000");
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
                    connections.send(connectionId, "ERROR\nmessage:Missing receipt header\n\n\u0000");
                    break;
                }
                db.logout(connectionId);
                shouldTerminate = true;
                isConnected = false;
                break;

            case "SUBSCRIBE":
                if (!isConnected) {
                    connections.send(connectionId, "ERROR\nmessage:User not connected\n\n\u0000");
                    break;
                }
                boolean idFound = false;
                boolean destinationFound = false;
                String subscriptionId = "";
                String destination = "";
                for (String header : headers) {
                    if (header.startsWith("id:")) {
                        if (idFound) {
                            connections.send(connectionId, "ERROR\nmessage:Multiple id headers\n\n\u0000");
                            return;
                        }
                        idFound = true;
                        subscriptionId = header.split(":")[1];
                    } else if (header.startsWith("destination:")) {
                        if (destinationFound) {
                            connections.send(connectionId, "ERROR\nmessage:Multiple destination headers\n\n\u0000");
                            return;
                        }
                        destinationFound = true;
                        destination = header.split(":")[1];
                    }
                }
                if (!(idFound && destinationFound)) {
                    connections.send(connectionId, "ERROR\nmessage:Missing id or destination header\n\n\u0000");
                    break;
                }

                db.subscribe(connectionId, destination, subscriptionId);
                connections.send(connectionId, "RECEIPT\nreceipt-id:sub-"+subscriptionId+"\ndestination:"+destination+"\n\n\u0000");
                break;

            case "UNSUBSCRIBE":
                if (!isConnected) {
                    connections.send(connectionId, "ERROR\nmessage:User not connected\n\n\u0000");
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
                    connections.send(connectionId, "ERROR\nmessage:Missing id header\n\n\u0000");
                    break;
                }
                db.unsubscribe(connectionId, unsubId);
                connections.send(connectionId, "RECEIPT\nreceipt-id:unsub-"+unsubId+"\n\n\u0000");
                break;
            
            case "SEND":
                if (!isConnected) {
                    connections.send(connectionId, "ERROR\nmessage:User not connected\n\n\u0000");
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
                    connections.send(connectionId, "ERROR\nmessage:Missing destination header\n\n\u0000");
                    break;
                }
                //might need to add db send message to subscribers
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
                connections.send(connectionId, "ERROR\nmessage:Unknown command\n\n\u0000");
                break;
        }
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }
}