package com.breakdash.launcher.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class FactoryApkTrustTest {
    @Test public void acceptsOnlyReadableReadOnlyFactoryApks() {
        assertTrue(FactoryApkTrust.accepts(true,"/system/app/Radio/Radio.apk",true,true,false));
        assertTrue(FactoryApkTrust.accepts(true,"/product/app/Radio/base.apk",true,true,false));
        assertFalse(FactoryApkTrust.accepts(true,"/system/app/Radio/Radio.apk",true,true,true));
        assertFalse(FactoryApkTrust.accepts(true,"/system/app/Radio/Radio.apk",false,true,false));
        assertFalse(FactoryApkTrust.accepts(true,"/system/app/Radio/Radio.apk",true,false,false));
    }
    @Test public void rejectsPackageSquattingUpdatesAndPathsOutsideFactoryPartitions() {
        assertFalse(FactoryApkTrust.accepts(false,"/system/app/Radio/Radio.apk",true,true,false));
        assertFalse(FactoryApkTrust.accepts(true,"/data/app/radio/base.apk",true,true,false));
        assertFalse(FactoryApkTrust.accepts(true,"/system-malicious/Radio.apk",true,true,false));
        assertFalse(FactoryApkTrust.accepts(true,"/system/../data/Radio.apk",true,true,false));
        assertFalse(FactoryApkTrust.accepts(true,"/sdcard/Radio.apk",true,true,false));
        assertFalse(FactoryApkTrust.accepts(true,null,true,true,false));
    }
}
