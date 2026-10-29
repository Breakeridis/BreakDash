package com.breakdash.launcher.core;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** Reflection definitions only. Never initializes, constructs, reads a field or invokes a method. */
public final class BluetoothSdkSchema {
    private BluetoothSdkSchema() {}
    public static final class Result {
        public final String text;
        public final List<Class<?>> related;
        public final boolean complete;
        Result(String text,List<Class<?>> related,boolean complete) { this.text=text; this.related=related; this.complete=complete; }
    }
    /** Deduplicate only complete, identical definitions; truncated lookalikes can differ. */
    public static final class Deduplicator {
        private final Map<String,String> definitions=new HashMap<>(),sources=new HashMap<>();
        public String display(String source,String type,Result result) {
            return display(source,type,result,Integer.MAX_VALUE);
        }
        public String display(String source,String type,Result result,int available) {
            if(!result.complete) return result.text;
            if(result.text.equals(definitions.get(type))) return "Type "+type+"\n  Same complete captured schema as "+sources.get(type)+"\n";
            if(result.text.length()<=available) { definitions.put(type,result.text); sources.put(type,source); }
            return result.text;
        }
    }
    public static Result inspect(Class<?> type,int max,BooleanSupplier stopped) {
        max=Math.max(0,max);
        StringBuilder out=new StringBuilder(); Set<Class<?>> related=new LinkedHashSet<>();
        boolean complete=true;
        out.append("Type ").append(type.getName()).append(' ').append(Modifier.toString(type.getModifiers())).append('\n');
        try {
            if(stopped.getAsBoolean()) return new Result(bounded(out+"  Cancelled/time budget\n",max),new ArrayList<>(),false);
            Class<?> parent=type.getSuperclass(); if(parent!=null) { out.append("  Extends ").append(parent.getName()).append('\n'); reference(parent,related); }
            for(Class<?> iface:type.getInterfaces()) { out.append("  Implements ").append(iface.getName()).append('\n'); reference(iface,related); }
            Constructor<?>[] constructors=type.getDeclaredConstructors();
            Arrays.sort(constructors,Comparator.comparingInt((Constructor<?> ctor)->ctor.getParameterCount()).thenComparing(ctor->Arrays.toString(ctor.getParameterTypes()))); int count=0;
            for(Constructor<?> ctor:constructors) {
                if(stopped.getAsBoolean() || out.length()>max-180 || count++>=6) { out.append("  Additional constructors omitted: time/text/count budget\n"); complete=false; break; }
                if(ctor.getParameterCount()>10) complete=false;
                out.append("  Constructor ").append(Modifier.toString(ctor.getModifiers())).append('('); parameters(ctor.getParameterTypes(),out,related); out.append(")\n");
            }
            List<Method> methods=new ArrayList<>(Arrays.asList(type.getDeclaredMethods()));
            for(Method method:type.getMethods()) if(method.getDeclaringClass()!=type && BluetoothInspectionPlan.contract(method.getDeclaringClass().getName())!=null) methods.add(method);
            methods.sort(Comparator.comparingInt((Method method)->Modifier.isStatic(method.getModifiers()) && type.isAssignableFrom(method.getReturnType()) ? 0 : BluetoothInspectionPlan.methodPriority(method.getName())).thenComparing(Method::getName).thenComparing(method->Arrays.toString(method.getParameterTypes())));
            Set<String> seen=new LinkedHashSet<>(); count=0;
            for(Method method:methods) {
                if(method.isSynthetic() || method.isBridge()) continue;
                if(stopped.getAsBoolean() || out.length()>max-220 || count>=96) { out.append("  Additional methods omitted: time/text/count budget\n"); complete=false; break; }
                if(method.getParameterCount()>10) complete=false;
                StringBuilder line=new StringBuilder("  ");
                line.append(Modifier.toString(method.getModifiers())).append(' ').append(method.getReturnType().getName()).append(' ').append(method.getName()).append('(');
                parameters(method.getParameterTypes(),line,related); line.append(')');
                if(!seen.add(line.toString())) continue;
                count++; out.append(line);
                if(method.getDeclaringClass()!=type) out.append(" [inherited: ").append(method.getDeclaringClass().getName()).append(']');
                out.append('\n'); reference(method.getReturnType(),related);
            }
            count=0;
            Field[] fields=type.getDeclaredFields(); Arrays.sort(fields,Comparator.comparing(Field::getName));
            for(Field field:fields) {
                if(!field.getName().toLowerCase(java.util.Locale.ROOT).matches(".*(instance|feature|callback|listener|service|manager|ble|gatt).*")) continue;
                if(stopped.getAsBoolean() || out.length()>max-180 || count>=6) { out.append("  Additional field definitions omitted: time/text/count budget\n"); complete=false; break; }
                out.append("  Field type only: ").append(Modifier.toString(field.getModifiers())).append(' ').append(field.getType().getName()).append(' ').append(field.getName()).append('\n');
                count++; reference(field.getType(),related);
            }
        } catch(LinkageError | RuntimeException error) { out.append("  Metadata unavailable: ").append(error.getClass().getSimpleName()).append('\n'); complete=false; }
        return new Result(bounded(out.toString(),max),new ArrayList<>(related),complete && out.length()<=max);
    }
    private static String bounded(String value,int max) {
        if(value.length()<=max) return value;
        String suffix="\n[schema text truncated]\n";
        return max<suffix.length() ? suffix.substring(0,max) : value.substring(0,max-suffix.length())+suffix;
    }
    private static void parameters(Class<?>[] types,StringBuilder out,Set<Class<?>> related) {
        for(int i=0;i<Math.min(types.length,10);i++) { if(i>0) out.append(", "); out.append(types[i].getName()); reference(types[i],related); }
        if(types.length>10) out.append(", ...");
    }
    private static void reference(Class<?> type,Set<Class<?>> related) {
        while(type.isArray()) type=type.getComponentType();
        if(related.size()<16 && BluetoothInspectionPlan.contract(type.getName())!=null) related.add(type);
    }
}
