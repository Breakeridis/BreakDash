package com.breakdash.launcher.core;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.MGF1ParameterSpec;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import org.junit.Test;
import static org.junit.Assert.*;

public class FeedbackReportTest {
    private static final String SAMPLE="{\"version\":\"synthetic-ci\",\"note\":\"ntfy synthetic feedback\",\"report\":\"Synthetic diagnostic data only\"}";
    private static KeyPair pair() throws Exception { KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA"); generator.initialize(3072); return generator.generateKeyPair(); }
    private static String pem(String kind,byte[] bytes) { return "-----BEGIN "+kind+"-----\n"+Base64.getEncoder().encodeToString(bytes)+"\n-----END "+kind+"-----\n"; }
    private byte[] decrypt(byte[] file,KeyPair recipient) throws Exception {
        ByteBuffer input=ByteBuffer.wrap(file); byte[] magic=new byte[4]; input.get(magic);
        byte[] wrapped=new byte[Short.toUnsignedInt(input.getShort())],iv=new byte[12]; input.get(wrapped); input.get(iv);
        Cipher rsa=Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        rsa.init(Cipher.DECRYPT_MODE,recipient.getPrivate(),new OAEPParameterSpec("SHA-256","MGF1",MGF1ParameterSpec.SHA256,PSource.PSpecified.DEFAULT));
        Cipher aes=Cipher.getInstance("AES/GCM/NoPadding"); aes.init(Cipher.DECRYPT_MODE,new SecretKeySpec(rsa.doFinal(wrapped),"AES"),new GCMParameterSpec(128,iv)); aes.updateAAD(magic);
        return aes.doFinal(file,input.position(),input.remaining());
    }
    @Test public void fixedRecipientCanDecryptWithoutSharingPerReportKeys() throws Exception {
        KeyPair recipient=pair(); byte[] file=FeedbackReport.seal(SAMPLE.getBytes(StandardCharsets.UTF_8),pem("PUBLIC KEY",recipient.getPublic().getEncoded()));
        assertEquals(SAMPLE,new String(decrypt(file,recipient),StandardCharsets.UTF_8));
        assertEquals("BDN1",new String(file,0,4,StandardCharsets.US_ASCII));
        assertFalse(new String(file,StandardCharsets.ISO_8859_1).contains("Synthetic diagnostic data only"));
        assertTrue(file.length<=FeedbackReport.MAX_FILE_BYTES);
    }
    @Test public void freshEncryptionForEachReportAndTamperingFails() throws Exception {
        KeyPair recipient=pair(); String publicPem=pem("PUBLIC KEY",recipient.getPublic().getEncoded());
        byte[] first=FeedbackReport.seal(SAMPLE.getBytes(StandardCharsets.UTF_8),publicPem),second=FeedbackReport.seal(SAMPLE.getBytes(StandardCharsets.UTF_8),publicPem);
        assertFalse(Arrays.equals(first,second)); first[first.length-1]^=1;
        assertThrows(javax.crypto.AEADBadTagException.class,()->decrypt(first,recipient));
        assertThrows(Exception.class,()->decrypt(second,pair()));
    }
    @Test public void restrictsTopicToOneNtfyPath() throws Exception {
        assertEquals("https://ntfy.sh/breakdash-test-123456",FeedbackReport.topicUrl("breakdash-test-123456"));
        for(String topic:new String[]{"short","a".repeat(65),"breakdash/other-topic","breakdash-topic?x=1","https://example.test","breakdash-topic\r\n"}) assertThrows(Exception.class,()->FeedbackReport.topicUrl(topic));
    }
    @Test public void rejectsOversizedPayloadsAndInvalidRecipientKeys() {
        assertThrows(Exception.class,()->FeedbackReport.seal(new byte[250001],"bad"));
        assertThrows(Exception.class,()->FeedbackReport.seal(new byte[0],"bad"));
        assertThrows(Exception.class,()->FeedbackReport.seal(SAMPLE.getBytes(StandardCharsets.UTF_8),"bad"));
    }
    @Test public void exportsOnlySyntheticRecipientMaterialForNodeTests() throws Exception {
        String folder=System.getenv("BREAKDASH_TEMPORARY_FIXTURE_DIR"); assertNotNull(folder);
        Path directory=Path.of(folder); Files.createDirectories(directory); KeyPair recipient=pair();
        String publicPem=pem("PUBLIC KEY",recipient.getPublic().getEncoded());
        Files.write(directory.resolve("synthetic-feedback.bdn"),FeedbackReport.seal(SAMPLE.getBytes(StandardCharsets.UTF_8),publicPem));
        Files.write(directory.resolve("synthetic-feedback-public.pem"),publicPem.getBytes(StandardCharsets.US_ASCII));
        Files.write(directory.resolve("synthetic-feedback-private.pem"),pem("PRIVATE KEY",recipient.getPrivate().getEncoded()).getBytes(StandardCharsets.US_ASCII));
    }
}
