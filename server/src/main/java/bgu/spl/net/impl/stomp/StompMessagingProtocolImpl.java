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
        String receiptId = "";
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
            for (String line: parts){
                if (line.startsWith("receipt:")){
                    receiptId = line.substring("receipt:".length());
                    break;
                }
            }
            sendError("Invalid frame format", receiptId, message);
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
        
        for (String header : headers) {
            if (header.startsWith("receipt:")) {
                receiptId = header.substring("receipt:".length());
                break;
            }
        }

        if (!started) {
            sendError("Protocol not initialized", receiptId, message);
            return;
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
                    sendError("Client already connected", receiptId, message);
                    return;
                }
                if (body.length() != 0) {
                    sendError("CONNECT frame should not have a body", receiptId, message);
                    return;
                }
                
                boolean[] validated = new boolean[4];
                int[] idx = new int[4];
                for (int i=0; i<headers.length; i++) {
                    switch (headers[i].split(":")[0]){
                        case "accept-version":
                            if (validated[0]){
                                sendError("Multiple accept-version headers", receiptId, message);
                                return;
                            }
                            validated[0] = headers[i].equals("accept-version:"+version);

                            if (!validated[0]){
                                sendError("Unsupported STOMP version", receiptId, message);
                                return;
                            }

                            idx[0] = i;
                            break;
                        case "host":
                            if (validated[1]){
                                sendError("Multiple host headers", receiptId, message);
                                return;
                            }
                            validated[1] = headers[i].equals("host:"+host);
                            if (!validated[1]){
                                sendError("Invalid host header", receiptId, message);
                                return;
                            }
                            idx[1] = i;
                            break;
                        case "login":
                            if (validated[2]){
                                sendError("Multiple login headers", receiptId, message);
                                return;
                            }
                            validated[2] = headers[i].startsWith("login:");
                            idx[2] = i;
                            break;
                        case "passcode":
                            if (validated[3]){
                                sendError("Multiple passcode headers", receiptId, message);
                                return;
                            }
                            validated[3] = headers[i].startsWith("passcode:");
                            idx[3] = i;
                            break;
                    }                        
                }
                if (!(validated[0] && validated[1] && validated[2] && validated[3])) {
                    sendError("Invalid CONNECT frame", receiptId, message);
                    return;
                }
                String username = headers[idx[2]].substring(6);
                String passcode = headers[idx[3]].substring(9);
                LoginStatus loginStatus = db.login(connectionId, username, passcode);
                switch (loginStatus) {
                    case CLIENT_ALREADY_CONNECTED:
                        sendError("Client already connected", receiptId, message);
                        return;
                    case WRONG_PASSWORD:
                        sendError("Wrong username or password", receiptId, message);
                        return;
                    case ALREADY_LOGGED_IN:
                        sendError("User already logged in", receiptId, message);
                        return;
                    default:
                        isConnected = true;
                        connections.send(connectionId, "CONNECTED\nversion:"+version+"\n\n\u0000");
                        if (receiptId.length() != 0) {
                            sendReceipt(receiptId);
                        }
                        return;
                }


            case "DISCONNECT":
                if (!isConnected) {
                    sendError("User not connected", receiptId, message);
                    return;
                }
                if (receiptId.length() == 0) {
                    sendError("Missing receipt header", "", message);
                    return;
                }

                if (body.length() != 0) {
                    sendError("DISCONNECT frame should not have a body", receiptId, message);
                    return;
                }

                sendReceipt(receiptId);
                connections.disconnect(connectionId);


                db.logout(connectionId);
                shouldTerminate = true;
                isConnected = false;
                break;

            case "SUBSCRIBE":
                if (!isConnected) {
                    sendError("User not connected", receiptId, message);
                    return;
                }
                if (body.length() != 0) {
                    sendError("SUBSCRIBE frame should not have a body", receiptId, message);
                    return;
                }
                boolean idFound = false;
                boolean destinationFound = false;
                String subscriptionId = "";
                String destination = "";
                for (String header : headers) {
                    if (header.startsWith("id:")) {
                        if (idFound) {
                            sendError("Multiple id headers", receiptId, message);
                            return;
                        }
                        idFound = true;
                        subscriptionId = header.split(":")[1];
                    } else if (header.startsWith("destination:")) {
                        if (destinationFound) {
                            sendError("Multiple destination headers", receiptId, message);
                            return;
                        }
                        destinationFound = true;
                        destination = header.split(":")[1];
                    }
                }
                
                if (!(idFound && destinationFound)) {
                    sendError("Missing id or destination header", receiptId, message);
                    return;
                }

                db.subscribe(connectionId, destination, subscriptionId);

                if (receiptId.length() != 0) {
                    sendReceipt(receiptId);
                }
    
                break;

            case "UNSUBSCRIBE":
                if (!isConnected) {
                    sendError("User not connected", receiptId, message);
                    return;
                }

                if (headers.length != 1) {
                    sendError("UNSUBSCRIBE frame should have exactly one header", receiptId, message);
                    return;
                }

                if (body.length() != 0) {
                    sendError("UNSUBSCRIBE frame should not have a body", receiptId, message);
                    return;
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
                    sendError("Missing id header", receiptId, message);
                    return;
                }
                db.unsubscribe(connectionId, unsubId);

                if (receiptId.length() != 0) {
                    sendReceipt(receiptId);
                }

                break;
            
            case "SEND":
                if (!isConnected) {
                    sendError("User not connected", receiptId, message);
                    return;
                }
                if (headers.length != 1) {
                    sendError("SEND frame should have exactly one header", receiptId, message);
                    return;
                }
                boolean destFound = false;
                String dest = "";
                for (String header : headers) {
                    if (header.startsWith("destination:")) {
                        destFound = true;
                        
                        if (header.split(":").length < 2) {
                            sendError("Invalid destination header", receiptId, message);
                            return;
                        }
                        dest = header.split(":")[1];
                        break;
                    }
                }
                if (!destFound) {
                    sendError("Missing destination header", receiptId, message);
                    return;
                }

                boolean alreadySubscribed = db.isSubscribed(connectionId, dest);
                if (!alreadySubscribed) {
                    sendError("Not subscribed to destination", receiptId, message);
                    return;
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

                if (receiptId.length() != 0) {
                    sendReceipt(receiptId);
                }
                break;
            default:
                sendError("Unknown command", receiptId, message);
                return;
        }
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }

    private void sendError(String msg, String receiptId, String fullFrame) {
        StringBuilder frame = new StringBuilder();

        frame.append("ERROR\n");
        frame.append("message:").append(msg).append("\n");

        if (!receiptId.isEmpty()) {
            frame.append("receipt-id:").append(receiptId).append("\n");
        }

        frame.append("\n");

        if (fullFrame != null && !fullFrame.isEmpty()) {
            frame.append("The message:\n");
            frame.append("-----\n");
            frame.append(fullFrame).append("\n");
            frame.append("-----\n");
        }

        frame.append("\u0000");
        connections.send(connectionId, frame.toString());
        Database.getInstance().logout(connectionId);
        shouldTerminate = true;
        isConnected = false;
        connections.disconnect(connectionId);
    }

    private void sendReceipt(String receiptId) {
        String frame = "RECEIPT\nreceipt-id:" + receiptId + "\n\n\u0000";
        connections.send(connectionId, frame);
    }
}
