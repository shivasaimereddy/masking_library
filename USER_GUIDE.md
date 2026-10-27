# Masking JAR user guide

Use `masking-1.0.0.jar` to mask strings and structured payloads in Java applications.
The library runs on Java 8 or later, has no runtime dependencies, and uses package
`com.infyss.projects`. It is a library JAR, so add it to your application rather than
running it with `java -jar`.

## Add the JAR to your application

### Plain Java

Use the supplied `masking-1.0.0.jar`. Copy it into your application's `lib` folder. Create `Demo.java`:

```java
import com.infyss.projects.Masks;

public class Demo {
    public static void main(String[] args) {
        String masked = Masks.last(4).apply("4111111111111111");
        System.out.println(masked); // ************1111
    }
}
```

Compile and run on Windows:

```powershell
javac -cp lib/masking-1.0.0.jar Demo.java
java -cp '.;lib/masking-1.0.0.jar' Demo
```

On Linux or macOS:

```bash
javac -cp lib/masking-1.0.0.jar Demo.java
java -cp '.:lib/masking-1.0.0.jar' Demo
```

### Maven

Install a supplied JAR into your local Maven repository:

```bash
mvn install:install-file -Dfile=lib/masking-1.0.0.jar -DgroupId=com.infyss.projects -DartifactId=masking -Dversion=1.0.0 -Dpackaging=jar
```

Add this dependency to your application's `pom.xml`:

```xml
<dependency>
  <groupId>com.infyss.projects</groupId>
  <artifactId>masking</artifactId>
  <version>1.0.0</version>
</dependency>
```

These coordinates are not assumed to be published to Maven Central. Install the supplied
JAR locally, or use the repository details provided by your organization.

### Gradle or WAR applications

For a local Gradle dependency, copy the JAR to `libs` and use:

```groovy
dependencies {
    implementation files('libs/masking-1.0.0.jar')
}
```

For a traditional WAR application, include the JAR in `WEB-INF/lib`.

## Choose a mask

Import `com.infyss.projects.Masks`. Create masks once and reuse them.

| Purpose | Java call | Input | Output |
|---|---|---|---|
| Keep first two | `Masks.first(2)` | `12345678` | `12******` |
| Keep last four | `Masks.last(4)` | `12345678` | `****5678` |
| Keep first and last two | `Masks.firstLast(2, 2)` | `12345678` | `12****78` |
| Hide everything | `Masks.full()` | `secret` | `******` |
| Leave unchanged | `Masks.none()` | `public` | `public` |
| Email default | `Masks.email()` | `john@example.com` | `j***@example.com` |
| Email first two | `Masks.email(2)` | `john@example.com` | `jo**@example.com` |
| Email first and last | `Masks.email(1, 1)` | `john@example.com` | `j**n@example.com` |
| Email last two only | `Masks.email(0, 2)` | `john@example.com` | `**hn@example.com` |
| Each word default | `Masks.words()` | `John Smith` | `J*** S****` |
| Each word first two | `Masks.words(2)` | `John Smith` | `Jo** Sm***` |
| Each word first and last | `Masks.words(1, 1)` | `John Smith` | `J**n S***h` |
| Each word last two only | `Masks.words(0, 2)` | `John Smith` | `**hn ***th` |
| Percentage in middle | `Masks.percent(60)` | `1234567890` | `12******90` |
| Code point range | `Masks.range(2, 6)` | `12345678` | `12****78` |
| Fixed replacement | `Masks.fixed("[HIDDEN]")` | `secret` | `[HIDDEN]` |

Append `.apply(value)` to any factory call to obtain the masked string.
`range(start, end)` includes start and excludes end, counts separators, and leaves text
outside the range visible. Out-of-bounds ranges are naturally clipped.

### Percentage position and custom characters

```java
Masks.percent(50, Masks.Position.START).apply("ABCDEFGH");  // ****EFGH
Masks.percent(50, Masks.Position.MIDDLE).apply("ABCDEFGH"); // AB****GH
Masks.percent(50, Masks.Position.END).apply("ABCDEFGH");    // ABCD****
Masks.percent(50, Masks.Position.MIDDLE, '#', false);
Masks.firstLast(2, 2, '#', false).apply("12-3456-78");       // 12######78
Masks.email('#');
Masks.email(2, '#');
Masks.email(1, 1, '#').apply("john@example.com");            // j##n@example.com
Masks.words('#');
Masks.words(2, '#');
Masks.words(1, 1, '#').apply("John Smith");                  // J##n S###h
```

Percentage counts round up. Zero masks nothing; 100 masks all participating characters.
For positional and percentage masks, separators are preserved by default and do not count.
Set `keepSeparators=false` in the four-argument overload to include them. Every non-letter
or digit code point is treated as a separator, including symbols and emoji. `full()` masks
separators too. Email domains remain visible; words split on whitespace only.

### Regex and custom rules

```java
import java.util.regex.Pattern;

Masks.Mask numbers = Masks.regex(Pattern.compile("\\d+"));
String output = numbers.apply("otp=123456; ref=42"); // otp=******; ref=**

Masks.Mask custom = value -> value == null || value.isEmpty() ? value : "[PRIVATE]";
```

Regex masking hides whole matches and leaves unmatched text visible. Use trusted patterns
appropriate for your input size. Custom lambdas must be thread-safe and define their own
null handling. Exceptions propagate to the caller.

## Configure rules with strings

```java
String card = Masks.mask("L:4", "4111111111111111");
Masks.Mask reusable = Masks.parse("FL:2:2");
String account = reusable.apply("12345678"); // 12****78
```

Specs are case-insensitive. Supported forms are:

| Spec | Meaning |
|---|---|
| `FIRST:n` or `F:n` | Keep first n |
| `LAST:n` or `L:n` | Keep last n |
| `FIRST_LAST:first:last` or `FL:first:last` | Keep both ends |
| `EMAIL`, `EMAIL:first`, `EMAIL:first:last` | Mask the local part |
| `WORDS`, `WORDS:first`, `WORDS:first:last` | Mask each word |
| `NAME`, `NAME:first`, `NAME:first:last` | Aliases for WORDS |
| `PERCENT:p` or `PERCENT:p:START`, `:MIDDLE`, `:END` | Mask a percentage |
| `RANGE:start:end` | Mask a code point range |
| `CARD` | Alias for LAST:4 |
| `PAN` | Alias for FIRST_LAST:5:1 |
| `FULL`, `NONE` | Hide everything or pass through |

Email and word defaults are first=1, last=0. Counts must be non-negative;
percentage must be 0 to 100. Extra, missing, empty or invalid arguments are rejected
with `IllegalArgumentException`. Fixed replacement, regex patterns and
separator settings use Java factories rather than spec strings.

### Choose the replacement character

Every character-based mask defaults to `*`. Change it per rule by adding `;char=`
and exactly one non-surrogate Java character. Rule names are case-insensitive;
replacement characters preserve their case. Unknown options, empty characters,
multiple characters and duplicate options are rejected.

```properties
mask.cardNumber=L:4;char=X
mask.email=EMAIL:1:1;char=#
mask.customerName=WORDS:1:1;char=x
mask.otp=FULL;char=X
mask.reference=RANGE:2:6;char=#
```

```java
Masks.last(4).apply("12345678");                  // ****5678 (default)
Masks.last(4, 'X').apply("12345678");             // XXXX5678
Masks.first(2, '#');
Masks.firstLast(2, 2, 'X');
Masks.full('X');
Masks.range(2, 6, '#');
Masks.regex(Pattern.compile("\\d+"), '#');
Masks.percent(60, 'X');
Masks.percent(50, Masks.Position.END, '#');
Masks.mask("L:4;char=x", "12345678");             // xxxx5678
new com.infyss.projects.MaskPolicy().rule("email", "EMAIL:1:1;char=#");
```

The suffix also works with presets and short aliases. Without it, existing specs
keep using `*`. `NONE` always passes through (a character option has no effect).
`fixed` uses its replacement string directly, and custom lambdas choose their own output.

## Apply rules by field name

```java
import com.infyss.projects.MaskPolicy;

MaskPolicy policy = new MaskPolicy()
    .defaultRule("FULL")
    .rule("cardNumber", "L:4")
    .rule("email", "EMAIL:1:1")
    .rule("customerName", "WORDS:1:1")
    .rule("password", Masks.fixed("[REDACTED]"))
    .rule("city", "NONE");

String card = policy.apply("card_number", "4111111111111111");
```

Field matching ignores case, underscores, hyphens and whitespace. Without `defaultRule`,
unknown scalar fields pass through unchanged. `defaultRule("FULL")` explicitly hides
unknown fields. `defaultRule(Masks.fixed("[HIDDEN]"))` provides a custom default.
Authorization stays in your application:

```java
String visible = canViewClear ? original : policy.apply("cardNumber", original);
```

## Load properties and reload rules

Example `masking.properties`:

```properties
mask.cardNumber=L:4
mask.email=EMAIL:1:1
mask.customerName=WORDS:1:1
mask.otp=FULL
mask.amount=NONE
```

```java
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

Properties properties = new Properties();
try (InputStream in = Files.newInputStream(Paths.get("masking.properties"))) {
    properties.load(in);
}
policy.load(properties, "mask.");
```

`policy.load(Map<String, String>)` also accepts field-to-spec mappings. Both load methods
replace all field rules atomically and retain the current default. Call load from your
application's configuration-refresh hook. Invalid specs or duplicate normalized field
names reject the entire load and leave the old policy intact. `rule` updates one field.
Reads use immutable snapshots; each payload operation uses one snapshot throughout.

## Mask structured payloads

```java
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

Map<String, Object> customer = new LinkedHashMap<>();
customer.put("email", "john@example.com");
Map<String, Object> payload = new LinkedHashMap<>();
payload.put("customers", Arrays.asList(customer));
payload.put("otp", new int[]{123456, 654321});
payload.put("amount", 1500);

MaskPolicy payloadPolicy = new MaskPolicy()
    .rule("email", "EMAIL:1:1")
    .rule("otp", "FULL");
Map<String, Object> masked = payloadPolicy.applyAll(payload);
// {customers=[{email=j**n@example.com}], otp=[******, ******], amount=1500}
```

Supported values are maps with string keys, iterables, arrays (including primitive
arrays), character sequences, numbers, booleans, characters and nulls. Input containers
are not modified. Output maps preserve iteration order; arrays and iterables become
lists. Scalar collection elements inherit their containing field rule; nested maps use
their own entry names. Numeric and boolean values become strings when masking changes
their text; unchanged values retain their type. Nulls stay null.

Cycles, nesting deeper than 128 levels, non-string map keys and unsupported object
values throw `IllegalArgumentException`. Convert POJOs through an explicit adapter.
No field-path selectors, POJO annotations or automatic Jackson integration are included.

## Important behavior

Built-in masks preserve null and empty strings. First/last, email and word masks hide
the entire relevant part when the requested visible counts would expose all of it:

```java
Masks.last(4).apply("1234");                // ****
Masks.email(1, 1).apply("ab@example.com");   // **@example.com
Masks.words(1, 1).apply("Al Bob");          // ** B*b
```

Email splitting uses the last `@`; it is not an address validator. Inputs without a
non-empty local part are fully masked. Code point handling avoids splitting surrogate
pairs but does not preserve all grapheme clusters. Standard masks disclose code point
count and sometimes separators; use fixed replacement when length must be hidden.
Masking is not encryption, tokenization or irreversible anonymization. Keep original
sensitive values out of logs and mask at the response boundary.

## Troubleshooting

| Problem | Action |
|---|---|
| Package or class not found | Put the JAR on compile and runtime classpaths; import `com.infyss.projects` |
| No main manifest attribute | Run your application with the JAR as a dependency; do not use `java -jar` on this library |
| Unsupported class version | Use Java 8 or later with the supplied library JAR |
| Maven cannot resolve dependency | Install locally or configure your organization's artifact repository |
| Unexpected clear value | Check field spelling, default rule, scalar type and separator handling |
| Value type changed | Masked scalar values are strings; adapt your response model accordingly |
| Configuration rejected | Check the spec grammar and duplicate normalized field names |

For a complete runnable reference, see `examples/ExampleUsage.java` and
`examples/masking.properties` if provided with your JAR.
