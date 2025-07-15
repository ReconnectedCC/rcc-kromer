package cc.reconnected.kromer.websockets;

import cc.reconnected.kromer.responses.Transaction;

public class TransactionEvent extends GenericEvent {
    public Transaction transaction;

    public TransactionEvent(Transaction transaction) {
        super("event", "transaction");
        this.transaction = transaction;
    }
}