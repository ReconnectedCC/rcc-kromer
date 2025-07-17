package cc.reconnected.kromer.database;

import cc.reconnected.kromer.models.Transaction;

import java.util.Arrays;

public class Wallet {
    public String address;
    public String privatekey;
    public Transaction[] incomingNotSeen;

    @Override
    public String toString() {
        return "Wallet{" +
                "address='" + address + '\'' +
                ", privatekey='" + privatekey + '\'' +
                ", incomingNotSeen=" + Arrays.toString(incomingNotSeen) +
                ", outgoingNotSeen=" + Arrays.toString(outgoingNotSeen) +
                '}';
    }

    public Transaction[] outgoingNotSeen;

    public Wallet(String address, String privatekey, Transaction[] incomingNotSeen, Transaction[] outgoingNotSeen) {
        this.address = address;
        this.privatekey = privatekey;
        this.incomingNotSeen = incomingNotSeen;
        this.outgoingNotSeen = outgoingNotSeen;
    }
}
