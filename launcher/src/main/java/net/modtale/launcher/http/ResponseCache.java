package net.modtale.launcher.http;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public final class ResponseCache<K, V> {
    private record Entry<V>(V value, Instant writtenAt) { }

    private final Clock clock;
    private final int capacity;
    private final LinkedHashMap<K, Entry<V>> entries = new LinkedHashMap<>(16, 0.75f, true);
    private final Object[] locks = java.util.stream.IntStream.range(0, 64)
            .mapToObj(i -> new Object()).toArray();

    public ResponseCache(int capacity, Clock clock) {
        if (capacity <= 0) throw new IllegalArgumentException("Cache capacity must be positive");
        this.capacity = capacity;
        this.clock = Objects.requireNonNull(clock);
    }

    public synchronized Optional<V> get(K key, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) return Optional.empty();
        Entry<V> entry = entries.get(key);
        if (entry == null) return Optional.empty();
        Duration age = Duration.between(entry.writtenAt(), clock.instant());
        return !age.isNegative() && age.compareTo(ttl) < 0 ? Optional.of(entry.value()) : Optional.empty();
    }

    public void put(K key, V value) {
        put(key, value, clock.instant());
    }

    public synchronized void put(K key, V value, Instant writtenAt) {
        entries.put(Objects.requireNonNull(key), new Entry<>(Objects.requireNonNull(value), Objects.requireNonNull(writtenAt)));
        if (entries.size() > capacity) entries.remove(entries.keySet().iterator().next());
    }

    public synchronized boolean contains(K key) {
        return entries.containsKey(key);
    }

    public synchronized void invalidate(K key) {
        entries.remove(key);
    }

    public synchronized void clear() {
        entries.clear();
    }

    public <T> T withRequestLock(K key, Supplier<T> request) {
        synchronized (locks[Math.floorMod(key.hashCode(), locks.length)]) {
            return request.get();
        }
    }
}
