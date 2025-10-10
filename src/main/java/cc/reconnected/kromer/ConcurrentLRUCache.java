package cc.reconnected.kromer;

import java.util.*;
import java.util.concurrent.*;

public class ConcurrentLRUCache<K, V> {
    private final int capacity;
    private final Map<K, V> map = new ConcurrentHashMap<>();
    private final Deque<K> order = new ConcurrentLinkedDeque<>();

    public ConcurrentLRUCache(int capacity) {
        this.capacity = capacity;
    }

    public synchronized void put(K key, V value) {
        if (map.containsKey(key)) order.remove(key);
        else if (map.size() >= capacity) map.remove(order.removeFirst());
        map.put(key, value);
        order.addLast(key);
    }

    public synchronized V get(K key) {
        if (!map.containsKey(key)) return null;
        order.remove(key);
        order.addLast(key);
        return map.get(key);
    }

    @Override
    public String toString() {
        return map.toString();
    }
}