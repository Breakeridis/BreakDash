package com.breakdash.launcher.core;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Primitive discovery fields are located by immutable SDK key definitions, never arbitrary payload values. */
public final class FactoryDiscoveryFields {
    public enum Outcome { DEVICE, ADDRESS_ONLY, UNMAPPED, NO_ADDRESS, INVALID_ADDRESS, WRONG_TYPE, AMBIGUOUS, UNREADABLE }
    public static final class Reading {
        public final Outcome outcome;
        public final FactoryDiscoveryContract.Device device;
        Reading(Outcome outcome,FactoryDiscoveryContract.Device device) { this.outcome=outcome; this.device=device; }
    }
    private final List<String> addressKeys=new ArrayList<>(),nameKeys=new ArrayList<>();
    public final String schema;
    public FactoryDiscoveryFields(Class<?> definitions) {
        StringBuilder out=new StringBuilder("Primitive discovery fields (SDK symbols only; keys/values omitted):\n");
        Field[] fields=definitions.getDeclaredFields(); Arrays.sort(fields,Comparator.comparing(Field::getName));
        int symbols=0;
        for(Field field:fields) {
            int flags=field.getModifiers(); String symbol=field.getName();
            if(field.getType()!=String.class || !Modifier.isPublic(flags) || !Modifier.isStatic(flags) || !Modifier.isFinal(flags)
                    || !symbol.matches("(?:KEY|EXTRA)_[A-Z0-9_]{1,96}")) continue;
            int role=role(symbol);
            if(symbols++<24) out.append(symbol).append(role==1 ? " : address\n" : role==2 ? " : name\n" : " : not used\n");
            if(role==0) continue;
            List<String> keys=role==1 ? addressKeys : nameKeys;
            if(keys.size()>=12) continue;
            try {
                Object value=field.get(null);
                if(value instanceof String) {
                    String key=(String)value;
                    if(!key.isEmpty() && key.length()<=160 && !key.matches(".*[\\p{Cntrl}].*") && !keys.contains(key)) keys.add(key);
                }
            } catch(ReflectiveOperationException | RuntimeException | LinkageError ignored) { /* Public schema only, no error text. */ }
        }
        out.append("Address key count: ").append(addressKeys.size()).append("; name key count: ").append(nameKeys.size()).append('\n');
        schema=out.length()>1600 ? out.substring(0,1560)+"\n[field schema truncated]\n" : out.toString();
    }
    private static int role(String symbol) {
        String prefix="(?:KEY|EXTRA)_(?:(?:BT|BLUETOOTH|BD|DEVICE|DEV|REMOTE|DISCOVERED|FOUND|SEARCHED)_){0,3}";
        if(symbol.matches(prefix+"(?:ADDRESS|ADDR|MAC|BDADDR)")) return 1;
        if(symbol.matches(prefix+"(?:NAME|BTNAME|BDNAME)")) return 2;
        return 0;
    }
    /** Caller gates this to a verified DEVICE_FOUND action. Lookup is exact and SDK-defined. */
    public Reading read(Function<String,Object> lookup) {
        if(addressKeys.isEmpty()) return new Reading(Outcome.UNMAPPED,null);
        Set<String> addresses=new LinkedHashSet<>(),names=new LinkedHashSet<>();
        boolean invalid=false,wrongType=false;
        try {
            for(String key:addressKeys) {
                Object value=lookup.apply(key);
                if(value==null || "".equals(value)) continue;
                if(!(value instanceof String)) { wrongType=true; continue; }
                String address=FactoryDiscoveryContract.normalizeAddress((String)value);
                if(address==null) invalid=true; else addresses.add(address);
            }
            if(addresses.size()>1) return new Reading(Outcome.AMBIGUOUS,null);
            if(wrongType) return new Reading(Outcome.WRONG_TYPE,null);
            if(invalid) return new Reading(Outcome.INVALID_ADDRESS,null);
            if(addresses.isEmpty()) return new Reading(Outcome.NO_ADDRESS,null);
            for(String key:nameKeys) {
                Object value=lookup.apply(key);
                if(value==null || "".equals(value)) continue;
                if(!(value instanceof String)) return new Reading(Outcome.WRONG_TYPE,null);
                String name=FactoryDiscoveryContract.cleanName((String)value);
                if(!FactoryDiscoveryContract.UNNAMED.equals(name)) names.add(name);
            }
            if(names.size()>1) return new Reading(Outcome.AMBIGUOUS,null);
            String name=names.isEmpty() ? FactoryDiscoveryContract.UNNAMED : names.iterator().next();
            return new Reading(names.isEmpty() ? Outcome.ADDRESS_ONLY : Outcome.DEVICE,new FactoryDiscoveryContract.Device(addresses.iterator().next(),name));
        } catch(RuntimeException | LinkageError ignored) { return new Reading(Outcome.UNREADABLE,null); }
    }
}
