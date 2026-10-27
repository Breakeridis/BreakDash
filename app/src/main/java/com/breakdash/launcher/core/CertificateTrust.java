package com.breakdash.launcher.core;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Exact, nonempty signer sets. Certificate ordering is not a trust boundary. */
public final class CertificateTrust {
    private CertificateTrust() {}
    public static boolean matches(List<String> installed,List<String> incoming) {
        Set<String> trusted=validated(installed),candidate=validated(incoming);
        return trusted!=null && candidate!=null && trusted.equals(candidate);
    }
    private static Set<String> validated(List<String> values) {
        if(values==null || values.isEmpty()) return null;
        Set<String> result=new HashSet<>();
        for(String value:values) {
            if(value==null || !value.matches("[a-fA-F0-9]{64}") || !result.add(value.toLowerCase(Locale.ROOT))) return null;
        }
        return result;
    }
}
