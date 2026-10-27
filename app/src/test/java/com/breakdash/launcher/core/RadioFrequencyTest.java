package com.breakdash.launcher.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class RadioFrequencyTest {
    @Test public void tunerUsesTenKhzUnitsForFm() {
        assertEquals("102.50",RadioFrequency.number(10250));
        assertEquals("MHz",RadioFrequency.unit(10250));
        assertEquals("531",RadioFrequency.number(531));
        assertEquals("kHz",RadioFrequency.unit(531));
    }
    @Test public void onlyDocumentedFmAndAmRangesAreValid() {
        for(int boundary:new int[]{8750,10800,531,1620}) assertTrue(RadioFrequency.valid(boundary));
        for(int outside:new int[]{-1,0,530,1621,8749,10801,102500}) assertFalse(RadioFrequency.valid(outside));
    }
}
