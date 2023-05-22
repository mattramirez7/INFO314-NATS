import io.nats.client.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.Duration;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicReference;

public class StockBroker {
    private String brokerName;
    private Dispatcher dispatcher;
    private Connection natsConnection;
    private String clientName;

    public StockBroker(String brokerName, Connection connection) {
        this.brokerName = brokerName;
        this.natsConnection = connection;

        //This takes the class instance of processOrder and uses that as message handler
        this.dispatcher = connection.createDispatcher(this::processOrder);
        

    }

    public void subscribe(String topic) {
        dispatcher.subscribe(topic);
    }

    public void unsubscribe(String topic) {
        dispatcher.unsubscribe(topic);
    }

    private void processOrder(Message message) {
        String order = new String(message.getData());
        System.out.println(order);

        try {
            Thread.sleep(3000); // Simulate order execution
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        double totalAmount = calculateTotalAmount(order);
        String response = constructResponse(order, totalAmount);
        publishResponse(response);
    }

    private double calculateTotalAmount(String order) {
        Pattern pattern = Pattern.compile("<(buy|sell)\\s+amount=\"(\\d+)\"\\s+symbol=\"(\\w+)\"\\s*/>");
        Matcher matcher = pattern.matcher(order);

        if (matcher.find()) {
            String type = matcher.group(1);
            double amount = Double.parseDouble(matcher.group(2));
            String symbol = matcher.group(3);
            double stockPrice = getStockPrice(symbol);
            double fee = 0.1 * (type.equals("buy") ? stockPrice * amount : stockPrice * amount);
            double totalAmount = type.equals("buy") ? stockPrice * amount + fee : stockPrice * amount - fee;

            return totalAmount / 100.f;
        }

        return 0.0;
    }

    private double getStockPrice(String symbol) {
        String xmlData = subscribeAndGetXmlData(symbol);
        return extractStockPrice(xmlData);
    }
 
    private String subscribeAndGetXmlData(String symbol) {
        try {
            String topic = "NASDAQ." + symbol;
            String xmlData = "";
            Subscription sub = natsConnection.subscribe(topic);
            while (xmlData == "") {
                Message nextMessage = sub.nextMessage(1000);
                if (nextMessage != null) {
                    xmlData = new String(nextMessage.getData());
                }
            }
            return xmlData;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }      
    }

    private double extractStockPrice(String xmlData) {
        Double data = Double.parseDouble(xmlData.substring(xmlData.indexOf("<adjustedPrice>") + "<adjustedPrice>".length(), xmlData.indexOf("</adjustedPrice>")));
        return (double) (data / 100.f);
    }

    private String constructResponse(String order, double totalAmount) {
        String orderClientName = extractClientName(order);
        
        String orderPattern = "<order>(.*?)</order>";

        // Create a Pattern object
        Pattern regex = Pattern.compile(orderPattern);

        // Create a Matcher object and apply the pattern to the input XML
        Matcher matcher = regex.matcher(order);

        String newOrderContent = "";

        // Find the first occurrence of the pattern
        if (matcher.find()) {
            // Extract the content between the <order> tags
            newOrderContent = matcher.group(1);
        
        }
        return "<orderReceipt brokerId = \"" + brokerName + "\" clientId = \"" + orderClientName + "\" >" + newOrderContent + "<complete amount=\"" + totalAmount + "\" /></orderReceipt>";
    }

    private String extractClientName(String order){
        String pattern = "brokerId=\"([^\"]+)\"\\s+clientId=\"([^\"]+)\"";
        Pattern client_broker_pattern = Pattern.compile(pattern);
        Matcher matcher = client_broker_pattern.matcher(order);
        String clientId = "";
        String brokerId = "";
        if (matcher.find()) {
            brokerId = matcher.group(1);
            clientId = matcher.group(2);
            this.clientName = clientId;
            this.brokerName = brokerId;
        } else {
            clientId = "N/A";
        }
        
        return clientId;

    }

    private void publishResponse(String response) {
      
        String responseTopic = "response." + clientName ;

        natsConnection.publish(responseTopic, response.getBytes());
        System.out.println(response);
    }

    public static void main(String... args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter the broker name: ");
        String brokerName = scanner.nextLine();

        try {
            Connection connection = Nats.connect("nats://localhost:4222");

            StockBroker stockBroker = new StockBroker(brokerName, connection);
            stockBroker.subscribe("broker."+ brokerName);

            // connection.flush(Duration.ZERO); // Flush any buffered messages
            // connection.flush(Duration.ofSeconds(100)); // Wait for 100 seconds to receive messages
            // connection.close(); // Close the NATS connection
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

