package com.breakdash.launcher.core;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Discovery controls and device model use installed SDK definitions, never a guessed Parcel layout. */
public final class FactoryDiscoveryContract {
    public static final class Device {
        public final String address,name;
        Device(String address,String name) { this.address=address; this.name=name; }
    }
    private final Class<?> deviceType;
    private final Method start,stop,getName,getAddress;
    public final List<String> actions;
    public final String schema;
    public FactoryDiscoveryContract(Class<?> feature,Class<?> definitions,Class<?> deviceType) throws ReflectiveOperationException {
        this.deviceType=deviceType;
        start=method(feature,"startSearchDevice_BC",boolean.class); stop=method(feature,"stopSearchDevice_BC",boolean.class);
        getName=method(deviceType,"getName",String.class); getAddress=method(deviceType,"getAddress",String.class);
        Set<String> selected=new LinkedHashSet<>(); StringBuilder out=new StringBuilder();
        Field[] fields=definitions.getDeclaredFields(); Arrays.sort(fields,Comparator.comparing(Field::getName));
        for(Field field:fields) {
            String symbol=field.getName(); int flags=field.getModifiers();
            if(field.getType()!=String.class || !Modifier.isPublic(flags) || !Modifier.isStatic(flags) || !Modifier.isFinal(flags)
                    || !symbol.matches("[A-Z][A-Z0-9_]{0,99}") || !symbol.contains("ACTION")
                    || !symbol.matches(".*(DISCOVER|SEARCH|FOUND).*")) continue;
            if(selected.size()>=16) { out.append("Additional discovery definitions omitted\n"); break; }
            // Only immutable public protocol constants are read, inside the explicit live discovery action.
            Object value=field.get(null);
            if(!(value instanceof String)) continue;
            String action=(String)value;
            if(!action.matches("com\\.(?:bt|nwd)\\.[A-Za-z0-9_.$]{1,170}")) continue;
            selected.add(action); if(out.length()<1200) out.append(symbol).append(" = ").append(action).append('\n');
        }
        if(selected.isEmpty()) {
            out.append("No recognized discovery action values. Public action symbols (names only):\n");
            for(Field field:fields) if(out.length()<1200 && field.getType()==String.class && Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers()) && field.getName().contains("ACTION")) out.append(field.getName()).append('\n');
        }
        actions=new ArrayList<>(selected); schema=out.toString();
    }
    private static Method method(Class<?> type,String name,Class<?> result) throws ReflectiveOperationException {
        Method method=type.getMethod(name);
        if(Modifier.isStatic(method.getModifiers()) || method.getReturnType()!=result) throw new NoSuchMethodException(name);
        return method;
    }
    public boolean start(Object feature) throws ReflectiveOperationException { return (Boolean)start.invoke(feature); }
    public boolean stop(Object feature) throws ReflectiveOperationException { return (Boolean)stop.invoke(feature); }
    /** Only actual SDK BTDevice instances are decoded. Other payload values are ignored. */
    public List<Device> devices(Object value) {
        List<Device> result=new ArrayList<>(); int[] budget={64};
        collect(value,result,new IdentityHashMap<>(),budget,0); return result;
    }
    private void collect(Object value,List<Device> result,IdentityHashMap<Object,Boolean> seen,int[] budget,int depth) {
        if(value==null || result.size()>=32 || budget[0]--<=0 || depth>2 || seen.put(value,true)!=null) return;
        try {
            if(deviceType.isInstance(value)) {
                String address=(String)getAddress.invoke(value);
                if(address==null || !address.matches("(?i)[0-9a-f]{2}(?::[0-9a-f]{2}){5}")) return;
                String name=(String)getName.invoke(value);
                if(name==null || name.trim().isEmpty()) name="Unnamed factory device";
                name=name.replaceAll("[\\p{Cntrl}]"," "); name=name.substring(0,Math.min(80,name.length()));
                result.add(new Device(address.toUpperCase(Locale.ROOT),name));
            } else if(value instanceof Object[]) {
                Object[] values=(Object[])value;
                for(int i=0;i<values.length && budget[0]>0 && result.size()<32;i++) collect(values[i],result,seen,budget,depth+1);
            } else if(value instanceof List<?>) {
                List<?> values=(List<?>)value;
                for(int i=0;i<Math.min(values.size(),64) && budget[0]>0 && result.size()<32;i++) collect(values.get(i),result,seen,budget,depth+1);
            }
        } catch(ReflectiveOperationException | RuntimeException | LinkageError ignored) { /* No exception messages or opaque values are retained. */ }
    }
}
