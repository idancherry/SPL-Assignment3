package bgu.spl.net.impl.stomp;

import bgu.spl.net.srv.Server;
import bgu.spl.net.impl.data.Database;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.function.Supplier;
import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.MessagingProtocol;



public class StompServer {

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java StompServer <port> <tpc|reactor>");
            return;
        }
        final int port;
        final String mode = args[1];
        try{
            port = Integer.parseInt(args[0]);
        }catch (NumberFormatException e){
            System.out.println("Usage: java StompServer <port> <tpc|reactor>");
            return;
        }
        
        Supplier<MessagingProtocol<String>> protocolFactory =
        () -> new StompProtocolAdapter();

        Thread reportThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if ("report".equalsIgnoreCase(line.trim())) {
                        Database.getInstance().printReport();
                    }
                }
            } catch (Exception e) {
                System.err.println("Report thread error: " + e.getMessage());
            }
        }, "report-thread");
        reportThread.setDaemon(true);
        reportThread.start();


        Supplier<MessageEncoderDecoder<String>> encdecFactory =
                () -> new StompMessageEncoderDecoder();

        Server<String> server;
        if (mode.equals("tpc")) {
            server = Server.threadPerClient(
                    port,
                    protocolFactory,
                    encdecFactory);
            server.serve();
        } else if (mode.equals("reactor")) {
            server = Server.reactor(
                    Runtime.getRuntime().availableProcessors(),
                    port,
                    protocolFactory,
                    encdecFactory);
            server.serve();
        } else {
            System.out.println("Usage: java StompServer <port> <tpc|reactor>");
        }

    }
}
