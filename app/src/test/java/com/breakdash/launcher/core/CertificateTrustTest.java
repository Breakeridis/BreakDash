package com.breakdash.launcher.core;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class CertificateTrustTest {
    private static final String A="a".repeat(64),B="b".repeat(64),C="c".repeat(64);
    @Test public void acceptsExactSignerSetsRegardlessOfOrderOrHexCase() {
        assertTrue(CertificateTrust.matches(Arrays.asList(A,B),Arrays.asList(B.toUpperCase(),A)));
        assertTrue(CertificateTrust.matches(Collections.singletonList(A),Collections.singletonList(A)));
    }
    @Test public void rejectsChangedExtraAndMissingSigners() {
        assertFalse(CertificateTrust.matches(Arrays.asList(A,B),Arrays.asList(A,C)));
        assertFalse(CertificateTrust.matches(Collections.singletonList(A),Arrays.asList(A,B)));
        assertFalse(CertificateTrust.matches(Arrays.asList(A,B),Collections.singletonList(A)));
    }
    @Test public void missingCertificatesNeverBecomeTrusted() {
        assertFalse(CertificateTrust.matches(Collections.emptyList(),Collections.emptyList()));
        assertFalse(CertificateTrust.matches(null,Collections.singletonList(A)));
        assertFalse(CertificateTrust.matches(Collections.singletonList(A),null));
        assertFalse(CertificateTrust.matches(Arrays.asList(A,null),Collections.singletonList(A)));
        assertFalse(CertificateTrust.matches(Arrays.asList(A,A),Collections.singletonList(A)));
        assertFalse(CertificateTrust.matches(Collections.singletonList("invalid"),Collections.singletonList("invalid")));
    }
}
