import io.nats.client.*;
import java.io.*;
import java.time.Duration;
import java.util.concurrent.Future;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import javax.xml.parsers.*;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.*;
import org.w3c.dom.*;

public class StockBrokerClient{
    private String clientName;
    private String brokerName;
    private Connection connection;
    private Subscription sub;
    private String clientPortfolio;
    private String clientStrategy;

    public StockBrokerClient(String clientName, String brokerName, Connection connection, Subscription subscription, String portfolio, String strategy) {
        this.clientName = clientName;
        this.brokerName = brokerName;
        this.connection = connection;
        this.sub=subscription;
        this.clientPortfolio=portfolio;
        this.clientStrategy=strategy;
    }

    public static void main(String...args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter client name: ");
        String clientName = scanner.nextLine();

        System.out.print("Enter the broker name: ");
        String brokerName = scanner.nextLine();

        System.out.print("Enter strategy doc: ");
        String strategyDoc = scanner.nextLine();
        String clientStrategy="../Clients/"+strategyDoc+".xml";

        System.out.print("Enter portfolio doc: ");
        String portfolioDoc = scanner.nextLine();
        String clientPortfolio="../Clients/"+portfolioDoc+".xml";

        // String clientPortfolio="../Clients/portfolio-2.xml";
        // String clientStrategy="../Clients/strategy-2.xml";
        String brokerResponse="response." + clientName;

        try {
            Connection connection = Nats.connect("nats://localhost:4222");
            Subscription sub = connection.subscribe(brokerResponse);
            StockBrokerClient stockClient = new StockBrokerClient(clientName, brokerName, connection, sub, clientPortfolio, clientStrategy);
            stockClient.subscribe("NASDAQ.*"); 

            // connection.flush(Duration.ZERO); // Flush any buffered messages
            // connection.flush(Duration.ofSeconds(100)); // Wait for 100 seconds to receive messages
            // connection.close(); // Close the NATS connection
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void subscribe(String market) {
        try {
            Dispatcher dispatcher = connection.createDispatcher((msg) -> {
                String message = new String(msg.getData());
                Object[] output = checkingStrategy(message);

            if (output != null) {
                String action = (String) output[0];
                String stockName = (String) output[1];
                int numberOfShares = (int) output[2];
                String xmlRequest = xmlRequestBuilder(action, stockName, numberOfShares);
                System.out.println(xmlRequest);

                // This sends buy/sell request to broker 
                String brokerString="broker." + brokerName;
                // String brokerResponse="response." + brokerName;
                try{
                    connection.publish(brokerString, xmlRequest.getBytes());

                    // Subscription sub = connection.subscribe(brokerResponse);
                    Message responseMessage = sub.nextMessage(Duration.ofMillis(5000));
                    if (responseMessage == null) {
                        System.out.println("No response recieved");
                        System.out.println();
                    } else {
                        String brokerResponse = new String(responseMessage.getData(), StandardCharsets.UTF_8);
                        System.out.println(brokerResponse);
                        updatePortfolio(stockName, numberOfShares, action);
                    }

                    // Future<Message> incoming = connection.request(brokerString, xmlRequest.getBytes());
                    // Message response = incoming.get(500, TimeUnit.MILLISECONDS);
                    // String brokerResponse = new String(response.getData(), StandardCharsets.UTF_8);
                    
                } catch (Exception e){
                    e.printStackTrace();
                }
            }
            });

            dispatcher.subscribe(market);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Takes String message from publisher, checks strategy, returns Object[] with the action, stock name, amount or null
    public Object[] checkingStrategy(String message){
        Object[] params = new Object[3];
        try{
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(new ByteArrayInputStream(message.getBytes()));
            
            doc.getDocumentElement().normalize();
            
            // Extract the value of the name and adjustment element
            Element nameElement = (Element) doc.getElementsByTagName("name").item(0);
            String name = nameElement.getTextContent();

            Element adjustedPriceElement = (Element) doc.getElementsByTagName("adjustedPrice").item(0);
            int adjustment = Integer.parseInt(adjustedPriceElement.getTextContent());

            // Load the strategy XML file
            File strategyXmlFile = new File(clientStrategy);
            DocumentBuilderFactory dbFactory2 = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder2 = dbFactory2.newDocumentBuilder();
            Document doc2 = dBuilder2.parse(strategyXmlFile);

            doc2.getDocumentElement().normalize();
        
            // when elements in the strategy XML
            NodeList whenList = doc2.getElementsByTagName("when");
            for (int i = 0; i < whenList.getLength(); i++) {
                Node node = whenList.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element symbolElement = (Element) node;
                    String stockSymbol = symbolElement.getElementsByTagName("stock").item(0).getTextContent();

                    // Check if the symbol matches
                    if (name.equals(stockSymbol)) {
                        boolean hasBelow = symbolElement.getElementsByTagName("below").getLength() > 0;
                        boolean hasAbove = symbolElement.getElementsByTagName("above").getLength() > 0;

                        Node belowNode = symbolElement.getElementsByTagName("below").item(0);
                        Node aboveNode = symbolElement.getElementsByTagName("above").item(0);

                        if (hasBelow && belowNode != null) {
                            int belowThreshold = Integer.parseInt(belowNode.getTextContent());
                            if (adjustment < belowThreshold) {
                                int buyAmount = Integer.parseInt(symbolElement.getElementsByTagName("buy").item(0).getTextContent());
                                params[0] = "buy";
                                params[1] = stockSymbol;
                                params[2] = buyAmount;
                                return params;
                            }
                        }

                        if (hasAbove && aboveNode != null) {
                            int aboveThreshold = Integer.parseInt(aboveNode.getTextContent());
                            if (adjustment > aboveThreshold) {
                                int sellValue=checkPortfolio(name);
                                if(sellValue==0){
                                    return null;
                                }
                                params[0] = "sell";
                                params[1] = stockSymbol;
                                params[2] = sellValue;
                                return params;
                            }
                        }   
                    }
                }
            }
        }catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // creates xml request to stock broker
    public String xmlRequestBuilder(String action, String stockName, int numberOfShares){
        try{
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

        Document doc = docBuilder.newDocument();
        Element rootElement = doc.createElement("order");
        rootElement.setAttribute("clientId", clientName);
        rootElement.setAttribute("brokerId", brokerName);
        doc.appendChild(rootElement);

        Element buyElement = doc.createElement(action);
        buyElement.setAttribute("symbol", stockName);
        buyElement.setAttribute("amount", String.valueOf(numberOfShares));
        rootElement.appendChild(buyElement);

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.getBuffer().toString();
        } catch (ParserConfigurationException | TransformerException e) {
            e.printStackTrace();
        }
        return null;
    }

    // updates portfolio returns nothing
    public void updatePortfolio(String symbol, int amount, String action){
        try{
            File xmlFile = new File(clientPortfolio);
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(xmlFile);
        
            // Normalize the XML structure
            doc.getDocumentElement().normalize();

            // Find the stock element with the specified symbol
            Element stockElement = null;
            NodeList stockList = doc.getElementsByTagName("stock");
            for (int i = 0; i < stockList.getLength(); i++) {
                Node node = stockList.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;
                    String stockSymbol = element.getAttribute("symbol");
                    if (stockSymbol.equals(symbol)) {
                        stockElement = element;
                        break;
                    }
                }
            }

            // Update the stock amount based on the action
            if (stockElement != null) {
                int currentAmount = Integer.parseInt(stockElement.getTextContent());
                int updatedAmount;
            
                if (action.equals("buy")) {
                    updatedAmount = currentAmount + amount;
                } else if (action.equals("sell")) {
                    updatedAmount = currentAmount - amount;
                } else {
                    return;
                }
            
                // Set the updated amount in the stock element
                stockElement.setTextContent(String.valueOf(updatedAmount));
            
                // Save the changes back to the XML file
                TransformerFactory transformerFactory = TransformerFactory.newInstance();
                Transformer transformer = transformerFactory.newTransformer();
                transformer.setOutputProperty(OutputKeys.INDENT, "no");
                DOMSource source = new DOMSource(doc);
                StreamResult result = new StreamResult(new FileWriter(xmlFile));
                transformer.transform(source, result);
            
                System.out.println("Portfolio updated successfully.");
            } else {
                System.out.println("Stock symbol not found in the portfolio.");
            }
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    // returns current number of stock in portfolio
    public int checkPortfolio(String stockName){
        int currentStockNumber=-1;
        try{
            File strategyXmlFile = new File(clientPortfolio);
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(strategyXmlFile);
            doc.getDocumentElement().normalize();
            NodeList stockList = doc.getElementsByTagName("stock");
            for (int i = 0; i < stockList.getLength(); i++) {
                Element stockElement = (Element) stockList.item(i);
                String symbol = stockElement.getAttribute("symbol");
                if (symbol.equals(stockName)) {
                    currentStockNumber = Integer.parseInt(stockElement.getTextContent());
                }
            }
        }catch (Exception e) {
            e.printStackTrace();
        }
        return currentStockNumber;
    }

}
