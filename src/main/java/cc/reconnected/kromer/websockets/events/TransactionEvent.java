package cc.reconnected.kromer.websockets.events;

import cc.reconnected.kromer.models.domain.Transaction;

public class TransactionEvent extends GenericEvent {

    public Transaction transaction;

    public TransactionEvent(Transaction transaction) {
        super("event", "transaction");
        this.transaction = transaction;
    }
}
