package com.breakdash.launcher.core;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** Main-thread discovery observations. Local records and identity-free evidence have separate accessors. */
public final class FactoryDiscoveryEvents {
    public interface Payload {
        Object get(String key);
        Iterable<?> values();
    }
    private final FactoryDiscoveryContract contract;
    private final BluetoothDiscovery<String,FactoryDiscoveryContract.Device> found=new BluetoothDiscovery<>();
    private final Map<String,Integer> actions=new LinkedHashMap<>(),decoding=new LinkedHashMap<>();
    private final Set<String> shapes=new LinkedHashSet<>();
    private int broadcasts,foundEvents,unhandled,unreadable;
    public FactoryDiscoveryEvents(FactoryDiscoveryContract contract) { this.contract=contract; }
    public List<FactoryDiscoveryContract.Device> devices() { return found.rows(); }
    public boolean partial() { return unhandled>0 || unreadable>0; }
    public void observe(String action,Supplier<Payload> load) {
        if(!contract.actions.contains(action)) return;
        broadcasts++; increment(actions,contract.actionSymbol(action));
        // Start/end/signal notifications cannot manufacture devices, even if they contain address-like strings.
        if(!contract.isDeviceFoundAction(action)) return;
        foundEvents++;
        try {
            Payload payload=load.get(); int parsed=0,count=0;
            if(payload!=null) {
                for(Object value:payload.values()) {
                    if(count++>=16) break;
                    if(shapes.size()<12) shapes.add(shape(value));
                    for(FactoryDiscoveryContract.Device device:contract.devices(value)) { merge(device); parsed++; }
                }
            }
            if(parsed>0) increment(decoding,"SDK_MODELS");
            else {
                FactoryDiscoveryFields.Reading reading=contract.primitiveDevice(action,key->payload==null ? null : payload.get(key));
                increment(decoding,reading.outcome.name());
                if(reading.device!=null) merge(reading.device);
                else if(reading.outcome==FactoryDiscoveryFields.Outcome.UNREADABLE) unreadable++;
                else unhandled++;
            }
        } catch(RuntimeException | LinkageError ignored) { unreadable++; }
    }
    private void merge(FactoryDiscoveryContract.Device device) {
        FactoryDiscoveryContract.Device row=found.getOrAdd(device.address,()->device);
        if(row!=null && !FactoryDiscoveryContract.UNNAMED.equals(device.name)) row.name=device.name;
    }
    private static void increment(Map<String,Integer> counters,String key) { counters.put(key,counters.getOrDefault(key,0)+1); }
    private static String shape(Object value) {
        if(value==null) return "null";
        if(value instanceof List<?>) return "List (contents omitted)";
        if(value instanceof Object[]) return "Object array (contents omitted)";
        if(value.getClass().getName().equals("com.bt.BTDevice")) return "SDK BTDevice";
        if(value instanceof String) return "String (value omitted)";
        if(value instanceof Number || value instanceof Boolean) return "Primitive (value omitted)";
        return "Opaque value omitted";
    }
    public String evidence() {
        StringBuilder out=new StringBuilder("Discovery broadcasts: "+broadcasts+"\nDevice-found events: "+foundEvents
                +"\nUndecodable device-found events: "+unhandled+"\nUnreadable device-found events: "+unreadable
                +"\nLocal devices: "+found.rows().size()+" (cap 32)\nAction counts (SDK symbols):\n");
        actions.forEach((symbol,count)->out.append("  ").append(symbol).append(": ").append(count).append('\n'));
        out.append("Device-field decoding outcomes:\n");
        decoding.forEach((outcome,count)->out.append("  ").append(outcome).append(": ").append(count).append('\n'));
        out.append("Device-found payload shapes only: ").append(String.join(", ",shapes)).append('\n');
        if(foundEvents==0) out.append("No SDK device-found action was received. Control/status events were not decoded as devices.\n");
        return out.toString();
    }
}
