package com.breakdash.launcher.core;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class FactoryDiscoveryContractTest {
    public interface Feature { boolean startSearchDevice_BC(); boolean stopSearchDevice_BC(); }
    public static final class Definitions {
        public static final String ACTION_DISCOVER_FOUND="com.bt.synthetic.FOUND";
        public static final String ACTION_SEARCH_FINISHED="com.nwd.synthetic.FINISHED";
        public static final String ACTION_CONTACT_SYNC="com.bt.synthetic.CONTACTS";
        public static final String ACTION_FOUND_INVALID="SYNTHETIC-PRIVATE-IDENTITY";
        public static String ACTION_SEARCH_MUTABLE="com.bt.synthetic.MUTABLE";
        public static final String PIN_CODE="SYNTHETIC-PRIVATE-PIN";
    }
    public static class DeviceModel {
        private final String address,name;
        public DeviceModel(String address,String name) { this.address=address; this.name=name; }
        public String getName() { return name; }
        public String getAddress() { return address; }
        public String getObdPassword() { throw new AssertionError("No private getter"); }
        @Override public String toString() { throw new AssertionError("No object dump"); }
    }
    @Test public void onlyImmutableDiscoveryProtocolConstantsBecomeReceiverActions() throws Exception {
        FactoryDiscoveryContract contract=new FactoryDiscoveryContract(Feature.class,Definitions.class,DeviceModel.class);
        assertEquals(2,contract.actions.size()); assertTrue(contract.actions.contains("com.bt.synthetic.FOUND"));
        assertTrue(contract.actions.contains("com.nwd.synthetic.FINISHED"));
        assertFalse(contract.schema.contains("SYNTHETIC-PRIVATE")); assertFalse(contract.schema.contains("CONTACTS")); assertFalse(contract.schema.contains("MUTABLE"));
    }
    @Test public void actualSdkModelsDecodeLocallyWithoutStringPayloadGuessing() throws Exception {
        FactoryDiscoveryContract contract=new FactoryDiscoveryContract(Feature.class,Definitions.class,DeviceModel.class);
        DeviceModel model=new DeviceModel("12:34:56:78:90:ab","Synthetic\nscanner");
        List<FactoryDiscoveryContract.Device> found=contract.devices(new Object[]{model,"12:34:56:78:90:AB",new DeviceModel("bad","Secret")});
        assertEquals(1,found.size()); assertEquals("12:34:56:78:90:AB",found.get(0).address); assertEquals("Synthetic scanner",found.get(0).name);
        assertFalse(contract.schema.contains(found.get(0).name)); assertFalse(contract.schema.contains(found.get(0).address));
    }
    @Test public void recursiveAndLargePayloadsStayBounded() throws Exception {
        FactoryDiscoveryContract contract=new FactoryDiscoveryContract(Feature.class,Definitions.class,DeviceModel.class);
        List<Object> values=new ArrayList<>(); values.add(values);
        for(int i=0;i<200;i++) values.add(new DeviceModel(String.format("12:34:56:78:90:%02X",i),"Synthetic"));
        assertEquals(32,contract.devices(values).size());
        assertTrue(contract.devices(List.of("address",123,true)).isEmpty());
    }
    public static final class WrongFeature { public String startSearchDevice_BC() { throw new AssertionError(); } public boolean stopSearchDevice_BC() { throw new AssertionError(); } }
    @Test(expected=NoSuchMethodException.class) public void unsupportedControlSignatureFailsBeforeAnyScan() throws Exception {
        new FactoryDiscoveryContract(WrongFeature.class,Definitions.class,DeviceModel.class);
    }
}
