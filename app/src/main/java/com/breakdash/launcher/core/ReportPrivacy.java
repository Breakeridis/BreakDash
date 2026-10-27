package com.breakdash.launcher.core;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Upload-only redaction for vendor keys and free-text values; raw local inspection remains available. */
public final class ReportPrivacy {
    private static final String MASK="[redacted]";
    private static final Pattern NORMALIZE=Pattern.compile("[^a-z0-9]");
    private static final Pattern SENSITIVE=Pattern.compile("serial|imei|imsi|iccid|androidid|deviceid|advertisingid|oaid|uuid|guid|password|passwd|passphrase|secret|token|credential|apikey|accesskey|authkey|providerkey|account|email|username|ssid|bssid|mac|bdaddr|localaddr|address|hostname|pincode|pairingcode|regcode|registrationcode|activation|license|licence|mfiid|emmc.*cid|productindent|location|latitude|longitude|coordinate|lastfix|gpsposition|sysnwdnet|netdns|btphonename|bluetoothname|btname");
    private static final Pattern PROPERTY=Pattern.compile("(?m)^\\[([^\\]\\r\\n]+)\\]: \\[");
    private static final Pattern MAC=Pattern.compile("(?i)(?<![a-f0-9])(?:[a-f0-9]{2}[:-]){5}[a-f0-9]{2}(?![a-f0-9])|(?<![a-f0-9])[a-f0-9]{12}(?![a-f0-9])");
    private static final String OCTET="(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)";
    private static final Pattern IPV4=Pattern.compile("(?<![\\w.])"+OCTET+"(?:\\."+OCTET+"){3}(?![\\w.])");
    private static final Pattern IP_CANDIDATE=Pattern.compile("(?i)(?<![\\w:])[0-9a-f:.]+(?:%[\\w.-]+)?(?![\\w:])");
    private static final Pattern COORDINATES=Pattern.compile("(?<![\\d.])[-+]?\\d{1,3}\\.\\d{3,}\\s*[,;]\\s*[-+]?\\d{1,3}\\.\\d{3,}(?![\\d.])");
    private static final Pattern EMAIL=Pattern.compile("(?i)[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9.-]+\\.[a-z]{2,}");
    private static final Pattern URL=Pattern.compile("(?i)https?://[^\\s<>\\\"]+");
    private static final Pattern SECRET_VALUE=Pattern.compile("(?i)\\b(?:password|passwd|passphrase|secret|token|api[_-]?key|pin[_-]?code|pincode|reg[_-]?code|activation[_-]?code|ssid)\\s*[:=]\\s*[^\\s,;]+");
    private ReportPrivacy() {}

    public static boolean sensitiveName(String name) {
        String key=NORMALIZE.matcher(name.toLowerCase(Locale.ROOT)).replaceAll("");
        return SENSITIVE.matcher(key).find();
    }
    public static String value(String name,String value) {
        if(sensitiveName(name)) return MASK;
        return text(value);
    }
    public static String text(String value) {
        if(value==null) return "";
        String cleaned=SECRET_VALUE.matcher(value).replaceAll(MASK);
        cleaned=URL.matcher(cleaned).replaceAll(MASK); cleaned=EMAIL.matcher(cleaned).replaceAll(MASK);
        cleaned=COORDINATES.matcher(cleaned).replaceAll(MASK); cleaned=MAC.matcher(cleaned).replaceAll(MASK);
        cleaned=IPV4.matcher(cleaned).replaceAll(MASK);
        Matcher addresses=IP_CANDIDATE.matcher(cleaned); StringBuffer result=new StringBuffer();
        while(addresses.find()) {
            String candidate=addresses.group(); long colons=candidate.chars().filter(c->c==':').count();
            addresses.appendReplacement(result,Matcher.quoteReplacement(candidate.contains("::") || colons>=7 ? MASK : candidate));
        }
        addresses.appendTail(result); return result.toString();
    }
    public static String properties(String raw) {
        Matcher records=PROPERTY.matcher(raw); StringBuilder output=new StringBuilder();
        String key=null; int valueStart=0;
        while(records.find()) {
            if(key!=null) appendProperty(output,key,raw.substring(valueStart,records.start()));
            key=records.group(1); valueStart=records.end();
        }
        if(key!=null) appendProperty(output,key,raw.substring(valueStart));
        return output.toString();
    }
    private static void appendProperty(StringBuilder output,String key,String rawValue) {
        String value=rawValue.trim(); if(value.endsWith("]")) value=value.substring(0,value.length()-1);
        output.append('[').append(key).append("]: [").append(value(key,value)).append("]\n");
    }
}
