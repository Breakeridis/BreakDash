package com.breakdash.launcher.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** In-memory discovery rows, preserving first-seen order and updating existing devices at capacity. */
public final class BluetoothDiscovery<K,V> {
    private final Map<K,V> byKey=new HashMap<>();
    private final List<V> rows=new ArrayList<>();
    private final List<V> view=Collections.unmodifiableList(rows);
    public List<V> rows() { return view; }
    public V getOrAdd(K key,Supplier<V> create) {
        Objects.requireNonNull(key);
        V existing=byKey.get(key); if(existing!=null) return existing;
        if(rows.size()>=BluetoothSession.MAX_DEVICES) return null;
        V value=Objects.requireNonNull(create.get()); byKey.put(key,value); rows.add(value); return value;
    }
    public void clear() { byKey.clear(); rows.clear(); }
}
