package com.breakdash.launcher.core;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.BooleanSupplier;

/** Calls only observed, zero-argument primitive status getters on the real factory proxy. */
public final class FactoryBluetoothStatus {
    private static final String[] BOOLEAN_GETTERS={"isBlueToothPowerOn","isConnectDevice","isConnectHFP","isConnectA2DP","isConnectAVRCP","isSppConnect"};
    private static final String[] INTEGER_GETTERS={"getBleRemoteState","getDiscoverMode","getAutoFeature","getLocalDeviceFeature"};
    private final Class<?> contract;
    public FactoryBluetoothStatus(Class<?> contract) { this.contract=contract; }
    public static final class Reading {
        public final String text;
        public final int values;
        Reading(String text,int values) { this.text=text; this.values=values; }
        public boolean complete() { return values==BOOLEAN_GETTERS.length+INTEGER_GETTERS.length; }
    }
    public Reading read(Object feature,BooleanSupplier stopped) throws InterruptedException {
        if(feature==null || !contract.isInstance(feature)) throw new IllegalArgumentException("Invalid factory feature");
        StringBuilder out=new StringBuilder(); int values=0;
        for(int group=0;group<2;group++) for(String name:group==0 ? BOOLEAN_GETTERS : INTEGER_GETTERS) {
            if(stopped.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new InterruptedException();
            out.append(name).append(": ");
            try {
                Method method=contract.getMethod(name);
                Class<?> expected=group==0 ? boolean.class : int.class;
                if(method.getReturnType()!=expected || Modifier.isStatic(method.getModifiers())) out.append("UNSUPPORTED_SIGNATURE");
                else { Object value=method.invoke(feature); out.append(group==0 ? Boolean.toString((Boolean)value) : Integer.toString((Integer)value)); values++; }
            } catch(ReflectiveOperationException | RuntimeException | LinkageError error) { out.append("UNAVAILABLE"); }
            out.append('\n');
        }
        if(stopped.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new InterruptedException();
        return new Reading(out.toString(),values);
    }
}
