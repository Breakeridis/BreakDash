package com.breakdash.launcher.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class RadioModelTest {
    static class Fields { private final int frequency=10250; public byte band=2; private final String privateNote="synthetic-never-report-this-value"; }
    static class Getters { public int getFreq() { return 8750; } public int getBand() { return 255; } }
    static class Ambiguous { public int frequency=8750,freq=10800; public int band=1000; }
    static class Unknown { public int value=10250; }
    static class WrongUnits { public double frequency=102.5; }
    static class Broken { public int getFrequency() { throw new IllegalStateException("synthetic failure"); } }
    static class ByteAliases { public int freq=531; public byte band=-1; public int getBand() { return 255; } }
    @Test public void readsActualNamedFieldsAndGettersWithoutAssumingMemberOrder() {
        RadioModel.Reading fields=new RadioModel(Fields.class).read(new Fields());
        assertEquals(10250,fields.frequency); assertEquals(Integer.valueOf(2),fields.band);
        RadioModel.Reading getters=new RadioModel(Getters.class).read(new Getters());
        assertEquals(8750,getters.frequency); assertEquals(Integer.valueOf(-1),getters.band);
        assertEquals(Integer.valueOf(-1),new RadioModel(ByteAliases.class).read(new ByteAliases()).band);
    }
    @Test public void refusesAmbiguousMembersUnknownNamesAndDifferentUnits() {
        RadioModel.Reading ambiguous=new RadioModel(Ambiguous.class).read(new Ambiguous());
        assertEquals(0,ambiguous.frequency); assertNull(ambiguous.band);
        assertEquals(0,new RadioModel(Unknown.class).read(new Unknown()).frequency);
        assertEquals(0,new RadioModel(WrongUnits.class).read(new WrongUnits()).frequency);
        assertEquals(0,new RadioModel(Broken.class).read(new Broken()).frequency);
        assertEquals(0,new RadioModel(Fields.class).read(null).frequency);
    }
    @Test public void schemaReportsNamesAndTypesRatherThanObjectValues() {
        String schema=new RadioModel(Fields.class).schema();
        assertTrue(schema.contains("frequency: int")); assertTrue(schema.contains("band: byte"));
        assertFalse(schema.contains("synthetic-never-report-this-value")); assertFalse(schema.contains("10250"));
    }
    @Test public void numericExtrasMustBeWholeIntegersRatherThanCoercedStringsOrTruncatedFloats() {
        assertEquals(Integer.valueOf(10250),RadioModel.integer(10250));
        assertNull(RadioModel.integer(8750.5)); assertNull(RadioModel.integer(Double.NaN));
        assertNull(RadioModel.integer(Long.MAX_VALUE)); assertNull(RadioModel.integer("10250"));
    }
}
