package com.breakdash.launcher.core;

import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class FactoryDiscoveryFieldsTest {
    public static final class Definitions {
        public static final String KEY_DEVICE_ADDRESS="sdk.opaque.address";
        public static final String EXTRA_BT_ADDR="sdk.other.address";
        public static final String KEY_DEVICE_NAME="sdk.opaque.name";
        public static final String EXTRA_BT_NAME="sdk.other.name";
        public static final String KEY_LOCAL_DEVICE_ADDRESS="SYNTHETIC-PRIVATE-LOCAL";
        public static final String KEY_CONTACT_NAME="SYNTHETIC-PRIVATE-CONTACT";
        public static final String KEY_PIN_CODE="SYNTHETIC-PRIVATE-PIN";
        public static String KEY_REMOTE_DEVICE_ADDR="sdk.mutable.address";
        private static final String KEY_BD_ADDR="sdk.private.address";
    }
    @Test public void mapsOnlySdkDefinedKeysAndKeepsAllValuesOutOfSchema() {
        FactoryDiscoveryFields fields=new FactoryDiscoveryFields(Definitions.class);
        Map<String,Object> values=new HashMap<>();
        values.put(Definitions.KEY_DEVICE_ADDRESS,"1234567890ab"); values.put(Definitions.KEY_DEVICE_NAME,"Synthetic\nscanner");
        FactoryDiscoveryFields.Reading reading=fields.read(key->{
            assertFalse(key.startsWith("SYNTHETIC-PRIVATE")); assertFalse(key.contains("mutable")); assertFalse(key.contains("private"));
            return values.get(key);
        });
        assertEquals(FactoryDiscoveryFields.Outcome.DEVICE,reading.outcome);
        assertEquals("12:34:56:78:90:AB",reading.device.address); assertEquals("Synthetic scanner",reading.device.name);
        assertTrue(fields.schema.contains("KEY_DEVICE_ADDRESS : address"));
        assertFalse(fields.schema.contains("sdk.opaque")); assertFalse(fields.schema.contains("SYNTHETIC-PRIVATE"));
        assertFalse(fields.schema.contains(reading.device.address)); assertFalse(fields.schema.contains(reading.device.name));
    }
    @Test public void addressLikeNamesAndUnknownKeysNeverBecomeAddresses() {
        FactoryDiscoveryFields fields=new FactoryDiscoveryFields(Definitions.class);
        Map<String,Object> values=Map.of("address","12:34:56:78:90:AB",Definitions.KEY_DEVICE_NAME,"1234567890AB");
        FactoryDiscoveryFields.Reading reading=fields.read(values::get);
        assertEquals(FactoryDiscoveryFields.Outcome.NO_ADDRESS,reading.outcome); assertNull(reading.device);
    }
    @Test public void ambiguousAliasesAreRejectedWithoutMixingDevices() {
        FactoryDiscoveryFields fields=new FactoryDiscoveryFields(Definitions.class);
        Map<String,Object> values=new HashMap<>();
        values.put(Definitions.KEY_DEVICE_ADDRESS,"12:34:56:78:90:AB"); values.put(Definitions.EXTRA_BT_ADDR,"12:34:56:78:90:AC");
        assertEquals(FactoryDiscoveryFields.Outcome.AMBIGUOUS,fields.read(values::get).outcome);
        values.put(Definitions.EXTRA_BT_ADDR,"12-34-56-78-90-ab");
        values.put(Definitions.KEY_DEVICE_NAME,"One"); values.put(Definitions.EXTRA_BT_NAME,"Two");
        assertEquals(FactoryDiscoveryFields.Outcome.AMBIGUOUS,fields.read(values::get).outcome);
        values.put(Definitions.EXTRA_BT_NAME,"One");
        assertEquals(FactoryDiscoveryFields.Outcome.DEVICE,fields.read(values::get).outcome);
    }
    @Test public void invalidAndWrongTypedAddressesFailClosed() {
        FactoryDiscoveryFields fields=new FactoryDiscoveryFields(Definitions.class);
        for(Object value:new Object[]{"000000000000","FF:FF:FF:FF:FF:FF","12:34-56:78:90:AB","not-an-address",7}) {
            FactoryDiscoveryFields.Reading reading=fields.read(key->key.equals(Definitions.KEY_DEVICE_ADDRESS) ? value : null);
            assertNull(reading.device);
            assertEquals(value instanceof String ? FactoryDiscoveryFields.Outcome.INVALID_ADDRESS : FactoryDiscoveryFields.Outcome.WRONG_TYPE,reading.outcome);
        }
        assertEquals(FactoryDiscoveryFields.Outcome.UNREADABLE,fields.read(key->{ throw new IllegalArgumentException("SYNTHETIC-PRIVATE"); }).outcome);
    }
    @Test public void unnamedDeviceAndUnavailableMappingsAreExplicit() {
        FactoryDiscoveryFields fields=new FactoryDiscoveryFields(Definitions.class);
        FactoryDiscoveryFields.Reading reading=fields.read(key->key.equals(Definitions.KEY_DEVICE_ADDRESS) ? "12:34:56:78:90:AB" : null);
        assertEquals(FactoryDiscoveryFields.Outcome.ADDRESS_ONLY,reading.outcome); assertEquals(FactoryDiscoveryContract.UNNAMED,reading.device.name);
        FactoryDiscoveryFields absent=new FactoryDiscoveryFields(Object.class);
        assertEquals(FactoryDiscoveryFields.Outcome.UNMAPPED,absent.read(key->{ throw new AssertionError(); }).outcome);
    }
}
