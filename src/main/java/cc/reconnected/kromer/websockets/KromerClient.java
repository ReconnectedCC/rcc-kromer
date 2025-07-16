package cc.reconnected.kromer.websockets;

import cc.reconnected.kromer.Main;
import cc.reconnected.kromer.database.Wallet;
import cc.reconnected.kromer.responses.Transaction;
import cc.reconnected.kromer.websockets.events.GenericEvent;
import cc.reconnected.kromer.websockets.events.SubscribeEvent;
import cc.reconnected.kromer.websockets.events.TransactionEvent;
import com.google.gson.Gson;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Pair;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.*;

public class KromerClient extends WebSocketClient {
    public MinecraftServer server;
    public KromerClient(URI serverUri, Draft draft) {
        super(serverUri, draft);
    }

    public KromerClient(URI serverURI, MinecraftServer server) {
        super(serverURI);

        this.server = server;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        Main.LOGGER.debug("Websocket opened!");
        this.send(new Gson().toJson(new SubscribeEvent("transactions", 0)));
        this.send(new Gson().toJson(new SubscribeEvent("names", 1)));
    }


    @Override
    public void onMessage(String message) {
        GenericEvent event = new Gson().fromJson(message, GenericEvent.class);

        if(Objects.equals(event.event, "transaction")) {
            TransactionEvent transactionEvent = new Gson().fromJson(message, TransactionEvent.class);
            Pair<UUID, Wallet> toWallet = Main.database.getWallet(transactionEvent.transaction.to);
            if(toWallet == null) return;
            Pair<UUID, Wallet> fromWallet = Main.database.getWallet(transactionEvent.transaction.from);
            if(fromWallet == null) return;
            ServerPlayerEntity fromPlayer = server.getPlayerManager().getPlayer(fromWallet.getLeft());
            ServerPlayerEntity toPlayer = server.getPlayerManager().getPlayer(toWallet.getLeft());

            if(fromPlayer == null) {
                Wallet fromPlayerRealWallet = fromWallet.getRight();
                List<Transaction> outgoingNotSeen = new ArrayList<>(Arrays.asList(fromPlayerRealWallet.outgoingNotSeen));
                outgoingNotSeen.add(transactionEvent.transaction);
                fromPlayerRealWallet.outgoingNotSeen = outgoingNotSeen.toArray(new Transaction[0]);

                Main.database.setWallet(fromWallet.getLeft(), fromPlayerRealWallet);
            } else {
                // TODO: Should I implement notifyTransfer for fromPlayer while it's online? I'm assuming that the player knows
                //       about this transfer.
            }


            if(toPlayer == null) {
                Wallet toPlayerRealWallet = toWallet.getRight();
                List<Transaction> incomingNotSeen = new ArrayList<>(Arrays.asList(toPlayerRealWallet.incomingNotSeen));
                incomingNotSeen.add(transactionEvent.transaction);
                toPlayerRealWallet.incomingNotSeen = incomingNotSeen.toArray(new Transaction[0]);

                Main.database.setWallet(toWallet.getLeft(), toPlayerRealWallet);
            } else {
                Main.notifyTransfer(toPlayer, transactionEvent.transaction);
            }
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        Main.LOGGER.error("Websocket was closed for reason: " + reason +" (C:"+code+").");
    }


    @Override
    public void onError(Exception ex) {
        Main.LOGGER.error("A error occured within the websocket: " + ex);
    }

}
