package com.breakdash.launcher;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import com.breakdash.launcher.core.ReportPrivacy;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** One-shot descriptor enumeration. Never opens devices, requests USB grants, reads serials, or accesses user files. */
final class UsbDiagnostics {
    private static final int MAX_TEXT=16000;
    private UsbDiagnostics() {}
    static String collect(Context context,boolean cloud) {
        StringBuilder out=new StringBuilder("USB HOST & REMOVABLE STORAGE\n");
        out.append("USB host feature: ").append(context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST)).append('\n');
        UsbManager manager=(UsbManager)context.getSystemService(Context.USB_SERVICE);
        try {
            List<UsbDevice> devices=manager==null ? new ArrayList<>() : new ArrayList<>(manager.getDeviceList().values());
            devices.sort(Comparator.comparingInt(UsbDevice::getVendorId).thenComparingInt(UsbDevice::getProductId).thenComparingInt(UsbDevice::getDeviceId));
            out.append("Android-visible USB devices: ").append(devices.size()).append('\n'); int shown=0;
            for(UsbDevice device:devices) {
                if(shown++>=16 || out.length()>MAX_TEXT) { out.append("Additional devices omitted\n"); break; }
                out.append(String.format(Locale.ROOT,"Device %d · VID:PID %04X:%04X · class %d/%d/%d\n",shown,device.getVendorId(),device.getProductId(),device.getDeviceClass(),device.getDeviceSubclass(),device.getDeviceProtocol()));
                try { out.append("  Manufacturer: ").append(clean(device.getManufacturerName(),cloud)).append(" · Product: ").append(clean(device.getProductName(),cloud)).append(" · Revision: ").append(clean(device.getVersion(),cloud)).append('\n'); }
                catch(SecurityException error) { out.append("  Product descriptors unavailable without USB access\n"); }
                out.append("  Device access already granted: ").append(manager.hasPermission(device)).append(" · Interfaces: ").append(device.getInterfaceCount()).append('\n');
                for(int i=0;i<Math.min(16,device.getInterfaceCount()) && out.length()<MAX_TEXT;i++) {
                    UsbInterface iface=device.getInterface(i);
                    out.append(String.format(Locale.ROOT,"  Interface %d alt %d · class %d/%d/%d · endpoints %d\n",iface.getId(),iface.getAlternateSetting(),iface.getInterfaceClass(),iface.getInterfaceSubclass(),iface.getInterfaceProtocol(),iface.getEndpointCount()));
                    for(int j=0;j<Math.min(16,iface.getEndpointCount()) && out.length()<MAX_TEXT;j++) {
                        UsbEndpoint endpoint=iface.getEndpoint(j);
                        out.append(String.format(Locale.ROOT,"    Endpoint 0x%02X · direction %s · type %d · packet %d · interval %d\n",endpoint.getAddress(),endpoint.getDirection()==128 ? "IN" : "OUT",endpoint.getType(),endpoint.getMaxPacketSize(),endpoint.getInterval()));
                    }
                }
            }
            if(devices.isEmpty()) out.append("No host devices exposed to this app; disconnected, restricted, or handled privately by vendor firmware.\n");
        } catch(Exception error) { out.append("Android USB enumeration unavailable: ").append(clean(error.getMessage(),cloud)).append('\n'); }
        try {
            UsbAccessory[] accessories=manager==null ? null : manager.getAccessoryList();
            out.append("USB accessory-mode devices: ").append(accessories==null ? 0 : accessories.length).append('\n');
            if(accessories!=null) for(int i=0;i<Math.min(accessories.length,8);i++) {
                UsbAccessory accessory=accessories[i];
                out.append("  Manufacturer: ").append(clean(accessory.getManufacturer(),cloud)).append(" · Model: ").append(clean(accessory.getModel(),cloud)).append(" · Revision: ").append(clean(accessory.getVersion(),cloud)).append('\n');
            }
        } catch(Exception error) { out.append("Accessory descriptors unavailable\n"); }
        try {
            StorageManager storage=(StorageManager)context.getSystemService(Context.STORAGE_SERVICE); int count=0;
            if(storage!=null) for(StorageVolume volume:storage.getStorageVolumes()) if(volume.isRemovable()) {
                out.append("Removable volume ").append(++count).append(" · state ").append(volume.getState()).append(" · primary ").append(volume.isPrimary()).append(" · emulated ").append(volume.isEmulated());
                if(!cloud) out.append(" · label ").append(volume.getDescription(context));
                out.append('\n');
            }
            if(count==0) out.append("No removable storage volumes exposed to Android\n");
            out.append("Removable volumes may be USB or SD; Android 10 does not reliably associate them with host descriptors. Volume labels/UUIDs and USB serials are omitted from uploads.\n");
        } catch(Exception error) { out.append("Removable storage status unavailable\n"); }
        // Some head-unit firmware hides host devices from UsbManager; read only non-unique kernel descriptors when accessible.
        int kernelCount=0;
        try {
          File[] entries=new File("/sys/bus/usb/devices").listFiles();
          if(entries!=null) for(File entry:entries) {
            if(kernelCount>=16 || out.length()>MAX_TEXT) break;
            if(!entry.getName().matches("(?:usb[0-9]+|[0-9]+-[0-9]+(?:\\.[0-9]+)*)")) continue;
            String vendor=descriptor(entry,"idVendor"),product=descriptor(entry,"idProduct");
            if(vendor.isEmpty() || product.isEmpty()) continue;
            kernelCount++; out.append("Kernel USB ").append(entry.getName()).append(" · VID:PID ").append(vendor).append(':').append(product)
                    .append(" · class ").append(descriptor(entry,"bDeviceClass")).append(" · manufacturer ").append(clean(descriptor(entry,"manufacturer"),cloud))
                    .append(" · product ").append(clean(descriptor(entry,"product"),cloud)).append('\n');
          }
        } catch(SecurityException error) { /* Descriptor fallback is optional on restricted firmware. */ }
        if(kernelCount==0) out.append("Kernel USB descriptors unavailable or empty (firmware permissions may restrict them)\n");
        return out.toString();
    }
    private static String clean(String text,boolean cloud) { return text==null ? "Unknown" : cloud ? ReportPrivacy.text(text) : text; }
    private static String descriptor(File directory,String name) {
        try(FileInputStream input=new FileInputStream(new File(directory,name))) { return Http.read(input,512).trim(); }
        catch(Exception error) { return ""; }
    }
}
