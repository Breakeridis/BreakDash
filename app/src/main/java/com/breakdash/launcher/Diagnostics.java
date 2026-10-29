package com.breakdash.launcher;

import android.content.Context;
import android.database.Cursor;
import android.os.Build;
import android.provider.Settings;
import android.util.Base64;
import java.io.IOException;
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
import com.breakdash.launcher.core.TemporaryReport;
import com.breakdash.launcher.core.FeedbackReport;
import com.breakdash.launcher.core.ReportPrivacy;

/** Collected on demand; upload is invoked only by the explicit Upload report action. */
final class Diagnostics {
    private Diagnostics() {}
    static String collect(Context context,String radio,boolean cloud) {
        StringBuilder report=new StringBuilder("BreakDash diagnostic report\n");
        report.append("Version: ").append(BuildConfig.VERSION_NAME).append("\nCaptured: ").append(new Date())
                .append("\nSDK (authoritative): ").append(Build.VERSION.SDK_INT).append("\nReported release: ").append(Build.VERSION.RELEASE)
                .append("\nBoard: ").append(Build.BOARD).append("\nHardware: ").append(Build.HARDWARE).append("\nModel: ").append(Build.MODEL)
                .append("\nABIs: ").append(String.join(", ",Build.SUPPORTED_ABIS)).append("\nBuild: ").append(Build.DISPLAY)
                .append("\nDisplay: ").append(context.getResources().getDisplayMetrics()).append("\n\nRADIO\n").append(cloud ? ReportPrivacy.text(radio) : radio)
                .append("\nKERNEL\n").append(file("/proc/version")).append("\nMEMORY (raw; vendor counters may be inflated)\n").append(file("/proc/meminfo"))
                .append('\n').append(UsbDiagnostics.collect(context,cloud)).append('\n')
                .append(BluetoothDiagnostics.collect(context)).append('\n');
        if(context instanceof MainActivity) report.append(((MainActivity)context).navigationDiagnostics()).append('\n');
        report.append(DiagnosticHistory.collect()).append("\nINSTALLED APP SIGNING\n");
        try { report.append(ApkSignatures.read(context.getPackageManager(),context.getPackageName(),null).describe()).append('\n'); }
        catch(Exception error) { report.append("Installed signature inspection unavailable\n"); }
        report.append("SYSTEM PROPERTIES\n");
        try {
            String properties=SystemTools.process(new String[]{"/system/bin/getprop"},4);
            report.append(cloud ? ReportPrivacy.properties(properties) : properties).append('\n');
        } catch(Exception error) { report.append("Unavailable: ").append(error.getMessage()).append('\n'); }
        report.append("\nSETTINGS.SYSTEM\n");
        try(Cursor cursor=context.getContentResolver().query(Settings.System.CONTENT_URI,new String[]{"name","value"},null,null,"name ASC")) {
            if(cursor!=null) while(cursor.moveToNext() && report.length()<200000) {
                String key=cursor.getString(0),value=cursor.getString(1);
                report.append(key).append(" = ").append(cloud ? ReportPrivacy.value(key,value) : value).append('\n');
            }
        } catch(Exception error) { report.append("Unavailable: ").append(error.getMessage()); }
        return cloud ? ReportPrivacy.text(report.toString()) : report.toString();
    }
    private static String file(String path) {
        try (java.io.InputStream input = new java.io.FileInputStream(path)) { return Http.read(input, 65536); }
        catch(Exception error) { return "Unavailable: " + error.getMessage() + "\n"; }
    }
    private static byte[] payload(String report,String note) throws Exception {
        byte[] bytes=new JSONObject().put("version",BuildConfig.VERSION_NAME).put("note",note).put("report",report).toString().getBytes(StandardCharsets.UTF_8);
        if(bytes.length>TemporaryReport.MAX_PLAINTEXT) throw new IOException("Report exceeds upload limit. Copy it from the inspector for manual review.");
        return bytes;
    }
    static String uploadTemporary(String report,String note) throws Exception {
        Http.checkInterrupted(); TemporaryReport.Sealed sealed=TemporaryReport.seal(payload(report,note));
        String boundary="BreakDash-"+java.util.UUID.randomUUID();
        byte[] prefix=("--"+boundary+"\r\nContent-Disposition: form-data; name=\"files[]\"; filename=\"BreakDash-report.bdr\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
        byte[] suffix=("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.US_ASCII);
        HttpURLConnection connection=Http.open("https://uguu.se/upload","POST");
        connection.setDoOutput(true); connection.setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary);
        connection.setFixedLengthStreamingMode(prefix.length+sealed.encrypted.length+suffix.length);
        try {
            try(java.io.OutputStream output=connection.getOutputStream()) {
                Http.checkInterrupted(); output.write(prefix);
                for(int offset=0;offset<sealed.encrypted.length;offset+=8192) {
                    Http.checkInterrupted(); output.write(sealed.encrypted,offset,Math.min(8192,sealed.encrypted.length-offset));
                }
                Http.checkInterrupted(); output.write(suffix);
            }
            int code=connection.getResponseCode();
            if(code<200 || code>=300) throw new IOException("Temporary upload unavailable (HTTP "+code+"). Try again later.");
            JSONObject response;
            try(java.io.InputStream input=connection.getInputStream()) { response=new JSONObject(Http.read(input,16384)); }
            if(!response.optBoolean("success") || response.optJSONArray("files")==null || response.getJSONArray("files").length()!=1) throw new IOException("Uguu could not accept the report. Try again later.");
            Http.checkInterrupted(); return sealed.shareLink(response.getJSONArray("files").getJSONObject(0).getString("url"));
        } finally { connection.disconnect(); }
    }
    static boolean feedbackConfigured() { return !BuildConfig.NTFY_TOPIC.isEmpty() && !BuildConfig.FEEDBACK_PUBLIC_KEY.isEmpty(); }
    static String uploadFeedback(String report,String note) throws Exception {
        Http.checkInterrupted(); String address=FeedbackReport.topicUrl(BuildConfig.NTFY_TOPIC);
        byte[] file=FeedbackReport.seal(payload(report,note),BuildConfig.FEEDBACK_PUBLIC_KEY);
        HttpURLConnection connection=Http.open(address,"PUT");
        connection.setDoOutput(true); connection.setRequestProperty("Content-Type","application/octet-stream");
        connection.setRequestProperty("X-Filename","BreakDash-feedback.bdn");
        connection.setRequestProperty("X-Title","BreakDash encrypted feedback");
        connection.setRequestProperty("X-Message","Encrypted diagnostic report");
        connection.setRequestProperty("X-Firebase","no"); connection.setFixedLengthStreamingMode(file.length);
        try {
            try(java.io.OutputStream output=connection.getOutputStream()) {
                for(int offset=0;offset<file.length;offset+=8192) {
                    Http.checkInterrupted(); output.write(file,offset,Math.min(8192,file.length-offset));
                }
            }
            int code=connection.getResponseCode();
            if(code<200 || code>=300) throw new IOException("Feedback unavailable (HTTP "+code+"). Try again later.");
            JSONObject response; try(java.io.InputStream input=connection.getInputStream()) { response=new JSONObject(Http.read(input,16384)); }
            if(!"message".equals(response.optString("event")) || !BuildConfig.NTFY_TOPIC.equals(response.optString("topic")) || response.optJSONObject("attachment")==null) throw new IOException("ntfy could not store the feedback attachment");
            JSONObject attachment=response.getJSONObject("attachment");
            if(!"BreakDash-feedback.bdn".equals(attachment.optString("name")) || attachment.optLong("size",-1)!=file.length) throw new IOException("Feedback attachment was not accepted");
            return "Feedback sent · ask to check it within 3 hours. No link needed.";
        } finally { connection.disconnect(); }
    }
    static String upload(Prefs prefs,String report,String note) throws Exception {
        String endpoint=prefs.get("diagnostics_url",BuildConfig.DIAGNOSTICS_URL),pem=prefs.get("diagnostics_key",BuildConfig.DIAGNOSTICS_PUBLIC_KEY);
        if(endpoint.isEmpty() || pem.isEmpty()) throw new IllegalStateException("Configure your server and public key in Report setup");
        String encoded=pem.replace("-----BEGIN PUBLIC KEY-----","").replace("-----END PUBLIC KEY-----","").replaceAll("\\s","");
        PublicKey publicKey=KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.decode(encoded,Base64.DEFAULT)));
        KeyGenerator generator=KeyGenerator.getInstance("AES"); generator.init(256); SecretKey key=generator.generateKey();
        byte[] nonce=new byte[12]; new SecureRandom().nextBytes(nonce);
        Cipher aes=Cipher.getInstance("AES/GCM/NoPadding"); aes.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,nonce));
        byte[] plaintext=payload(report,note);
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
