package com.breakdash.launcher.core;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class FactoryDiscoveryEventsTest {
    public static final class Definitions {
        public static final String ACTION_BT_DEVICE_FOUND="com.bt.synthetic.DEVICE_FOUND";
        public static final String ACTION_BT_DISCOVER_START="com.bt.synthetic.START";
        public static final String ACTION_BT_DISCOVER_FINISH="com.bt.synthetic.FINISH";
        public static final String ACTION_BT_SEARCH_DEVICE_SIGNAL="com.bt.synthetic.SIGNAL";
        public static final String KEY_DEVICE_ADDRESS="sdk.addr";
        public static final String KEY_DEVICE_NAME="sdk.name";
    }
    private static FactoryDiscoveryContract contract(Class<?> definitions) throws Exception {
        return new FactoryDiscoveryContract(FactoryDiscoveryContractTest.Feature.class,definitions,FactoryDiscoveryContractTest.DeviceModel.class);
    }
    private static FactoryDiscoveryEvents.Payload payload(Map<String,Object> values) {
        return new FactoryDiscoveryEvents.Payload() {
            public Object get(String key) { return values.get(key); }
            public Iterable<?> values() { return values.values(); }
        };
    }
    @Test public void controlNotificationsAreCountedWithoutTouchingPayloadsOrProducingPartialFailure() throws Exception {
        FactoryDiscoveryEvents events=new FactoryDiscoveryEvents(contract(Definitions.class));
        for(String action:List.of(Definitions.ACTION_BT_DISCOVER_START,Definitions.ACTION_BT_SEARCH_DEVICE_SIGNAL,Definitions.ACTION_BT_DISCOVER_FINISH)) {
            events.observe(action,()->{ throw new AssertionError("Control payload must not be read"); });
        }
        assertFalse(events.partial()); assertTrue(events.devices().isEmpty());
        assertTrue(events.evidence().contains("Discovery broadcasts: 3")); assertTrue(events.evidence().contains("Device-found events: 0"));
        assertTrue(events.evidence().contains("ACTION_BT_SEARCH_DEVICE_SIGNAL: 1"));
    }
    @Test public void primitiveDeviceIsDecodedAndNamedUpdatesDeduplicateAtCapacity() throws Exception {
        FactoryDiscoveryEvents events=new FactoryDiscoveryEvents(contract(Definitions.class));
        for(int i=0;i<40;i++) {
            String address=String.format("12:34:56:78:90:%02X",i);
            events.observe(Definitions.ACTION_BT_DEVICE_FOUND,()->payload(Map.of(Definitions.KEY_DEVICE_ADDRESS,address)));
        }
        assertEquals(32,events.devices().size());
        events.observe(Definitions.ACTION_BT_DEVICE_FOUND,()->payload(Map.of(Definitions.KEY_DEVICE_ADDRESS,"12:34:56:78:90:00",Definitions.KEY_DEVICE_NAME,"SYNTHETIC-PRIVATE-SCANNER")));
        assertEquals(32,events.devices().size()); assertEquals("SYNTHETIC-PRIVATE-SCANNER",events.devices().get(0).name);
        assertFalse(events.partial()); assertFalse(events.evidence().contains("SYNTHETIC-PRIVATE"));
        assertFalse(events.evidence().contains("12:34")); assertFalse(events.evidence().contains("sdk.addr"));
    }
    @Test public void unsupportedFoundAndMalformedPayloadAreDistinctFromEmptyDiscovery() throws Exception {
        FactoryDiscoveryEvents events=new FactoryDiscoveryEvents(contract(Definitions.class));
        events.observe(Definitions.ACTION_BT_DEVICE_FOUND,()->payload(Map.of("unknown","12:34:56:78:90:AB")));
        events.observe(Definitions.ACTION_BT_DEVICE_FOUND,()->{ throw new IllegalArgumentException("SYNTHETIC-PRIVATE"); });
        assertTrue(events.partial()); assertTrue(events.devices().isEmpty());
        assertTrue(events.evidence().contains("Undecodable device-found events: 1")); assertTrue(events.evidence().contains("Unreadable device-found events: 1"));
        assertFalse(events.evidence().contains("SYNTHETIC-PRIVATE")); assertFalse(events.evidence().contains("12:34"));
    }
    @Test public void sdkModelsRemainAuthoritativeAndUnknownActionsAreIgnored() throws Exception {
        FactoryDiscoveryEvents events=new FactoryDiscoveryEvents(contract(Definitions.class));
        FactoryDiscoveryContractTest.DeviceModel model=new FactoryDiscoveryContractTest.DeviceModel("12:34:56:78:90:AB","Synthetic device");
        events.observe(Definitions.ACTION_BT_DEVICE_FOUND,()->payload(Map.of("model",model)));
        events.observe("SYNTHETIC-PRIVATE-ACTION",()->{ throw new AssertionError(); });
        assertEquals(1,events.devices().size()); assertFalse(events.partial()); assertTrue(events.evidence().contains("SDK_MODELS: 1"));
        assertFalse(events.evidence().contains("Synthetic device")); assertFalse(events.evidence().contains("SYNTHETIC-PRIVATE-ACTION"));
    }
    public static final class AmbiguousDefinitions {
        public static final String ACTION_BT_DEVICE_FOUND="com.bt.synthetic.COLLISION";
        public static final String ACTION_SEARCH_START="com.bt.synthetic.COLLISION";
        public static final String KEY_DEVICE_ADDRESS="sdk.addr";
    }
    @Test public void collidingControlAndFoundActionNeverDecodeADevice() throws Exception {
        FactoryDiscoveryEvents events=new FactoryDiscoveryEvents(contract(AmbiguousDefinitions.class));
        events.observe(AmbiguousDefinitions.ACTION_BT_DEVICE_FOUND,()->{ throw new AssertionError(); });
        assertTrue(events.devices().isEmpty()); assertFalse(events.partial());
    }
}
