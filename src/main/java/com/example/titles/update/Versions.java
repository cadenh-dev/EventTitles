package com.example.titles.update;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Bukkit-free numeric version comparison ("v1.10.0" > "1.9.2"; "1.2" == "1.2.0"). */
public final class Versions {
    private Versions() {}

    /** Strips a leading "v"/"V" and surrounding whitespace. */
    public static String normalize(String version) {
        String v = version == null ? "" : version.trim();
        return v.toLowerCase(Locale.ROOT).startsWith("v") ? v.substring(1) : v;
    }

    /** True when {@code candidate} is strictly newer than {@code current}. */
    public static boolean isNewer(String candidate, String current) {
        return compare(candidate, current) > 0;
    }

    /**
     * Compares dot-separated numeric parts; anything after '-' or '+' (e.g. "-SNAPSHOT") makes a version
     * sort just below the same number without it.
     */
    public static int compare(String a, String b) {
        String na = normalize(a);
        String nb = normalize(b);
        List<Integer> pa = parts(na);
        List<Integer> pb = parts(nb);
        for (int i = 0; i < Math.max(pa.size(), pb.size()); i++) {
            int x = i < pa.size() ? pa.get(i) : 0;
            int y = i < pb.size() ? pb.get(i) : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        boolean qa = hasQualifier(na);
        boolean qb = hasQualifier(nb);
        return qa == qb ? 0 : (qa ? -1 : 1);
    }

    private static boolean hasQualifier(String v) {
        return v.indexOf('-') >= 0 || v.indexOf('+') >= 0;
    }

    private static List<Integer> parts(String v) {
        String core = v.split("[-+]", 2)[0];
        List<Integer> out = new ArrayList<>();
        for (String p : core.split("\\.")) {
            try {
                out.add(Integer.parseInt(p.replaceAll("\\D", "").isEmpty() ? "0" : p.replaceAll("\\D", "")));
            } catch (NumberFormatException e) {
                out.add(0);
            }
        }
        return out;
    }
}
