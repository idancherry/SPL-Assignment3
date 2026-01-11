package bgu.spl.net.impl.stomp;
import bgu.spl.net.impl.data.Database;
import bgu.spl.net.impl.data.LoginStatus;
import bgu.spl.net.api.StompMessagingProtocol;
import bgu.spl.net.srv.Connections;

public class StompMessagingProtocolImpl implements StompMessagingProtocol<String> {
    private int connectionId;
    private Connections<String> connections;
    private boolean shouldTerminate;
    final private String version= "1.2";
    final private String host = "stomp.cs.bgu.ac.il";


    @Override
    public void start(int connectionId, Connections<String> connections) {
        this.connectionId = connectionId;
        this.connections = connections;
        this.shouldTerminate = false;
    }

    @Override
    public void process(String message) {
        String[] parts = message.split("\n\n");
        if (parts.length < 2 ){
            connections.send(connectionId, "ERROR\nmessage:Invalid message\n\n\u0000");
            return;
        }
        String[] command_headers = parts[0].split("\n");
        String command = command_headers[0];
        String[] headers = new String[command_headers.length - 1];
        System.arraycopy(command_headers, 1, headers, 0, headers.length);
        String[] bodyAndNull = parts[1].split("\n");
        String body; //should probably use the body variable later
        boolean nullFound = false;

        for (int i=0; i<bodyAndNull.length; i++) {
            if (bodyAndNull[i].equals("\u0000")) {
                nullFound = true;
                String[] bodyArray = new String[i];
                System.arraycopy(bodyAndNull, 0, bodyArray, 0, i);
                body = String.join("\n", bodyArray);
                break;
            }
        }

        if (!nullFound) {
            connections.send(connectionId, "ERROR\nmessage:Missing null terminator\n\n\u0000");
            return;
        }

        switch (command) {
            case "CONNECT":
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
                Database db = Database.getInstance();
                LoginStatus loginStatus = db.login(connectionId, username, passcode);
                if (loginStatus != LoginStatus.WRONG_PASSWORD) {
                    connections.send(connectionId, "CONNECTED\nversion:"+version+"\n\n\u0000");
                    break;
                }
                connections.send(connectionId, "ERROR\nmessage:Invalid login or passcode\n\n\u0000");
                break;
            case "DISCONNECT":
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
                Database.getInstance().logout(connectionId);
                shouldTerminate = true;
                break;

            case "SUBSCRIBE":
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

                //might need to add db subscription
                connections.send(connectionId, "RECEIPT\nreceipt-id:sub-"+subscriptionId+"\ndestination:"+destination+"\n\n\u0000");
                break;

            case "UNSUBSCRIBE":
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
                //might need to add db unsubscription
                connections.send(connectionId, "RECEIPT\nreceipt-id:unsub-"+unsubId+"\n\n\u0000");
                break;
            
            case "SEND":
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
                connections.send(connectionId, "RECEIPT\nreceipt-id:send-"+dest+"\n\n\u0000");
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