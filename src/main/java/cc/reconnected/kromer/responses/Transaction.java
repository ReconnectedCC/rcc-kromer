package cc.reconnected.kromer.responses;

import java.util.Date;
import java.util.Optional;

public class Transaction {
    public int id;
    public String from;
    public String to;
    public float value;
    public Date time;
    public String  name;
    public String metadata;
    public String  sent_metaname;
    public String  sent_name;
    public String type;
}
