package cc.reconnected.kromer.database;

import cc.reconnected.kromer.responses.Transaction;

public class Wallet {
    public String address;
    public String password;
    public Transaction[] incomingNotSeen;
    public Transaction[] outgoingNotSeen;

    public Wallet(String address, String password, Transaction[] incomingNotSeen, Transaction[] outgoingNotSeen) {
        this.address = address;
        this.password = password;
    }
}
