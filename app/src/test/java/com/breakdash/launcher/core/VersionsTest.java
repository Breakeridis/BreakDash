package com.breakdash.launcher.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class VersionsTest {
    @Test public void comparesNumericallyAndAcceptsReleaseTags() {
        assertTrue(Versions.compare("v1.10.0","1.9.9")>0);
        assertEquals(0,Versions.compare("v2.0.0+build17","2.0.0"));
        assertTrue(Versions.compare("1.0.0","1.0.0-dev")>0);
    }
    @Test public void prereleaseOrderingFollowsSemver() {
        String[] versions={"1.0.0-alpha","1.0.0-alpha.1","1.0.0-alpha.beta","1.0.0-beta","1.0.0-beta.2","1.0.0-beta.11","1.0.0-rc.1","1.0.0"};
        for(int i=1;i<versions.length;i++) assertTrue(Versions.compare(versions[i-1],versions[i])<0);
    }
    @Test(expected=IllegalArgumentException.class) public void refusesMalformedReleaseTags() { Versions.compare("latest","1.0.0"); }
}
