package cc.reconnected.kromer.responses;

import java.util.Date;

public class Transaction {
    public int id;
    public String from;
    public String to;
    public float value;
    public Date time;
    public String name;
    public String metadata;
    public String sent_metaname;
    public String sent_name;
    public String type;

    public Transaction(String sent_metaname, int id, String from, String to, float value, Date time, String name, String metadata, String sent_name, String type) {
        this.sent_metaname = sent_metaname;
        this.id = id;
        this.from = from;
        this.to = to;
        this.value = value;
        this.time = time;
        this.name = name;
        this.metadata = metadata;
        this.sent_name = sent_name;
        this.type = type;
    }
}
