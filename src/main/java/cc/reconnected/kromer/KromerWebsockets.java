package cc.reconnected.kromer;

import cc.reconnected.kromer.database.Wallet;

import java.net.URI;
import java.util.*;

import com.google.gson.Gson;
import net.minecraft.server.MinecraftServer;
import ovh.sad.jkromer.models.Transaction;
import ovh.sad.jkromer.websocket.AbstractKromerClient;

public class KromerWebsockets extends AbstractKromerClient {

    public final MinecraftServer server;

    public KromerWebsockets(URI uri, MinecraftServer server) {
        super(uri, new Gson());
        this.server = server;
    }

    @Override
    protected void onConnected() {
        Kromer.kromerStatus = true;

        for (int i = 0; i < Kromer.welfareQueued; i++) {
            Kromer.executeWelfare();
        }
        Kromer.welfareQueued = 0;
    }

    @Override
    protected void onDisconnected(int code, String reason, boolean remote) {
        Kromer.kromerStatus = false;
    }

    @Override
    protected void onTransactionReceived(Transaction tx) {
        var toWallet = Kromer.database.getWallet(tx.to);
        var fromWallet = Kromer.database.getWallet(tx.from);

        if (toWallet == null || fromWallet == null) return;

        var toPlayer = server.getPlayerManager().getPlayer(toWallet.getLeft());
        var fromPlayer = server.getPlayerManager().getPlayer(fromWallet.getLeft());

        if (toPlayer == null) {
            var realWallet = toWallet.getRight();
            realWallet.incomingNotSeen = appendTransaction(realWallet.incomingNotSeen, tx);
            Kromer.database.setWallet(toWallet.getLeft(), realWallet);
        } else {
            Kromer.notifyTransfer(toPlayer, tx);
        }

        if (fromPlayer == null) {
            var realWallet = fromWallet.getRight();
            realWallet.outgoingNotSeen = appendTransaction(realWallet.outgoingNotSeen, tx);
            Kromer.database.setWallet(fromWallet.getLeft(), realWallet);
        }
    }

    @Override
    protected void onUnknownMessage(String eventType, String rawMessage) {
        // Make some error handling for this
    }

    @Override
    protected void reconnectClient() {
        try {
            Kromer.connectWebsocket(server);
        } catch (Exception e) {
            // Shouldn't fail
        }
    }

    private Transaction[] appendTransaction(Transaction[] arr, Transaction tx) {
        var list = new ArrayList<>(List.of(arr));
        list.add(tx);
        return list.toArray(new Transaction[0]);
    }
}