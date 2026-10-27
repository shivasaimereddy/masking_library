# com.infyss.projects

Small data-masking library for Java. Two classes, no dependencies, Java 8+.
Mask rules can be fixed in code or driven by config, and can be changed at runtime.

- `Masks`: the masking rules (static factories, immutable and thread-safe)
- `MaskPolicy`: field name -> rule mapping, reloadable at runtime

Java package, Maven group ID, and module name: `com.infyss.projects`. Maven artifact ID: `masking`.

---

For application setup and integration, see [the JAR user guide](USER_GUIDE.md).

## 1. Mask types

| # | Use case | Call | Spec string | Input | Output |
|---|----------|------|-------------|-------|--------|
| 1 | Card, last 4 visible | `Masks.last(4)` | `CARD` or `LAST:4` | `4111111111111111` | `************1111` |
| 2 | Email | `Masks.email()` | `EMAIL` | `john.doe@gmail.com` | `j*******@gmail.com` |
| 3 | Last 3 visible | `Masks.last(3)` | `LAST:3` | `9876543210` | `*******210` |
| 4 | PAN, first 5 + last 1 | `Masks.firstLast(5, 1)` | `PAN` or `FIRST_LAST:5:1` | `ABCDE1234F` | `ABCDE****F` |
| 5 | First 4 + last 4 | `Masks.firstLast(4, 4)` | `FIRST_LAST:4:4` | `1234567890123456` | `1234********3456` |
| 6 | First letter of each word | `Masks.words()` | `NAME` or `WORDS` | `Shiva Sai Kumar` | `S**** S** K****` |
| 7 | Percentage | `Masks.percent(60)` | `PERCENT:60` | `1234567890` | `12******90` |

Extras: `Masks.first(n)` (`FIRST:n`), `Masks.full()` (`FULL`), `Masks.none()` (`NONE`, pass-through).

### Percentage

`PERCENT:p[:POSITION]`, where POSITION is where the masked block sits:

| Spec | Input | Output |
|------|-------|--------|
| `PERCENT:60` (MIDDLE, default) | `1234567890` | `12******90` |
| `PERCENT:50:START` | `ABCDEFGH` | `****EFGH` |
| `PERCENT:50:END` | `ABCDEFGH` | `ABCD****` |

The count of masked characters is rounded **up**, so at least p% is always hidden.
`0` masks nothing, `100` masks everything.

### Default mask behavior

- **null / empty** input is returned unchanged.
- **Fail-closed positional masks.** For first/last masks, if the visible part would cover the whole value, everything is masked
  (`last(4)` on `1234` gives `****`; `PAN` on a 6 char value gives `******`).
  An email-mask input with no `@` is fully masked. An email with a 1 character local part
  gives `*@domain`.
- **Separators are kept and not counted.** Anything that is not a letter or digit (space, `-`, `/`, `+`, `.`)
  stays where it is: `4111 1111 1111 1111` -> `**** **** **** 1111`,
  `+91-98765-43210` + `last(3)` -> `+**-*****-**210`.
  Use the 4-argument overloads with `keepSeparators=false` to mask them too.
  (Email and words masks have their own rules and ignore this flag.)
- **Unicode safe.** Works on code points, so surrogate pairs are never split.
- **Mask character** is `*`; the longer overloads take any `char`.

---

## 2. API

### `Masks`

```java
Masks.Mask m = Masks.last(4);              // build once, reuse (immutable, thread-safe)
String out   = m.apply("4111111111111111");

Masks.first(int n)
Masks.last(int n)
Masks.firstLast(int first, int last)
Masks.firstLast(int first, int last, char maskChar, boolean keepSeparators)
Masks.email()           Masks.email(char maskChar)
Masks.words()           Masks.words(char maskChar)
Masks.percent(int pct)
Masks.percent(int pct, Masks.Position pos)
Masks.percent(int pct, Masks.Position pos, char maskChar, boolean keepSeparators)
Masks.full()  Masks.none()

Masks.parse("LAST:4")                      // spec -> Mask (cached, throws IllegalArgumentException if invalid)
Masks.mask("LAST:4", value)                // shortcut for parse(spec).apply(value)
```

`Mask` is a functional interface (`String apply(String)`), so a custom rule is a lambda:

```java
Masks.Mask custom = v -> v == null ? null : v.replaceAll("\\d(?=\\d{2})", "#");
```

### Spec grammar (case-insensitive)

```
NONE        FULL
FIRST:n     LAST:n      FIRST_LAST:n:m     RANGE:start:end
F:n         L:n         FL:n:m            (short aliases)
EMAIL       WORDS
PERCENT:p[:START|MIDDLE|END]
presets:  CARD = LAST:4    PAN = FIRST_LAST:5:1    NAME = WORDS
```

Invalid specs throw `IllegalArgumentException`. Parse them at startup / config load so mistakes fail
immediately and not at the first masked request.

Short aliases are case-insensitive and work anywhere a spec is accepted:

```properties
mask.card=L:4
mask.account=FL:2:2
mask.reference=F:2
mask.email=EMAIL:1:1
mask.customerName=WORDS:1:1
```

`F`, `L`, and `FL` mean `FIRST`, `LAST`, and `FIRST_LAST`. The descriptive names and
Java methods (`Masks.first`, `Masks.last`, `Masks.firstLast`) remain supported.
Parsed specs are cached (max 256 entries).

### `MaskPolicy`

```java
MaskPolicy policy = new MaskPolicy()
        .rule("cardNumber", "CARD")                 // from a spec
        .rule("otp", Masks.full())                  // from a Mask
        .defaultRule("FULL");                       // optional: unknown fields get this (default: NONE)

policy.apply("cardNumber", value);                  // masked per rule
policy.applyAll(map);                               // copies and masks nested maps, lists, arrays, scalars
policy.load(Map<String,String> fieldToSpec);        // atomic replace of all rules
policy.load(Properties props, "mask.");             // same, from properties with a prefix
```

- Field names are normalised, so `cardNumber`, `card_number` and `CARD-NUMBER` are the same rule.
- Reads are lock-free. `rule(...)` / `load(...)` publish a new immutable snapshot, so concurrent readers
  see the old rules or the new ones, never a mix. `load(...)` changes nothing if any spec is invalid.
- Scalar fields with no rule pass through unchanged unless you set `defaultRule("FULL")` (fail-closed).
- Who may see clear text is the caller's decision:
  `canViewClear ? value : policy.apply("pan", value)`.

---

## 3. Build

Requires JDK 8+. Builds target Java 8 bytecode by default.

```bash
# Linux / macOS; set JAVA_HOME to select a consistent JDK
./build.sh
```

```powershell
# Windows; uses JAVA_HOME if set, otherwise the JDK containing javac
./build.ps1
# Or from cmd.exe:
build.bat
```

Both scripts compile the library and create `dist/masking-1.0.0.jar`.
Each run uses a fresh compilation directory to avoid stale classes. Previous outputs are not deleted.

Maven is optional:

```bash
mvn clean package
mvn clean install
```

The build contains no test sources or test execution hooks.

```xml
<dependency>
  <groupId>com.infyss.projects</groupId>
  <artifactId>masking</artifactId>
  <version>1.0.0</version>
</dependency>
```

For manual integration, add the JAR to your classpath, `WEB-INF/lib` for a WAR,
or use `implementation files('libs/masking-1.0.0.jar')` in Gradle.

---

## 4. Run the example

[examples/ExampleUsage.java](examples/ExampleUsage.java) demonstrates every supported
mask factory and overload, every spec and short alias, custom rules, properties,
policy defaults, reloads, nested payloads, and boundary behavior.

Build the JAR first (`./build.sh` or `./build.ps1`), then run from the project root:

```bash
# Linux / macOS (JDK 9+ compiler)
mkdir -p build/example
javac --release 8 -cp dist/masking-1.0.0.jar -d build/example examples/ExampleUsage.java
java -cp dist/masking-1.0.0.jar:build/example ExampleUsage
```

```powershell
# Windows PowerShell (JDK 9+ compiler)
New-Item -ItemType Directory -Force build/example | Out-Null
javac --release 8 -cp dist/masking-1.0.0.jar -d build/example examples/ExampleUsage.java
java -cp 'dist/masking-1.0.0.jar;build/example' ExampleUsage
```

With a JDK 8 compiler, replace `--release 8` with `-source 8 -target 8`.
Optionally pass a properties-file path as the first program argument. The default is
`examples/masking.properties`. Output is grouped into labeled sections so each rule
can be compared with its result. Sample data is fictional.

---

## 5. Integration patterns

### Plain Java: one shared policy

```java
public final class Masking {
    public static final MaskPolicy POLICY = new MaskPolicy().defaultRule("NONE");

    static {
        Properties p = new Properties();
        try (InputStream in = Masking.class.getResourceAsStream("/masking.properties")) {
            p.load(in);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
        POLICY.load(p, "mask.");
    }
}

log.info("txn card={}", Masking.POLICY.apply("cardNumber", card));
```

### Reload at runtime (no restart)

Call `policy.load(newProps, "mask.")` from your config-refresh hook, admin endpoint, or a scheduled task.
Each applyAll call captures one snapshot of rules and fallback, including all nested containers. Later calls see published updates.

### Spring (snippet, not compiled here)

```java
@Configuration
class MaskingConfig {
    @Bean
    MaskPolicy maskPolicy(Environment env) {
        MaskPolicy p = new MaskPolicy();
        p.rule("cardNumber", env.getProperty("mask.cardNumber", "CARD"));
        p.rule("email",      env.getProperty("mask.email",      "EMAIL"));
        return p;
    }
}
```

### Jackson, mask on serialisation (snippet, not compiled here)

```java
public class CardSerializer extends StdSerializer<String> {
    private static final Masks.Mask MASK = Masks.last(4);
    public CardSerializer() { super(String.class); }
    @Override
    public void serialize(String v, JsonGenerator g, SerializerProvider p) throws IOException {
        g.writeString(MASK.apply(v));
    }
}

public class PaymentResponse {
    @JsonSerialize(using = CardSerializer.class)
    private String cardNumber;
}
```

### Database / UI layer

Mask as late as possible, at the response boundary, and keep the clear value out of logs and DTOs where you can.
For per-role visibility: `canViewClear ? value : policy.apply(field, value)`.

---

## 6. Design notes

- **Four implementations cover the seven required types.** `last(n)`, `first(n)`, `PAN` and `first4/last4` are all
  `firstLast(first, last)`. That keeps behaviour and edge cases in one place.
- **One core routine.** Every positional mask picks the maskable characters (letters and digits, or all of them),
  then asks "is character k of n visible?". Fail-closed, separators and Unicode handling are written once.
- **Immutable masks, snapshot policy.** No locks on the read path; config reload is an atomic reference swap.
- **Fail fast on config.** Bad specs throw when loaded, not when masking.
- **No dependencies.** Easy to approve in locked-down environments and no version conflicts.

## 7. Limits and security notes

- Masking hides data, it does not protect it. Do not use it in place of encryption or tokenisation.
- Standard positional masks keep the code-point count (and separator layout). If length itself is sensitive,
  use `Masks.fixed("[REDACTED]")`.
- Percentage masking is deterministic: the same input always gives the same output.
- Java `String`s are immutable and cannot be wiped from memory.
- Words mask splits on whitespace only: `Mary-Jane Smith` -> `M******** S****`.
- Email mask uses the last `@` as the split, which is correct for valid addresses.
  It does not validate the address.

## 8. Layout

```
masking-lib/
  README.md
  pom.xml
  build.sh / build.bat / build.ps1
  src/main/java/com/infyss/projects/Masks.java
  src/main/java/com/infyss/projects/MaskPolicy.java
  examples/ExampleUsage.java
  examples/masking.properties
```

## 9. Additional masks and payload behavior

```java
Masks.fixed("[REDACTED]").apply("any length secret"); // [REDACTED]
Masks.range(2, 6).apply("12345678");                 // 12****78
Masks.mask("RANGE:2:6", "12345678");                 // 12****78
Masks.regex(java.util.regex.Pattern.compile("\\d+"))
     .apply("otp=123456");                            // otp=******

MaskPolicy policy = new MaskPolicy()
    .defaultRule("FULL")
    .rule("city", "NONE")
    .rule("password", Masks.fixed("[REDACTED]"));
```

`range(start, end)` uses zero-based code-point indices, includes start, excludes end,
and counts separators. An end beyond the input is clipped naturally; a start beyond it
leaves the input unchanged. `regex` masks whole matches; unmatched text stays visible.
Patterns are caller supplied and should be trusted and appropriate for the input size.
Fixed, range and regex masks preserve null/empty inputs. Fixed masks can hide input length.

`applyAll` supports string-keyed maps, iterables, object arrays, primitive arrays,
strings, character sequences, numbers, booleans, characters, and nulls. Arrays and iterables
become new lists; maps become new insertion-ordered maps. Scalar list elements inherit the
containing field rule. Nested maps use their own entry names. Input containers are not modified.
Scalar values whose text changes become strings; unchanged values retain their original type.
For example, numeric OTP `123456` under `FULL` becomes string `"******"`.
Nulls remain null. Unsupported object types, non-string map keys, cycles, and nesting deeper
than 128 levels throw `IllegalArgumentException`. Arbitrary POJOs need an explicit adapter.
Shared containers without cycles are supported.

Configuration parsing rejects extra or empty arguments. Loading multiple field names that
normalize to the same key is rejected instead of choosing an arbitrary winner. Failed loads
leave the existing policy unchanged. Blank normalized field names are invalid.
Single-character name words now mask completely: `A B C` becomes `* * *`.
Custom mask characters cannot be UTF-16 surrogates.

The default policy remains pass-through for compatibility. Use `defaultRule("FULL")`
for an explicit allowlist policy. Separator preservation treats every non-letter/digit
code point as a separator (including symbols and emoji). Use `FULL` or
`firstLast(first, last, '*', false)` when those characters must also be hidden.
Code-point safety does not guarantee preservation of grapheme clusters.

Custom `Mask` lambdas must be thread-safe. Their exceptions propagate; the library does not
silently return clear text when a rule fails. Field-path selection, POJO annotations, and
framework integrations are not included in this dependency-free core.


## Configurable email and word prefixes

```java
Masks.email(2).apply("john@example.com"); // jo**@example.com
Masks.words(2).apply("John Smith");       // Jo** Sm***
Masks.email(2, '#');                     // custom mask character
Masks.words(2, '#');

new MaskPolicy()
    .rule("email", "EMAIL:2")
    .rule("customerName", "WORDS:2");     // NAME:2 is also supported
```

The count is a non-negative number of Unicode code points. Zero hides the whole local
part or each word. If the count would expose an entire local part or word, that part
is fully masked: `email(2)` on `ab@example.com` gives `**@example.com`, and
`words(2)` on `Al John` gives `** Jo**`. Whitespace and email domains remain visible.
Existing no-argument and character-only overloads still default to one visible character.

## First and last characters in emails and words

```java
Masks.email(1, 1).apply("john@example.com"); // j**n@example.com
Masks.words(1, 1).apply("John Smith");       // J**n S***h
Masks.email(2, 1).apply("john@example.com"); // jo*n@example.com
Masks.words(2, 1).apply("John Smith");       // Jo*n Sm**h
Masks.email(1, 1, '#');                     // custom mask character
Masks.words(1, 1, '#');

new MaskPolicy()
    .rule("email", "EMAIL:1:1")
    .rule("customerName", "WORDS:1:1");     // NAME:1:1 also works
```

Arguments are first-visible and last-visible code-point counts. Zero is allowed for
either side. Existing prefix-only forms remain supported. When first + last covers
the entire local part or word, that part masks completely: `ab@x.com` becomes
`**@x.com`, and `Al` becomes `**` for counts 1 and 1. Domains and whitespace stay visible.


## Optional replacement characters

All character-based masks default to `*`. Use `Masks.first(2, 'X')`,
`Masks.last(4, 'X')`, `Masks.firstLast(2, 2, '#')`, `Masks.full('X')`,
`Masks.range(2, 6, '#')`, `Masks.regex(pattern, '#')`, or
`Masks.percent(60, 'X')` to customize them. Email and word overloads also accept
characters, for example `Masks.email(1, 1, '#')` and `Masks.words(1, 1, 'X')`.

Every character-based spec accepts an optional `;char=` suffix:

```properties
mask.cardNumber=L:4;char=X
mask.email=EMAIL:1:1;char=#
mask.customerName=WORDS:1:1;char=x
mask.otp=FULL;char=X
```

The replacement must be exactly one non-surrogate Java character and preserves case.
Rule and option names are case-insensitive. Invalid or duplicate options are rejected.
`NONE` remains pass-through. Fixed replacements and custom lambdas define their own output.
