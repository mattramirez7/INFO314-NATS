import io.nats.client.*;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;

public class Producer {
    private static Connection nc = null;

    public static void main(String... args) throws Exception {
        String natsURL = "nats://localhost:4222";
        if (args.length > 0) {
            natsURL = args[0];
        }

        nc = Nats.connect(natsURL);
        System.console().writer().println("Executing Test Purchase");
        nc.publish("orders", "<orderReceipt><sell symbol=\"MSFT\" amount=\"40\" /><complete amount=\"180000\" /></orderReceipt>".getBytes());
        System.out.println("done");
        System.exit(0);
        
    }
}
