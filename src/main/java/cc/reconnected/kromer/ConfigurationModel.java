package cc.reconnected.kromer;

public class ConfigurationModel {
    private String url;
    private String internal_key;
    private double welfare;
    private String bot;
    private SupporterModel supporter;

    public ConfigurationModel() {
        // Required for ConfigBeanFactory
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getInternal_key() {
        return internal_key;
    }

    public void setInternal_key(String internal_key) {
        this.internal_key = internal_key;
    }

    public double getWelfare() {
        return welfare;
    }

    public void setWelfare(double welfare) {
        this.welfare = welfare;
    }

    public String getBot() {
        return bot;
    }

    public void setBot(String bot) {
        this.bot = bot;
    }

    public SupporterModel getSupporter() {
        return supporter;
    }

    public void setSupporter(SupporterModel supporter) {
        this.supporter = supporter;
    }

    public static class SupporterModel {
        private double multiplier;
        private String group;

        public SupporterModel() {
            // Required for ConfigBeanFactory
        }

        public double getMultiplier() {
            return multiplier;
        }

        public void setMultiplier(double multiplier) {
            this.multiplier = multiplier;
        }

        public String getGroup() {
            return group;
        }

        public void setGroup(String group) {
            this.group = group;
        }
    }
}
