package cc.reconnected.kromer.database;

import cc.reconnected.kromer.responses.Transaction;

import java.util.Arrays;

public class Wallet {
    public String address;
    public String password;
    public Transaction[] incomingNotSeen;

    @Override
    public String toString() {
        return "Wallet{" +
                "address='" + address + '\'' +
                ", password='" + password + '\'' +
                ", incomingNotSeen=" + Arrays.toString(incomingNotSeen) +
                ", outgoingNotSeen=" + Arrays.toString(outgoingNotSeen) +
                '}';
    }

    public Transaction[] outgoingNotSeen;

    public Wallet(String address, String password, Transaction[] incomingNotSeen, Transaction[] outgoingNotSeen) {
        this.address = address;
        this.password = password;
        this.incomingNotSeen = incomingNotSeen;
        this.outgoingNotSeen = outgoingNotSeen;
    }
}
