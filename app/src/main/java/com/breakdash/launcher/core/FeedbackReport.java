package com.breakdash.launcher.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.KeyFactory;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

/** Fresh AES key per report, wrapped for a fixed recipient whose private key never enters the app. */
public final class FeedbackReport {
    public static final int MAX_FILE_BYTES=251100;
    private static final byte[] MAGIC={'B','D','N','1'};
    private FeedbackReport() {}

    public static String topicUrl(String topic) throws IOException {
        if(topic==null || !topic.matches("[A-Za-z0-9_-]{16,64}")) throw new IOException("Feedback inbox is not configured");
        return "https://ntfy.sh/"+topic;
    }
    public static byte[] seal(byte[] plaintext,String publicPem) throws Exception {
        if(plaintext.length==0 || plaintext.length>TemporaryReport.MAX_PLAINTEXT) throw new IOException("Report exceeds feedback limit");
        String encoded=publicPem.replace("-----BEGIN PUBLIC KEY-----","").replace("-----END PUBLIC KEY-----","").replaceAll("\\s","");
        RSAPublicKey recipient=(RSAPublicKey)KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(encoded)));
        if(recipient.getModulus().bitLength()<2048 || recipient.getModulus().bitLength()>8192) throw new IOException("Invalid feedback recipient key");
        KeyGenerator generator=KeyGenerator.getInstance("AES"); generator.init(256); SecretKey key=generator.generateKey();
        byte[] nonce=new byte[12]; new SecureRandom().nextBytes(nonce);
        Cipher aes=Cipher.getInstance("AES/GCM/NoPadding"); aes.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,nonce)); aes.updateAAD(MAGIC);
        byte[] encrypted=aes.doFinal(plaintext);
        Cipher rsa=Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        rsa.init(Cipher.ENCRYPT_MODE,recipient,new OAEPParameterSpec("SHA-256","MGF1",MGF1ParameterSpec.SHA256,PSource.PSpecified.DEFAULT));
        byte[] wrapped=rsa.doFinal(key.getEncoded());
        return ByteBuffer.allocate(6+wrapped.length+nonce.length+encrypted.length).put(MAGIC).putShort((short)wrapped.length).put(wrapped).put(nonce).put(encrypted).array();
    }
}
