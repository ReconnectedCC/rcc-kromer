package cc.reconnected.kromer.models.responses;

import cc.reconnected.kromer.models.domain.Transaction;

import java.util.List;

public class GetTransactionsFromAddressResponse extends GenericResponse {
    public int count;
    public int total;
    public List<Transaction> transactions;
}
