package com.breakdash.launcher.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class ReportPrivacyTest {
    @Test public void vendorLocationAndIdentityKeysAreMaskedRegardlessOfPunctuationOrCase() {
        for(String key:new String[]{"persist.zj.BTmac","localaddr","sys.nwd.net.loc","sys.nwd.net.city","sys.nwd.net.country","persist.zj.mfi.id","sys.zlink.regcode","key_current_bt_pincode","bt_phone_name","ro.boot.emmc.cid.value","ro.product.indent","net.dns1","vendor.latitude","vendor.access_token","persist.wifi.ssid"}) {
            assertEquals(key,"[redacted]",ReportPrivacy.value(key,"PRIVATE-SYNTHETIC-VALUE"));
        }
    }
    @Test public void usefulHardwareAndRadioValuesArePreserved() {
        assertEquals("zkwAT6558R",ReportPrivacy.value("persist.sys.nwd.gps-hw.version","zkwAT6558R"));
        assertEquals("8800_BT5.4_WIFI6/2025:07:07:14:30:10",ReportPrivacy.value("nwd_bt_version","8800_BT5.4_WIFI6/2025:07:07:14:30:10"));
        assertEquals("40",ReportPrivacy.value("mcu_max_volume","40"));
        assertEquals("10550",ReportPrivacy.value("nwd_radio_current_freq","10550"));
        assertEquals("V3.6-FF01-20250423-A02",ReportPrivacy.value("mcu_version","V3.6-FF01-20250423-A02"));
    }
    @Test public void unexpectedKeyNamesCannotExposeCommonValueFormats() {
        String value="IPv4 192.0.2.42; IPv6 2001:db8::1234; MAC 02:11:22:33:44:55; packed 021122334455; GPS 12.3456,23.4567; mail test@example.test; https://example.test/?token=synthetic";
        String masked=ReportPrivacy.value("vendor.unknown",value);
        for(String privateValue:new String[]{"192.0.2.42","2001:db8::1234","02:11:22:33:44:55","021122334455","12.3456","23.4567","test@example.test","example.test"}) assertFalse(privateValue,masked.contains(privateValue));
    }
    @Test public void multilineSensitivePropertyCannotLeakContinuationLines() {
        String raw="[vendor.token]: [SECRET-FIRST\nSECRET-SECOND]\n[persist.sys.nwd.gps-hw.version]: [gps-model]\n[sys.nwd.net.loc]: [12.3456,23.4567]\n";
        String masked=ReportPrivacy.properties(raw);
        assertFalse(masked.contains("SECRET")); assertFalse(masked.contains("12.3456"));
        assertTrue(masked.contains("[vendor.token]: [[redacted]]")); assertTrue(masked.contains("[persist.sys.nwd.gps-hw.version]: [gps-model]"));
    }
    @Test public void multilineNonSensitiveValuesRemainReadableAndLooseOutputIsOmitted() {
        String raw="unexpected loose data\n[odm.kernel.version]: [4.9.170\n]\n[vendor.boot.reason]: [reboot\nfallback]\n";
        String masked=ReportPrivacy.properties(raw);
        assertFalse(masked.contains("unexpected loose data")); assertTrue(masked.contains("4.9.170")); assertTrue(masked.contains("reboot\nfallback"));
    }
    @Test public void embeddedCredentialPairsAreMaskedButCaptureTimesArePreserved() {
        String masked=ReportPrivacy.text("Status password=synthetic PIN_code:4567 at 20:21:21");
        assertFalse(masked.contains("synthetic")); assertFalse(masked.contains("4567")); assertTrue(masked.contains("20:21:21"));
    }
}
