package cc.reconnected.kromer.websockets;

import cc.reconnected.kromer.Main;
import org.java_websocket.client.WebSocketClient;

import java.net.URI;
import java.net.URISyntaxException;

public class Websockets {
    public Websockets() {
        try {
            WebSocketClient client = new KromerClient( new URI(Main.config.KromerURL().replace("http", "ws")+"api/krist/ws") );
            client.connect();
        } catch (Exception e) {
            // @TODO: Lmao
        }
    }
}
