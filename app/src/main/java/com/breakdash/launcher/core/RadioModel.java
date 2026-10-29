package com.breakdash.launcher.core;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Reads named numeric members of a real vendor model; never deserializes or invents a Parcel layout. */
public final class RadioModel {
    public static final class Reading {
        public final int frequency;
        public final Integer band;
        public Reading(int frequency,Integer band) { this.frequency=frequency; this.band=band; }
    }
    private interface Accessor { Object get(Object model) throws ReflectiveOperationException; }
    private final Class<?> type;
    private final List<Accessor> frequencies=new ArrayList<>(),bands=new ArrayList<>();
    private final String schema;
    public RadioModel(Class<?> type) {
        this.type=type; StringBuilder description=new StringBuilder(type.getName()); int described=0;
        Class<?> current=type;
        for(int depth=0;current!=null && current!=Object.class && depth<4;depth++,current=current.getSuperclass()) {
            for(Field field:current.getDeclaredFields()) {
                if(Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                if(described++<24) description.append("\n  field ").append(field.getName()).append(": ").append(field.getType().getSimpleName());
                int role=role(field.getName()); if(role==0 || !numeric(field.getType())) continue;
                try { field.setAccessible(true); add(role,field::get); } catch(RuntimeException ignored) {}
            }
            for(Method method:current.getDeclaredMethods()) {
                if(Modifier.isStatic(method.getModifiers()) || method.isSynthetic() || method.getParameterTypes().length!=0) continue;
                int role=role(method.getName()); if(role==0 || !method.getName().startsWith("get") || !numeric(method.getReturnType())) continue;
                if(described++<24) description.append("\n  getter ").append(method.getName()).append(": ").append(method.getReturnType().getSimpleName());
                try { method.setAccessible(true); add(role,model->method.invoke(model)); } catch(RuntimeException ignored) {}
            }
        }
        schema=description.toString();
    }
    public Reading read(Object model) {
        if(model==null || !type.isInstance(model)) return new Reading(0,null);
        Integer frequency=unique(frequencies,model,false),band=unique(bands,model,true);
        return new Reading(frequency!=null && RadioFrequency.valid(frequency) ? frequency : 0,
                band!=null && band>=-128 && band<=255 ? band : null);
    }
    public String schema() { return schema; }
    public static Integer integer(Object value) {
        if(!(value instanceof Number)) return null;
        double number=((Number)value).doubleValue();
        return Double.isFinite(number) && number==Math.rint(number) && number>=Integer.MIN_VALUE && number<=Integer.MAX_VALUE ? (int)number : null;
    }
    private void add(int role,Accessor accessor) { List<Accessor> list=role==1 ? frequencies : bands; if(list.size()<8) list.add(accessor); }
    private static int role(String name) {
        switch(name.toLowerCase(Locale.ROOT)) {
            case "frequency": case "freq": case "mfrequency": case "mfreq": case "m_frequency": case "m_freq": case "getfrequency": case "getfreq": return 1;
            case "band": case "mband": case "m_band": case "getband": return 2;
            default: return 0;
        }
    }
    private static boolean numeric(Class<?> type) {
        return Number.class.isAssignableFrom(type) || type==int.class || type==byte.class || type==short.class || type==long.class || type==float.class || type==double.class;
    }
    private static Integer unique(List<Accessor> accessors,Object model,boolean band) {
        Integer result=null;
        for(Accessor accessor:accessors) {
            try {
                Object value=accessor.get(model); if(value==null) continue;
                Integer integer=integer(value); if(integer==null) return null;
                if(band) { if(integer<-128 || integer>255) return null; integer=(int)integer.byteValue(); }
                if(result!=null && !result.equals(integer)) return null; result=integer;
            } catch(ReflectiveOperationException | RuntimeException ignored) { return null; }
        }
        return result;
    }
}
