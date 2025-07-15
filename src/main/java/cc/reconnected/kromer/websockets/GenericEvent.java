package cc.reconnected.kromer.websockets;

public class GenericEvent {
    public String type;
    public String event;

    public GenericEvent(String type, String event) {
        this.type = type;
        this.event = event;
    }
}
