import com.infyss.projects.MaskPolicy;
import com.infyss.projects.Masks;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Complete API and configuration reference. Run from the project root, optionally passing
 * a properties-file path. show() and section() only format console output; they are not
 * library APIs. In application code use: String masked = Masks.last(4).apply(value);
 */
public class ExampleUsage {
    private static final Masks.Mask CARD = Masks.last(4); // Build once, reuse.

    public static void main(String[] args) throws Exception {
        section("Positional masks");
        show("first(2)", "Masks.first(2)", "12345678", Masks.first(2).apply("12345678"));
        show("last(4)", "CARD (Masks.last(4))", "4111111111111111", CARD.apply("4111111111111111"));
        show("firstLast(2, 2)", "Masks.firstLast(2, 2)", "12345678", Masks.firstLast(2, 2).apply("12345678"));
        show("keep separators", "CARD (Masks.last(4))", "4111-1111-1111-1111", CARD.apply("4111-1111-1111-1111"));
        show("custom character, mask separators", "Masks.firstLast(2, 2, '#', false)", "12-3456-78", Masks.firstLast(2, 2, '#', false).apply("12-3456-78"));
        show("custom character, keep separators", "Masks.firstLast(2, 2, '#', true)", "12-3456-78", Masks.firstLast(2, 2, '#', true).apply("12-3456-78"));
        show("full", "Masks.full()", "secret-value", Masks.full().apply("secret-value"));
        show("none", "Masks.none()", "public value", Masks.none().apply("public value"));

        section("Email: the domain stays visible");
        show("email()", "Masks.email()", "john@example.com", Masks.email().apply("john@example.com"));
        show("email('#')", "Masks.email('#')", "john@example.com", Masks.email('#').apply("john@example.com"));
        show("email(2)", "Masks.email(2)", "john@example.com", Masks.email(2).apply("john@example.com"));
        show("email(2, '#')", "Masks.email(2, '#')", "john@example.com", Masks.email(2, '#').apply("john@example.com"));
        show("email(1, 1)", "Masks.email(1, 1)", "john@example.com", Masks.email(1, 1).apply("john@example.com"));
        show("email(2, 1, '#')", "Masks.email(2, 1, '#')", "john@example.com", Masks.email(2, 1, '#').apply("john@example.com"));
        show("suffix only: email(0, 2)", "Masks.email(0, 2)", "john@example.com", Masks.email(0, 2).apply("john@example.com"));
        show("hide local part: email(0)", "Masks.email(0)", "john@example.com", Masks.email(0).apply("john@example.com"));

        section("Words: whitespace is preserved");
        show("words()", "Masks.words()", "John Smith", Masks.words().apply("John Smith"));
        show("words('#')", "Masks.words('#')", "John Smith", Masks.words('#').apply("John Smith"));
        show("words(2)", "Masks.words(2)", "John Smith", Masks.words(2).apply("John Smith"));
        show("words(2, '#')", "Masks.words(2, '#')", "John Smith", Masks.words(2, '#').apply("John Smith"));
        show("words(1, 1)", "Masks.words(1, 1)", "John Smith", Masks.words(1, 1).apply("John Smith"));
        show("words(2, 1, '#')", "Masks.words(2, 1, '#')", "John Smith", Masks.words(2, 1, '#').apply("John Smith"));
        show("suffix only: words(0, 2)", "Masks.words(0, 2)", "John Smith", Masks.words(0, 2).apply("John Smith"));
        show("hide words: words(0)", "Masks.words(0)", "John Smith", Masks.words(0).apply("John Smith"));

        section("Percentage, range, fixed replacement and regex");
        show("percent(60): middle", "Masks.percent(60)", "1234567890", Masks.percent(60).apply("1234567890"));
        for (Masks.Position position : Masks.Position.values()) {
            show("percent(50, " + position + ")", "Masks.percent(50, Masks.Position." + position + ")", "ABCDEFGH", Masks.percent(50, position).apply("ABCDEFGH"));
        }
        show("percent with custom options", "Masks.percent(50, Masks.Position.MIDDLE, '#', false)", "AB-CD-EF", Masks.percent(50, Masks.Position.MIDDLE, '#', false).apply("AB-CD-EF"));
        show("percent keeping separators", "Masks.percent(50, Masks.Position.MIDDLE, '#', true)", "AB-CD-EF", Masks.percent(50, Masks.Position.MIDDLE, '#', true).apply("AB-CD-EF"));
        show("range(2, 6): [start, end)", "Masks.range(2, 6)", "12345678", Masks.range(2, 6).apply("12345678"));
        show("fixed replacement hides length", "Masks.fixed(\"[REDACTED]\")", "any length secret", Masks.fixed("[REDACTED]").apply("any length secret"));
        show("regex: mask whole matches", "Masks.regex(Pattern.compile(\"\\\\d+\"))", "otp=123456; ref=42", Masks.regex(Pattern.compile("\\d+")).apply("otp=123456; ref=42"));
        Masks.Mask custom = value -> value == null || value.isEmpty() ? value : "[PRIVATE]";
        show("custom Mask lambda", "custom (value -> value == null || value.isEmpty() ? value : \"[PRIVATE]\")", "secret", custom.apply("secret"));

        section("Optional mask characters: default remains '*'");
        show("DEFAULT_MASK_CHAR", "Masks.DEFAULT_MASK_CHAR", "(constant; no input)", Masks.DEFAULT_MASK_CHAR);
        show("default last(4)", "Masks.last(4)", "12345678", Masks.last(4).apply("12345678"));
        show("first(2, 'X')", "Masks.first(2, 'X')", "12345678", Masks.first(2, 'X').apply("12345678"));
        show("last(4, 'X')", "Masks.last(4, 'X')", "12345678", Masks.last(4, 'X').apply("12345678"));
        show("firstLast(2, 2, '#')", "Masks.firstLast(2, 2, '#')", "12345678", Masks.firstLast(2, 2, '#').apply("12345678"));
        show("full('X')", "Masks.full('X')", "secret", Masks.full('X').apply("secret"));
        show("range(2, 6, '#')", "Masks.range(2, 6, '#')", "12345678", Masks.range(2, 6, '#').apply("12345678"));
        show("regex with '#'", "Masks.regex(Pattern.compile(\"\\\\d+\"), '#')", "otp=1234", Masks.regex(Pattern.compile("\\d+"), '#').apply("otp=1234"));
        show("percent(60, 'X')", "Masks.percent(60, 'X')", "1234567890", Masks.percent(60, 'X').apply("1234567890"));
        show("percent position and character", "Masks.percent(50, Masks.Position.END, '#')", "ABCDEFGH", Masks.percent(50, Masks.Position.END, '#').apply("ABCDEFGH"));
        show("spec with X", "Masks.mask(" + javaLiteral("L:4;char=X") + ", value)", "12345678", Masks.mask("L:4;char=X", "12345678"));
        show("spec with lowercase x", "Masks.mask(" + javaLiteral("L:4;char=x") + ", value)", "12345678", Masks.mask("L:4;char=x", "12345678"));
        show("email spec with #", "Masks.mask(" + javaLiteral("EMAIL:1:1;char=#") + ", value)", "john@example.com", Masks.mask("EMAIL:1:1;char=#", "john@example.com"));
        show("words spec with X", "Masks.mask(" + javaLiteral("WORDS:1:1;char=X") + ", value)", "John Smith", Masks.mask("WORDS:1:1;char=X", "John Smith"));

        section("Every configuration spec (case-insensitive)");
        String[] specs = {
            "NONE", "FULL", "FIRST:2", "F:2", "LAST:4", "L:4", "FIRST_LAST:2:2", "FL:2:2",
            "RANGE:2:6", "CARD", "PAN", "EMAIL", "EMAIL:2", "EMAIL:1:1",
            "WORDS", "WORDS:2", "WORDS:1:1", "NAME", "NAME:2", "NAME:1:1",
            "PERCENT:60", "PERCENT:50:START", "PERCENT:50:MIDDLE", "PERCENT:50:END"
        };
        for (String spec : specs) {
            String input = spec.startsWith("EMAIL") ? "john@example.com"
                    : spec.startsWith("WORDS") || spec.startsWith("NAME") ? "John Smith"
                    : spec.equals("PAN") ? "ABCDE1234F" : "1234567890";
            show(spec, "Masks.mask(" + javaLiteral(spec) + ", value)", input, Masks.mask(spec, input));
            // Every supported spec accepts the same optional character suffix.
            for (char replacement : new char[]{'*', 'X', '#', 'x', '/'}) {
                show(spec + ";char=" + replacement, "Masks.mask(" + javaLiteral(spec + ";char=" + replacement) + ", value)", input, Masks.mask(spec + ";char=" + replacement, input));
            }
        }
        Masks.Mask parsed = Masks.parse(" l : 4 ");
        show("parse once, reuse", "parsed (Masks.parse(\" l : 4 \"))", "12345678", parsed.apply("12345678"));
        show("case-insensitive option name", "Masks.mask(" + javaLiteral("email:1:1;CHAR=x") + ", value)", "john@example.com", Masks.mask("email:1:1;CHAR=x", "john@example.com"));
        // Fixed replacements and regex patterns use factory APIs, not spec strings.

        section("Policy from properties");
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(Paths.get(
                args.length > 0 ? args[0] : "examples/masking.properties"))) {
            props.load(in);
        }
        MaskPolicy policy = new MaskPolicy().load(props, "mask.");
        show("normalized field: card_number", "policy", "card_number", "4111111111111111", policy.apply("card_number", "4111111111111111"));
        show("unknown field defaults to NONE", "policy", "city", "Hyderabad", policy.apply("city", "Hyderabad"));
        policy.rule("password", Masks.fixed("[REDACTED]")); // Factory or custom Mask.
        policy.rule("reference", "F:2");                  // Spec string.
        policy.rule("customField", custom);              // User-defined Mask lambda.
        show("policy custom lambda", "policy", "customField", "secret", policy.apply("customField", "secret"));
        MaskPolicy characterPolicy = new MaskPolicy()
                .defaultRule("FULL;char=X")
                .rule("email", "EMAIL:1:1;char=#");
        show("policy character option", "characterPolicy", "email", "john@example.com", characterPolicy.apply("email", "john@example.com"));
        show("default character option", "characterPolicy", "unknown", "secret", characterPolicy.apply("unknown", "secret"));

        section("Nested payload: maps, lists, arrays and scalars");
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("email", "john@example.com");
        customer.put("customerName", "John Smith");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("cardNumber", "4111111111111111");
        payload.put("customers", Arrays.asList(customer)); // Map entries use their own field rules.
        payload.put("mobile", Arrays.asList("9876543210", "9123456780")); // Elements inherit mobile rule.
        payload.put("email", new String[]{"john@example.com", "jane@example.com"});
        payload.put("otp", new int[]{123456, 654321});
        payload.put("password", "secret");
        payload.put("amount", 1500);                       // NONE retains numeric type.
        payload.put("active", true);
        payload.put("initial", 'J');
        payload.put("note", new StringBuilder("public"));
        payload.put("optional", null);
        payload.put("references", new java.util.LinkedHashSet<>(Arrays.asList("abc", "def")));
        payload.put("matrix", new Object[]{Arrays.asList("one", "two"), new int[]{12, 34}});
        show("masked copy", "policy.applyAll(payload)", payload, policy.applyAll(payload));
        // Input containers are untouched. Arrays/iterables become lists; changed scalars become strings.

        section("Defaults, caller-controlled access and runtime reload");
        MaskPolicy strict = new MaskPolicy().defaultRule("FULL").rule("city", "NONE");
        show("strict unknown field", "strict", "unknown", "secret", strict.apply("unknown", "secret"));
        show("explicit public field", "strict", "city", "Hyderabad", strict.apply("city", "Hyderabad"));
        MaskPolicy fixedDefault = new MaskPolicy().defaultRule(Masks.fixed("[HIDDEN]"));
        show("custom default rule", "fixedDefault", "unknown", "secret", fixedDefault.apply("unknown", "secret"));
        Map<String, Object> scalarValues = new LinkedHashMap<>();
        scalarValues.put("number", 1234);
        scalarValues.put("boolean", true);
        scalarValues.put("character", 'J');
        scalarValues.put("text", new StringBuilder("secret"));
        show("strict scalar types", "strict.applyAll(scalarValues)", scalarValues, strict.applyAll(scalarValues));
        boolean canViewClear = false; // Authorization belongs to the calling application.
        String card = "4111111111111111";
        show("caller-selected visibility", "canViewClear ? card : policy.apply(\"cardNumber\", card)", card, canViewClear ? card : policy.apply("cardNumber", card));
        Map<String, String> replacementRules = new LinkedHashMap<>();
        replacementRules.put("cardNumber", "FL:6:4");
        policy.load(replacementRules); // Replaces ALL field rules atomically; retains the default rule.
        show("after reload", "policy", "cardNumber", card, policy.apply("cardNumber", card));
        // Each applyAll operation uses one snapshot throughout all nested containers.

        section("Boundary behavior and configuration errors");
        show("null stays null", "Masks.email(1, 1)", null, Masks.email(1, 1).apply(null));
        show("empty stays empty", "Masks.words(1, 1)", "", Masks.words(1, 1).apply(""));
        show("short positional input", "Masks.last(4)", "1234", Masks.last(4).apply("1234"));
        show("short email local part", "Masks.email(1, 1)", "ab@example.com", Masks.email(1, 1).apply("ab@example.com"));
        show("short words", "Masks.words(1, 1)", "A Al Bob", Masks.words(1, 1).apply("A Al Bob"));
        show("email without @", "Masks.email()", "invalid", Masks.email().apply("invalid"));
        show("Unicode code points", "Masks.range(1, 2)", "A\uD83D\uDE00B", Masks.range(1, 2).apply("A\uD83D\uDE00B"));
        show("percent zero", "Masks.percent(0)", "1234", Masks.percent(0).apply("1234"));
        show("percent hundred", "Masks.percent(100)", "1234", Masks.percent(100).apply("1234"));
        show("first zero", "Masks.first(0)", "1234", Masks.first(0).apply("1234"));
        show("last zero", "Masks.last(0)", "1234", Masks.last(0).apply("1234"));
        show("both counts zero", "Masks.firstLast(0, 0)", "1234", Masks.firstLast(0, 0).apply("1234"));
        show("email both counts zero", "Masks.email(0, 0)", "john@example.com", Masks.email(0, 0).apply("john@example.com"));
        show("words both counts zero", "Masks.words(0, 0)", "John Smith", Masks.words(0, 0).apply("John Smith"));
        show("words preserve whitespace", "Masks.words(1, 1)", " John\tSmith ", Masks.words(1, 1).apply(" John\tSmith "));
        show("range empty interval", "Masks.range(2, 2)", "1234", Masks.range(2, 2).apply("1234"));
        show("range past input end", "Masks.range(2, 100)", "1234", Masks.range(2, 100).apply("1234"));
        show("regex without matches", "Masks.regex(Pattern.compile(\"\\\\d+\"))", "abc", Masks.regex(Pattern.compile("\\d+")).apply("abc"));
        show("empty fixed replacement", "Masks.fixed(\"\")", "secret", Masks.fixed("").apply("secret"));
        try {
            Masks.parse("FL:2:2:extra");
            throw new AssertionError("Invalid spec was accepted");
        } catch (IllegalArgumentException expected) {
            show("invalid spec rejected", "Masks.parse(spec)", "FL:2:2:extra", "ERROR: " + expected.getMessage());
        }
        section("Invalid configuration examples");
        for (String invalid : new String[]{"L:-1", "EMAIL:1:", "PERCENT:101", "RANGE:4:2",
                "FULL;char=", "L:4;char=XX", "L:4;unknown=X", "L:4;char=X;char=#"}) {
            try {
                Masks.parse(invalid);
            } catch (IllegalArgumentException expected) {
                show(invalid, "Masks.parse(spec)", invalid, "ERROR: " + expected.getMessage());
            }
        }
        Map<String, String> invalidRules = new LinkedHashMap<>();
        invalidRules.put("cardNumber", "FULL");
        invalidRules.put("card_number", "NONE"); // Same normalized field: rejected.
        try {
            policy.load(invalidRules);
        } catch (IllegalArgumentException expected) {
            show("invalid reload", "policy.load(invalidRules)", invalidRules, "ERROR: " + expected.getMessage());
        }
        show("previous rules retained", "policy", "cardNumber", card, policy.apply("cardNumber", card));
        // applyAll rejects cycles, depth > 128, non-string keys and unsupported objects such as POJOs.
        // Custom rule exceptions propagate; invalid loads leave the previous policy unchanged.
    }

    private static void section(String title) {
        System.out.println("\n=== " + title + " ===");
        System.out.println("Name, Code, Input, Output");
    }

    // These inputs are fictional demo data. Do not print original sensitive values in application logs.
    private static void show(String label, String code, Object input, Object result) {
        System.out.println(column(label) + ", " + column(code) + ", "
                + column(tableValue(input)) + ", " + column(tableValue(result)));
    }

    private static String tableValue(Object value) {
        String text = display(value);
        return value instanceof CharSequence || value instanceof Character
                ? text.substring(1, text.length() - 1) : text;
    }

    // Quote fields containing commas or quotes so code arguments stay in one column.
    private static String column(String value) {
        return value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }

    private static void show(String label, String policy, String field, Object input, Object result) {
        show(label, policy + ".apply(" + javaLiteral(field) + ", value)", input, result);
    }

    private static String javaLiteral(String value) {
        return value == null ? "null" : "\"" + value.replace("\\", "\\\\")
                .replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n")
                .replace("\t", "\\t") + "\"";
    }

    private static String display(Object value) {
        if (value == null) return "null";
        if (value instanceof CharSequence || value instanceof Character) return javaLiteral(value.toString());
        if (value instanceof Map) {
            StringBuilder out = new StringBuilder("{");
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (out.length() > 1) out.append(", ");
                out.append(entry.getKey()).append('=').append(display(entry.getValue()));
            }
            return out.append('}').toString();
        }
        if (value instanceof Iterable || value.getClass().isArray()) {
            StringBuilder out = new StringBuilder("[");
            if (value instanceof Iterable) {
                for (Object element : (Iterable<?>) value) {
                    if (out.length() > 1) out.append(", ");
                    out.append(display(element));
                }
            } else {
                for (int i = 0; i < java.lang.reflect.Array.getLength(value); i++) {
                    if (i > 0) out.append(", ");
                    out.append(display(java.lang.reflect.Array.get(value, i)));
                }
            }
            return out.append(']').toString();
        }
        return String.valueOf(value);
    }

}
