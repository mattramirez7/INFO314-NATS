// The SEC (Securities and Exchange Commission) is always on the lookout for suspicious transactions,
// and transactions that are over $5000 are always suspicious. (Not really.) Write a SEC class that is able to
// see all of the client-broker orders, and write a line out to a file called "suspicions.log" that tracks the
// timestamp of the order, the client, the broker, the order sent, and the amount. (You don't need to stop the
// order, just log it--if the offline analysis determines there was any funny activity, the dedicated agents of
// the FBI will be happy to stop by either the client or the broker and have a chat.)

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Nats;
import io.nats.client.Options;

public class SEC {

    private static final String LOG_FILE_PATH = "suspicions.log";

    public static void main(String[] args) throws IOException, InterruptedException {
        // Initialize NATS connection and subscribe to relevant messages
        // Example code for NATS subscription:
        // nats.subscribe("orders.*", SEC::processOrderMessage);
        Connection nc = Nats.connect("nats://localhost:4222");
        Dispatcher d = nc.createDispatcher((msg) -> {
            System.out.println(new String(msg.getData()));
        });

        d.subscribe("orders");

        // Keep the program running to receive messages
        // You may use a library such as NATS Java Client for NATS connectivity
        // Example code for NATS subscription loop:
        // while (true) {
        //     nats.waitMessage();
        // }

        // Simulated message processing
        simulateMessageProcessing();
    }

    private static void simulateMessageProcessing() {
        // Simulated processing of received messages
        String client = "Client John Doe";
        String broker = "Broker Mary Joe";
        String order = "Buy";
        double amount = 5000.0;

        // Log suspicious transaction
        logSuspiciousTransaction(LocalDateTime.now(), client, broker, order, amount);
    }

    private static void logSuspiciousTransaction(LocalDateTime timestamp, String client, String broker,
                                                 String order, double amount) {
        String logEntry = String.format("Timestamp: %s, Client: %s, Broker: %s, Order: %s, Amount: %.2f%n",
                timestamp, client, broker, order, amount);

        try (FileWriter writer = new FileWriter(LOG_FILE_PATH, true)) {
            writer.write(logEntry);
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Example message processing method for orders
    private static void processOrderMessage(String subject, String message) {
        // Extract relevant information from the message
        // Example:

        message = "<orderReceipt><sell symbol=\"MSFT\" amount=\"40\" /><complete amount=\"180000\" /></orderReceipt>";

        Pattern orderReceiptPattern = Pattern.compile("<orderReceipt>(.+?)</orderReceipt>");
        Matcher orderReceiptMatcher = orderReceiptPattern.matcher(message);

        if (orderReceiptMatcher.find()) {
            String orderReceiptContent = orderReceiptMatcher.group(1);

            Pattern buyPattern = Pattern.compile("<buy symbol=\"(.+?)\" amount=\"(\\d+)\" />");
            Matcher buyMatcher = buyPattern.matcher(orderReceiptContent);

            Pattern sellPattern = Pattern.compile("<sell symbol=\"(.+?)\" amount=\"(\\d+)\" />");
            Matcher sellMatcher = sellPattern.matcher(orderReceiptContent);

            if (buyMatcher.find()) {
                String symbol = buyMatcher.group(1);
                String stockAmount = buyMatcher.group(2);

                // Extract complete amount
                String completeAmount = "";
                Pattern completePattern = Pattern.compile("<complete amount=\"(\\d+)\" />");
                Matcher completeMatcher = completePattern.matcher(orderReceiptContent);

                if (completeMatcher.find()) {
                    completeAmount = completeMatcher.group(1);
                }
                

                // Print the extracted values
                System.out.println("Symbol: " + symbol);
                System.out.println("Stock Amount: " + stockAmount);
                System.out.println("Dollar Amount: " + completeAmount);
            } else if (sellMatcher.find()) {
                String symbol = sellMatcher.group(1);
                String stockAmount = sellMatcher.group(2);

                // Extract complete amount
                String completeAmount = "";
                Pattern completePattern = Pattern.compile("<complete amount=\"(\\d+)\" />");
                Matcher completeMatcher = completePattern.matcher(orderReceiptContent);

                if (completeMatcher.find()) {
                    completeAmount = completeMatcher.group(1);
                }

                // Print the extracted values
                System.out.println("Symbol: " + symbol);
                System.out.println("Stock Amount: " + stockAmount);
                System.out.println("Dollar Amount: " + completeAmount);
            }
        }


        // Check if the transaction amount is suspicious
        // if (amount > 5000.0) {
        //     logSuspiciousTransaction(LocalDateTime.now(), client, broker, order, amount);
        // }
    }
}

