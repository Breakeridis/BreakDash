package com.breakdash.launcher.core;

import java.math.BigInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Versions {
    private static final Pattern VERSION = Pattern.compile("^v?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$");
    private Versions() {}
    public static int compare(String left, String right) {
        Matcher a = VERSION.matcher(left), b = VERSION.matcher(right);
        if (!a.matches() || !b.matches()) throw new IllegalArgumentException("Invalid semantic version");
        for (int i = 1; i <= 3; i++) {
            int difference = new BigInteger(a.group(i)).compareTo(new BigInteger(b.group(i)));
            if (difference != 0) return difference;
        }
        String ap = a.group(4), bp = b.group(4);
        if (ap == null || bp == null) return ap == bp ? 0 : ap == null ? 1 : -1;
        String[] aa = ap.split("\\."), bb = bp.split("\\.");
        for (int i = 0; i < Math.min(aa.length, bb.length); i++) {
            boolean an = aa[i].matches("[0-9]+"), bn = bb[i].matches("[0-9]+");
            int difference = an && bn ? new BigInteger(aa[i]).compareTo(new BigInteger(bb[i]))
                    : an != bn ? (an ? -1 : 1) : aa[i].compareTo(bb[i]);
            if (difference != 0) return difference;
        }
        return Integer.compare(aa.length, bb.length);
    }
}
