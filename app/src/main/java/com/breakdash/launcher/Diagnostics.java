package com.breakdash.launcher;

import android.content.Context;
import android.database.Cursor;
import android.os.Build;
import android.provider.Settings;
import android.util.Base64;
import java.io.File;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Date;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import org.json.JSONObject;

/** Collected on demand; upload is invoked only by the explicit Upload report action. */
final class Diagnostics {
    private Diagnostics() {}
    private static boolean sensitive(String name) { return name.toLowerCase(java.util.Locale.US).matches(".*(serial|imei|imsi|password|secret|token|android_id|macaddr|bdaddr|address|ssid|hostname|account).*" ); }
    static String collect(Context context,String radio,boolean cloud) {
        StringBuilder report=new StringBuilder("BreakDash diagnostic report\n");
        report.append("Version: ").append(BuildConfig.VERSION_NAME).append("\nCaptured: ").append(new Date())
                .append("\nSDK (authoritative): ").append(Build.VERSION.SDK_INT).append("\nReported release: ").append(Build.VERSION.RELEASE)
                .append("\nBoard: ").append(Build.BOARD).append("\nHardware: ").append(Build.HARDWARE).append("\nModel: ").append(Build.MODEL)
                .append("\nABIs: ").append(String.join(", ",Build.SUPPORTED_ABIS)).append("\nBuild: ").append(Build.DISPLAY)
                .append("\nDisplay: ").append(context.getResources().getDisplayMetrics()).append("\n\nRADIO\n").append(radio)
                .append("\nKERNEL\n").append(file("/proc/version")).append("\nMEMORY (raw; vendor counters may be inflated)\n").append(file("/proc/meminfo"))
                .append("\nSYSTEM PROPERTIES\n");
        try {
            String properties=SystemTools.process(new String[]{"/system/bin/getprop"},4);
            for(String line:properties.split("\n")) {
                String key=line.contains("]") ? line.substring(0,line.indexOf(']')) : line;
                report.append(cloud && sensitive(key) ? key + "]: [redacted]" : line).append('\n');
            }
        } catch(Exception error) { report.append("Unavailable: ").append(error.getMessage()).append('\n'); }
        report.append("\nSETTINGS.SYSTEM\n");
        try(Cursor cursor=context.getContentResolver().query(Settings.System.CONTENT_URI,new String[]{"name","value"},null,null,"name ASC")) {
            if(cursor!=null) while(cursor.moveToNext() && report.length()<200000) {
                String key=cursor.getString(0),value=cursor.getString(1);
                report.append(key).append(" = ").append(cloud && sensitive(key) ? "[redacted]" : value).append('\n');
            }
        } catch(Exception error) { report.append("Unavailable: ").append(error.getMessage()); }
        return report.toString();
    }
    private static String file(String path) {
        try (java.io.InputStream input = new java.io.FileInputStream(path)) { return Http.read(input, 65536); }
        catch(Exception error) { return "Unavailable: " + error.getMessage() + "\n"; }
    }
    static String upload(Prefs prefs,String report,String note) throws Exception {
        String endpoint=prefs.get("diagnostics_url",BuildConfig.DIAGNOSTICS_URL),pem=prefs.get("diagnostics_key",BuildConfig.DIAGNOSTICS_PUBLIC_KEY);
        if(endpoint.isEmpty() || pem.isEmpty()) throw new IllegalStateException("Configure a diagnostics endpoint and public key in Connections");
        String encoded=pem.replace("-----BEGIN PUBLIC KEY-----","").replace("-----END PUBLIC KEY-----","").replaceAll("\\s","");
        PublicKey publicKey=KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.decode(encoded,Base64.DEFAULT)));
        KeyGenerator generator=KeyGenerator.getInstance("AES"); generator.init(256); SecretKey key=generator.generateKey();
        byte[] nonce=new byte[12]; new SecureRandom().nextBytes(nonce);
        Cipher aes=Cipher.getInstance("AES/GCM/NoPadding"); aes.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,nonce));
        JSONObject payload=new JSONObject().put("version",BuildConfig.VERSION_NAME).put("note",note).put("report",report);
        byte[] plaintext=payload.toString().getBytes(StandardCharsets.UTF_8);
        if(plaintext.length>250000) throw new java.io.IOException("Report exceeds upload limit. Copy it from the inspector for manual review.");
        byte[] ciphertext=aes.doFinal(plaintext);
        Cipher rsa=Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        rsa.init(Cipher.ENCRYPT_MODE,publicKey,new OAEPParameterSpec("SHA-256","MGF1",MGF1ParameterSpec.SHA256,PSource.PSpecified.DEFAULT));
        JSONObject envelope=new JSONObject().put("v",1).put("key",Base64.encodeToString(rsa.doFinal(key.getEncoded()),Base64.NO_WRAP))
                .put("iv",Base64.encodeToString(nonce,Base64.NO_WRAP)).put("data",Base64.encodeToString(ciphertext,Base64.NO_WRAP));
        HttpURLConnection connection=Http.open(endpoint,"POST");
        connection.setDoOutput(true); connection.setRequestProperty("Content-Type","application/json");
        try {
            byte[] body=envelope.toString().getBytes(StandardCharsets.UTF_8); connection.setFixedLengthStreamingMode(body.length);
            try(java.io.OutputStream output=connection.getOutputStream()) { output.write(body); }
            int status=connection.getResponseCode();
            if(status<200 || status>=300) throw new java.io.IOException("Diagnostics endpoint returned HTTP " + status);
            try(java.io.InputStream input=connection.getInputStream()) { return new JSONObject(Http.read(input,16384)).optString("message","Report uploaded"); }
        } finally { connection.disconnect(); }
    }
}
