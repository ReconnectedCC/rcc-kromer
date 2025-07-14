package cc.reconnected.kromer.responses;

import com.google.gson.annotations.SerializedName;


public class MotdResponse {
    public class Constants {
        public int wallet_version;
        public int nonce_max_size;
        public int name_cost;
        public int min_work;
        public int max_work;
        public double work_factor;
        public int seconds_per_block;
    }

    public static class Currency {
        public String address_prefix;
        public String name_suffix;
        public String currency_name;
        public String currency_symbol;
    }

    public class Package {
        public String name;
        public String version;
        public String author;
        public String licence;
        public String repository;
    }

    public boolean ok;
    public String server_time;
    public String motd;
    public Object set;
    public Object motd_set;
    public String public_url;
    public String public_ws_url;
    public boolean mining_enabled;
    public boolean transactions_enabled;
    public boolean debug_mode;
    public int work;
    public Object last_block;
    @SerializedName("package")
    public Package motdPackage;
    public Constants constants;
    public Currency currency;
    public String notice;
}

