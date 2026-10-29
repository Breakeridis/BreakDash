package com.breakdash.launcher.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class ThemePaletteTest {
    @Test public void contrastScaleHasTheExpectedEndpoints() {
        assertEquals(21,ThemePalette.contrast(0xFF000000,0xFFFFFFFF),1e-9);
        assertEquals(1,ThemePalette.contrast(0xFF536170,0xFF536170),1e-9);
    }
    @Test public void essentialTextAndSemanticLabelsMeetEnhancedContrastInBothThemes() {
        for(boolean day:new boolean[]{true,false}) {
            ThemePalette palette=new ThemePalette(day);
            for(int surface:new int[]{palette.background,palette.surface})
                for(int foreground:new int[]{palette.text,palette.secondary,palette.navigation,palette.radio,palette.active,palette.danger})
                    assertTrue("Essential text needs 7:1",ThemePalette.contrast(foreground,surface)>=7);
            assertTrue(ThemePalette.contrast(ThemePalette.ON_FILLED,palette.navigationFill)>=7);
            assertTrue(ThemePalette.contrast(ThemePalette.ON_FILLED,palette.radioFill)>=7);
        }
    }
    @Test public void bordersAndSwitchThumbsRemainVisibleIncludingPressedControls() {
        for(boolean day:new boolean[]{true,false}) {
            ThemePalette palette=new ThemePalette(day);
            assertTrue(ThemePalette.contrast(palette.outline,palette.surface)>=3);
            int pressedSurface=blend(palette.neutralRipple(),palette.surface),pressedOutline=blend(palette.neutralRipple(),palette.outline);
            assertTrue(ThemePalette.contrast(pressedOutline,pressedSurface)>=3);
            for(int foreground:new int[]{palette.text,palette.secondary,palette.navigation,palette.radio,palette.active,palette.danger})
                assertTrue(ThemePalette.contrast(foreground,pressedSurface)>=7);
            int thumb=day ? ThemePalette.ON_FILLED : palette.background;
            assertTrue(ThemePalette.contrast(thumb,palette.active)>=3);
            assertTrue(ThemePalette.contrast(thumb,palette.outline)>=3);
        }
    }
    private static int blend(int overlay,int base) {
        double alpha=((overlay>>>24)&255)/255.0; int result=0xFF000000;
        for(int shift:new int[]{16,8,0}) result|=((int)Math.round(((overlay>>>shift)&255)*alpha+((base>>>shift)&255)*(1-alpha)))<<shift;
        return result;
    }
}
