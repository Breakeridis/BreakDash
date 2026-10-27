package com.breakdash.launcher.core;

import java.io.IOException;
import java.net.URI;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** A self-contained ciphertext file; its one-time key travels only in the share-link fragment. */
public final class TemporaryReport {
    public static final int MAX_PLAINTEXT = 250000;
    private static final byte[] MAGIC = {'B','D','R','1'};
    private TemporaryReport() {}

    public static final class Sealed {
        public final byte[] encrypted;
        private final byte[] key;
        private Sealed(byte[] encrypted, byte[] key) { this.encrypted=encrypted; this.key=key; }
        public String shareLink(String download) throws Exception {
            return downloadUrl(download)+"#breakdash-v1="+Base64.getUrlEncoder().withoutPadding().encodeToString(key);
        }
    }

    public static Sealed seal(byte[] plaintext) throws Exception {
        if(plaintext.length==0 || plaintext.length>MAX_PLAINTEXT) throw new IOException("Report exceeds sharing limit");
        KeyGenerator generator=KeyGenerator.getInstance("AES"); generator.init(256); SecretKey key=generator.generateKey();
        byte[] nonce=new byte[12]; new SecureRandom().nextBytes(nonce);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,nonce)); cipher.updateAAD(MAGIC);
        byte[] ciphertext=cipher.doFinal(plaintext),file=new byte[16+ciphertext.length];
        System.arraycopy(MAGIC,0,file,0,4); System.arraycopy(nonce,0,file,4,12); System.arraycopy(ciphertext,0,file,16,ciphertext.length);
        return new Sealed(file,key.getEncoded());
    }

    public static String downloadUrl(String address) throws Exception {
        if(address==null || address.length()>2048) throw new IOException("Invalid report download link");
        URI uri=new URI(address); String host=uri.getHost(),path=uri.getRawPath();
        if(!"https".equalsIgnoreCase(uri.getScheme()) || host==null
                || !(host.equalsIgnoreCase("uguu.se") || host.toLowerCase(java.util.Locale.ROOT).endsWith(".uguu.se"))
                || uri.getRawUserInfo()!=null || uri.getPort()!=-1 || uri.getRawQuery()!=null || uri.getRawFragment()!=null
                || path==null || !path.matches("/[A-Za-z0-9_./-]+\\.bdr")) throw new IOException("Invalid report download link");
        return uri.toASCIIString();
    }
}
