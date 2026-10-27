package com.infyss.projects;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal, dependency-free data masking. Java 8+.
 *
 * <p>Every mask is an immutable, stateless {@link Mask}, so instances are thread-safe and can be
 * created once and shared (static final fields, Spring beans, etc.).
 *
 * <p>Built-in mask behavior (custom rules define their own contract):
 * <ul>
 *   <li>null and empty input are returned as-is.</li>
 *   <li>First/last masks fail closed: if the "visible" part would cover the whole value (e.g. last 4 of a 4 char
 *       string), the whole value is masked instead of leaking it.</li>
 *   <li>Works on Unicode code points, so surrogate pairs are never split.</li>
 *   <li>By default separators (space, '-', '/', ...) are kept in place and are not counted, so
 *       "4111 1111 1111 1111" becomes "**** **** **** 1111".</li>
 * </ul>
 *
 * <p>Static usage:
 * <pre>
 *   Masks.last(4).apply(card);
 *   Masks.mask("LAST:4", card);          // spec driven, for dynamic / config based masking
 * </pre>
 */
public final class Masks {

    public static final char DEFAULT_MASK_CHAR = '*';

    /** Where the masked region sits for {@link #percent}. */
    public enum Position { START, MIDDLE, END }

    /** A masking rule. Implementations must be stateless and thread-safe. */
    @FunctionalInterface
    public interface Mask {
        String apply(String input);
    }

    private Masks() {}

    /** Replaces a non-empty value with a constant, hiding its original length. */
    public static Mask fixed(String replacement) {
        Objects.requireNonNull(replacement, "replacement");
        return in -> in == null || in.isEmpty() ? in : replacement;
    }

    /** Masks the code-point range [start, end), counting separators too. */
    public static Mask range(int start, int end) {
        return range(start, end, DEFAULT_MASK_CHAR);
    }

    public static Mask range(int start, int end, char maskChar) {
        validateMaskChar(maskChar);
        if (start < 0 || end < start) throw new IllegalArgumentException("Invalid range");
        return in -> apply(in, maskChar, false, (k, n) -> k < start || k >= end);
    }

    /** Masks each regex match, retaining unmatched text. Compile patterns once and reuse. */
    public static Mask regex(java.util.regex.Pattern pattern) {
        return regex(pattern, DEFAULT_MASK_CHAR);
    }

    public static Mask regex(java.util.regex.Pattern pattern, char maskChar) {
        validateMaskChar(maskChar);
        Objects.requireNonNull(pattern, "pattern");
        return in -> {
            if (in == null || in.isEmpty()) return in;
            java.util.regex.Matcher matcher = pattern.matcher(in);
            StringBuffer out = new StringBuffer();
            while (matcher.find()) matcher.appendReplacement(out,
                    java.util.regex.Matcher.quoteReplacement(maskAll(matcher.group(), maskChar)));
            matcher.appendTail(out);
            return out.toString();
        };
    }

    // ------------------------------------------------------------------ factories

    /** Returns the input untouched (e.g. for privileged users). */
    public static Mask none() {
        return in -> in;
    }

    /** Masks every character. */
    public static Mask full() {
        return full(DEFAULT_MASK_CHAR);
    }

    public static Mask full(char maskChar) {
        return firstLast(0, 0, maskChar, false);
    }

    /** Keeps the first n visible. */
    public static Mask first(int n) {
        return firstLast(n, 0);
    }

    public static Mask first(int n, char maskChar) {
        return firstLast(n, 0, maskChar);
    }

    /** Keeps the last n visible. Card number: last(4). Mobile: last(3). */
    public static Mask last(int n) {
        return firstLast(0, n);
    }

    public static Mask last(int n, char maskChar) {
        return firstLast(0, n, maskChar);
    }

    /** Keeps first and last visible. PAN: firstLast(5, 1). Card: firstLast(4, 4). */
    public static Mask firstLast(int first, int last) {
        return firstLast(first, last, DEFAULT_MASK_CHAR, true);
    }

    public static Mask firstLast(int first, int last, char maskChar) {
        return firstLast(first, last, maskChar, true);
    }

    public static Mask firstLast(int first, int last, char maskChar, boolean keepSeparators) {
        validateMaskChar(maskChar);
        if (first < 0 || last < 0) {
            throw new IllegalArgumentException("first/last must be >= 0");
        }
        return in -> apply(in, maskChar, keepSeparators,
                (k, n) -> (long) first + last < n && (k < first || k >= n - last));
    }

    /** First letter of the local part, '@' and the domain stay visible. */
    public static Mask email() {
        return email(DEFAULT_MASK_CHAR);
    }

    public static Mask email(char maskChar) {
        return email(1, maskChar);
    }

    /** Keeps the first visible code points of the local part and the entire domain. */
    public static Mask email(int visible) {
        return email(visible, DEFAULT_MASK_CHAR);
    }

    public static Mask email(int visible, char maskChar) {
        return email(visible, 0, maskChar);
    }

    /** Keeps first and last code points before '@'; the domain stays visible. */
    public static Mask email(int first, int last) {
        return email(first, last, DEFAULT_MASK_CHAR);
    }

    public static Mask email(int first, int last, char maskChar) {
        if (first < 0 || last < 0) throw new IllegalArgumentException("first/last must be >= 0");
        validateMaskChar(maskChar);
        return in -> {
            if (in == null || in.isEmpty()) {
                return in;
            }
            int at = in.lastIndexOf('@');
            if (at < 1) {
                return maskAll(in, maskChar); // not an email: fail closed
            }
            int[] local = in.substring(0, at).codePoints().toArray();
            StringBuilder sb = new StringBuilder(in.length());
            for (int i = 0; i < local.length; i++) {
                if (edgeVisible(i, local.length, first, last)) sb.appendCodePoint(local[i]);
                else sb.append(maskChar);
            }
            return sb.append(in, at, in.length()).toString();
        };
    }

    /** First letter of every whitespace separated word stays visible: "John Smith" -> "J*** S****". */
    public static Mask words() {
        return words(DEFAULT_MASK_CHAR);
    }

    public static Mask words(char maskChar) {
        return words(1, maskChar);
    }

    /** Keeps the first visible code points of each whitespace-separated word. */
    public static Mask words(int visible) {
        return words(visible, DEFAULT_MASK_CHAR);
    }

    public static Mask words(int visible, char maskChar) {
        return words(visible, 0, maskChar);
    }

    /** Keeps first and last code points of each whitespace-separated word. */
    public static Mask words(int first, int last) {
        return words(first, last, DEFAULT_MASK_CHAR);
    }

    public static Mask words(int first, int last, char maskChar) {
        if (first < 0 || last < 0) throw new IllegalArgumentException("first/last must be >= 0");
        validateMaskChar(maskChar);
        return in -> {
            if (in == null || in.isEmpty()) {
                return in;
            }
            int[] cp = in.codePoints().toArray();
            for (int i = 0; i < cp.length;) {
                if (Character.isWhitespace(cp[i])) {
                    i++;
                } else {
                    int start = i;
                    while (i < cp.length && !Character.isWhitespace(cp[i])) i++;
                    for (int j = start; j < i; j++) {
                        if (!edgeVisible(j - start, i - start, first, last)) cp[j] = maskChar;
                    }
                }
            }
            return new String(cp, 0, cp.length);
        };
    }

    /** Masks pct% of the characters (rounded up, so at least pct% is always hidden). */
    public static Mask percent(int pct) {
        return percent(pct, Position.MIDDLE);
    }

    public static Mask percent(int pct, Position position) {
        return percent(pct, position, DEFAULT_MASK_CHAR, true);
    }

    public static Mask percent(int pct, char maskChar) {
        return percent(pct, Position.MIDDLE, maskChar, true);
    }

    public static Mask percent(int pct, Position position, char maskChar) {
        return percent(pct, position, maskChar, true);
    }

    public static Mask percent(int pct, Position position, char maskChar, boolean keepSeparators) {
        validateMaskChar(maskChar);
        if (pct < 0 || pct > 100) {
            throw new IllegalArgumentException("percent must be 0..100");
        }
        Objects.requireNonNull(position, "position");
        return in -> apply(in, maskChar, keepSeparators, (k, n) -> {
            int masked = (int) (((long) n * pct + 99) / 100);
            int visible = n - masked;
            switch (position) {
                case START: return k >= masked;
                case END:   return k < visible;
                default:
                    int head = (visible + 1) / 2;
                    int tail = visible - head;
                    return k < head || k >= n - tail;
            }
        });
    }

    // ------------------------------------------------------------------ spec driven (dynamic)

    /**
     * Builds a mask from a text spec, so the rule can live in config / DB and change without a
     * redeploy. Case-insensitive. Invalid specs throw IllegalArgumentException, so call this at
     * startup / config load to fail fast.
     *
     * <pre>
     *   NONE                      FULL
     *   FIRST:n                   LAST:n                 FIRST_LAST:n:m
     *   aliases: F:n = FIRST:n, L:n = LAST:n, FL:n:m = FIRST_LAST:n:m
     *   EMAIL[:first[:last]]      WORDS[:first[:last]]
     *   PERCENT:p[:START|MIDDLE|END]
     *   presets:  CARD = LAST:4,  PAN = FIRST_LAST:5:1,  NAME = WORDS
     * </pre>
     */
    public static Mask parse(String spec) {
        if (spec == null || spec.trim().isEmpty()) {
            throw new IllegalArgumentException("mask spec is empty");
        }
        // Keep replacement character case intact, including lowercase 'x'.
        Mask m = CACHE.get(spec);
        if (m == null) {
            m = compile(spec);
            synchronized (CACHE) {
                if (CACHE.size() < MAX_CACHE) { // Keep the bound exact even with concurrent misses.
                    CACHE.putIfAbsent(spec, m);
                }
            }
        }
        return m;
    }

    /** One-liner for dynamic use: {@code Masks.mask("LAST:4", value)}. */
    public static String mask(String spec, String value) {
        return parse(spec).apply(value);
    }

    private static final int MAX_CACHE = 256;
    private static final ConcurrentHashMap<String, Mask> CACHE = new ConcurrentHashMap<>();

    private static Mask compile(String spec) {
        char maskChar = DEFAULT_MASK_CHAR;
        int optionStart = spec.indexOf(';');
        if (optionStart >= 0) {
            String option = spec.substring(optionStart + 1);
            if (option.length() != 6 || !option.regionMatches(true, 0, "char=", 0, 5))
                throw new IllegalArgumentException("Expected ;char= followed by one character");
            maskChar = option.charAt(5);
            validateMaskChar(maskChar);
            spec = spec.substring(0, optionStart);
        }
        String[] p = spec.trim().split(":", -1);
        String type = p[0].trim().toUpperCase(Locale.ROOT);
        switch (type) {
            case "F":  type = "FIRST"; break;
            case "L":  type = "LAST"; break;
            case "FL": type = "FIRST_LAST"; break;
            default: break;
        }
        int expected = type.equals("FIRST_LAST") || type.equals("RANGE") ? 3
                : type.equals("FIRST") || type.equals("LAST") || type.equals("PERCENT") ? 2 : 1;
        boolean optionalCount = type.equals("EMAIL") || type.equals("WORDS") || type.equals("NAME");
        if (p.length != expected && !(type.equals("PERCENT") && p.length == 3)
                && !(optionalCount && (p.length == 2 || p.length == 3)))
            throw new IllegalArgumentException("Bad mask spec: " + spec);
        switch (type) {
            case "NONE":       return none();
            case "FULL":       return full(maskChar);
            case "CARD":       return last(4, maskChar);
            case "PAN":        return firstLast(5, 1, maskChar);
            case "EMAIL":      return email(p.length >= 2 ? intArg(p, 1, spec) : 1,
                                           p.length == 3 ? intArg(p, 2, spec) : 0, maskChar);
            case "NAME":
            case "WORDS":      return words(p.length >= 2 ? intArg(p, 1, spec) : 1,
                                           p.length == 3 ? intArg(p, 2, spec) : 0, maskChar);
            case "FIRST":      return first(intArg(p, 1, spec), maskChar);
            case "LAST":       return last(intArg(p, 1, spec), maskChar);
            case "FIRST_LAST": return firstLast(intArg(p, 1, spec), intArg(p, 2, spec), maskChar);
            case "RANGE":      return range(intArg(p, 1, spec), intArg(p, 2, spec), maskChar);
            case "PERCENT":
                Position pos = p.length > 2
                        ? Position.valueOf(p[2].trim().toUpperCase(Locale.ROOT)) : Position.MIDDLE;
                return percent(intArg(p, 1, spec), pos, maskChar);
            default:
                throw new IllegalArgumentException("Unknown mask spec: " + spec);
        }
    }

    private static int intArg(String[] p, int i, String spec) {
        try {
            return Integer.parseInt(p[i].trim());
        } catch (RuntimeException e) { // missing arg or not a number
            throw new IllegalArgumentException("Bad mask spec: " + spec);
        }
    }

    // ------------------------------------------------------------------ core

    /** Decides if the k-th maskable character (0-based) out of n stays visible. */
    private interface Visibility {
        boolean visible(int k, int n);
    }

    private static String apply(String in, char maskChar, boolean keepSeparators, Visibility v) {
        if (in == null || in.isEmpty()) {
            return in;
        }
        int[] cp = in.codePoints().toArray();
        int[] idx = new int[cp.length]; // positions of the characters that take part in masking
        int n = 0;
        for (int i = 0; i < cp.length; i++) {
            if (!keepSeparators || Character.isLetterOrDigit(cp[i])) {
                idx[n++] = i;
            }
        }
        for (int k = 0; k < n; k++) {
            if (!v.visible(k, n)) {
                cp[idx[k]] = maskChar;
            }
        }
        return new String(cp, 0, cp.length);
    }

    private static String maskAll(String in, char maskChar) {
        char[] c = new char[in.codePointCount(0, in.length())];
        Arrays.fill(c, maskChar);
        return new String(c);
    }

    private static void validateMaskChar(char maskChar) {
        if (Character.isSurrogate(maskChar)) throw new IllegalArgumentException("Mask character must not be a surrogate");
    }

    private static boolean edgeVisible(int index, int length, int first, int last) {
        return (long) first + last < length && (index < first || index >= length - last);
    }
}
