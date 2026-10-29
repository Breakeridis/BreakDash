package com.breakdash.launcher.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class MapStylesTest {
    @Test public void missingAndBlankOverridesSelectBundledMaps() {
        for (String value : new String[]{null, "", "  "}) {
            assertEquals(MapStyles.DAY, MapStyles.uri(true, value));
            assertEquals(MapStyles.NIGHT, MapStyles.uri(false, value));
        }
    }

    @Test public void previousDefaultsMigrateIndependently() {
        assertEquals(MapStyles.DAY, MapStyles.uri(true, "https://tiles.openfreemap.org/styles/positron"));
        assertEquals(MapStyles.NIGHT, MapStyles.uri(false, "https://tiles.openfreemap.org/styles/dark/"));
        assertEquals("", MapStyles.override(true, " https://tiles.openfreemap.org/styles/positron/ "));
        // A driver's explicitly swapped themes are custom overrides.
        assertEquals("https://tiles.openfreemap.org/styles/dark", MapStyles.uri(true, "https://tiles.openfreemap.org/styles/dark"));
        assertEquals("https://tiles.openfreemap.org/styles/positron", MapStyles.uri(false, "https://tiles.openfreemap.org/styles/positron"));
    }

    @Test public void customProvidersAndTheirUrlParametersRemainUntouched() {
        for (String url : new String[]{"https://maps.example/style.json?theme=night&v=2", "https://tiles.openfreemap.org/styles/bright", "https://tiles.openfreemap.org/styles/dark-custom"}) {
            assertEquals(url, MapStyles.uri(true, url));
            assertEquals(url, MapStyles.uri(false, url));
        }
    }
}
