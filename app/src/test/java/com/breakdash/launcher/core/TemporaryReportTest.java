package com.breakdash.launcher.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.Test;
import static org.junit.Assert.*;

public class TemporaryReportTest {
    private static final String DOWNLOAD="https://d.uguu.se/synthetic.bdr";
    private static final String SAMPLE="{\"version\":\"synthetic-ci\",\"note\":\"Synthetic upload test\",\"report\":\"Synthetic diagnostic data only\"}";
    private byte[] decrypt(byte[] file,String link) throws Exception {
        byte[] key=Base64.getUrlDecoder().decode(link.substring(link.indexOf('#')+"#breakdash-v1=".length()));
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,Arrays.copyOfRange(file,4,16)));
        cipher.updateAAD(Arrays.copyOfRange(file,0,4)); return cipher.doFinal(file,16,file.length-16);
    }
    @Test public void plaintextAndKeyAreSeparateFromTheUploadedFile() throws Exception {
        TemporaryReport.Sealed sealed=TemporaryReport.seal(SAMPLE.getBytes(StandardCharsets.UTF_8));
        String link=sealed.shareLink(DOWNLOAD);
        assertTrue(link.matches("https://d\\.uguu\\.se/synthetic\\.bdr#breakdash-v1=[A-Za-z0-9_-]{43}"));
        assertEquals(SAMPLE,new String(decrypt(sealed.encrypted,link),StandardCharsets.UTF_8));
        assertFalse(new String(sealed.encrypted,StandardCharsets.ISO_8859_1).contains("Synthetic diagnostic data only"));
        assertEquals(DOWNLOAD,link.substring(0,link.indexOf('#')));
    }
    @Test public void eachUploadUsesFreshKeyAndNonce() throws Exception {
        TemporaryReport.Sealed first=TemporaryReport.seal(SAMPLE.getBytes(StandardCharsets.UTF_8)),second=TemporaryReport.seal(SAMPLE.getBytes(StandardCharsets.UTF_8));
        assertFalse(first.shareLink(DOWNLOAD).equals(second.shareLink(DOWNLOAD)));
        assertFalse(Arrays.equals(Arrays.copyOfRange(first.encrypted,4,16),Arrays.copyOfRange(second.encrypted,4,16)));
    }
    @Test public void modifiedCiphertextAndWrongKeyCannotDecrypt() throws Exception {
        TemporaryReport.Sealed first=TemporaryReport.seal(SAMPLE.getBytes(StandardCharsets.UTF_8)),second=TemporaryReport.seal(SAMPLE.getBytes(StandardCharsets.UTF_8));
        assertThrows(javax.crypto.AEADBadTagException.class,()->decrypt(first.encrypted,second.shareLink(DOWNLOAD)));
        first.encrypted[first.encrypted.length-1]^=1;
        assertThrows(javax.crypto.AEADBadTagException.class,()->decrypt(first.encrypted,first.shareLink(DOWNLOAD)));
    }
    @Test public void serviceCannotReturnAnArbitraryOrKeyBearingDownloadUrl() throws Exception {
        for(String address:new String[]{"http://d.uguu.se/a.bdr","https://uguu.se.evil.test/a.bdr","https://evil.test/a.bdr","https://uguu.se@evil.test/a.bdr","https://d.uguu.se:444/a.bdr","https://d.uguu.se/a.bdr#secret","https://d.uguu.se/a.bdr?secret","https://d.uguu.se/a.html"}) {
            assertThrows(Exception.class,()->TemporaryReport.downloadUrl(address));
        }
        assertEquals(DOWNLOAD,TemporaryReport.downloadUrl(DOWNLOAD));
    }
    @Test public void capsTheReportBeforeEncryption() {
        assertThrows(Exception.class,()->TemporaryReport.seal(new byte[TemporaryReport.MAX_PLAINTEXT+1]));
        assertThrows(Exception.class,()->TemporaryReport.seal(new byte[0]));
    }
    @Test public void exportsSyntheticJavaCiphertextForTheNodeReader() throws Exception {
        String folder=System.getenv("BREAKDASH_TEMPORARY_FIXTURE_DIR"); assertNotNull("CI fixture directory must be configured",folder);
        TemporaryReport.Sealed sealed=TemporaryReport.seal(SAMPLE.getBytes(StandardCharsets.UTF_8));
        Path directory=Path.of(folder); Files.createDirectories(directory);
        Files.write(directory.resolve("synthetic.bdr"),sealed.encrypted);
        Files.writeString(directory.resolve("synthetic-link.txt"),sealed.shareLink(DOWNLOAD),StandardCharsets.UTF_8);
    }
}
