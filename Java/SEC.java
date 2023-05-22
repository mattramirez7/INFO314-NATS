// The SEC (Securities and Exchange Commission) is always on the lookout for suspicious transactions,
// and transactions that are over $5000 are always suspicious. (Not really.) Write a SEC class that is able to
// see all of the client-broker orders, and write a line out to a file called "suspicions.log" that tracks the
// timestamp of the order, the client, the broker, the order sent, and the amount. (You don't need to stop the
// order, just log it--if the offline analysis determines there was any funny activity, the dedicated agents of
// the FBI will be happy to stop by either the client or the broker and have a chat.)

import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import io.nats.client.Connection;
import io.nats.client.Message;
import io.nats.client.Nats;
import io.nats.client.Subscription;

public class SEC {

    private static final String LOG_FILE_PATH = "suspicions.log";
    private static Connection nc;

    public static void main(String[] args) throws IOException, InterruptedException {
        try {
            nc = Nats.connect("nats://localhost:4222");

            // Subscription brokerSubscription = nc.subscribe("response.*");
            Subscription clientSubscription = nc.subscribe("broker.*");
            System.out.println("connected");
            while (true) {
                // Check for messages from the broker connection
                Message brokerMessage = clientSubscription.nextMessage(Duration.ZERO);
                if (brokerMessage != null) {
                    processOrderMessage(new String(brokerMessage.getData()));
                }
    
                // Check for messages from the client connection
                // Message clientMessage = clientSubscription.nextMessage(Duration.ZERO);
                // if (clientMessage != null) {
                //     System.out.println("client data");
                //     processClientMessage(clientMessage);
                // }
                System.out.println();
                System.out.println();
                System.out.println();
                System.out.println();
    
                // Sleep for a short duration before checking for new messages
                Thread.sleep(100);
            }
        } catch(IOException | InterruptedException e) {
            System.out.println("************ EXCEPTION AT LINE 62 SEC.java ************");
        }   
    }

    private static void logSuspiciousTransaction(LocalDateTime timestamp, String client, String broker, String order, double amount) {
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
    private static void processOrderMessage(String message) {
        Pattern pattern = Pattern.compile("<order brokerId=\"(.+?)\" clientId=\"(.+?)\">\\s*<buy amount=\"(\\d+)\" symbol=\"(.+?)\"/>\\s*</order>");
        Matcher matcher = pattern.matcher(message);

        if (matcher.find()) {
            String brokerId = matcher.group(1);
            String clientId = matcher.group(2);
            int amount = Integer.parseInt(matcher.group(3));

            LocalDateTime timestamp = LocalDateTime.now();

            // Process the extracted values
            System.out.println("Timestamp: " + timestamp);
            System.out.println("Client ID: " + clientId);
            System.out.println("Broker ID: " + brokerId);
            System.out.println("Order: " + message);
            System.out.println("Amount: " + amount);

            if (amount > 5000.0) {
                System.out.println("logging suspicious amount");
                logSuspiciousTransaction(LocalDateTime.now(), clientId, brokerId, message, amount);
            }
        }
    }
}

