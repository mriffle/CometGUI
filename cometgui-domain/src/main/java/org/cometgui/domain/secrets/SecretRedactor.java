/*
 * CometGUI -- Comet to Percolator proteomics search workflow with provenance.
 * Copyright (C) 2026 The CometGUI authors.
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License, version 3, as published
 * by the Free Software Foundation. It is distributed WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for details.
 *
 * The full licence is the LICENSE file at the root of this repository. If it
 * is missing, see <https://www.gnu.org/licenses/gpl-3.0.html>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.cometgui.domain.secrets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The single rule set that removes credentials from every string this application writes down.
 *
 * <p>{@code R-SEC-03} requires that secrets are "redacted from command display, process environment
 * capture and exported reports", and phase 04's exit gate item 6 states the consequence as a
 * property that can be tested: a seeded corpus of secrets appears nowhere in the generated JSON,
 * the generated reStructuredText or the event log. This class is where that is true, once. The
 * manifest writer, the report writer and the event-log writer all call it, so a field added to one
 * of them cannot open a leak path the others already closed.
 *
 * <p><strong>The rules come in two halves and both are load-bearing.</strong>
 *
 * <ul>
 *   <li><b>Pattern rules</b> catch a secret nobody declared: the password in a credential-bearing
 *       URL, an {@code Authorization} or {@code Proxy-Authorization} header, an assignment whose
 *       <em>name</em> looks secret, a handful of token formats that are recognisable on sight, and
 *       a PEM-encoded private key, which is the one carrier that spans lines.
 *   <li><b>The registry</b> ({@link SecretRegistry}) catches the secret the application actually
 *       holds. It came from the OS keychain, so its exact characters are known and every emitted
 *       string has that substring replaced, wherever it appears and whatever syntax surrounds it.
 *       It runs both before and after the pattern rules; see {@link #redactText(String)} for the
 *       measurement that put it at both ends rather than only at the end.
 * </ul>
 *
 * <p>Neither half is sufficient. Pattern rules cannot see a bare token passed as {@code -k <value>}
 * -- see the design constraint below -- and the registry cannot see a credential the application
 * never learned about, such as one embedded in a URL the user typed into a parameter field.
 *
 * <p><strong>Three entry points, one rule set.</strong> {@link #redactText(String)} handles free
 * text: a log line, an exception message, one field of a manifest. {@link
 * #redactEnvironment(java.util.Map)} adds a name-driven rule, because an environment variable
 * called {@code PERCOLATOR_PASSWORD} holds a secret no matter what the value looks like. {@link
 * #redactArgv(java.util.List)} adds the flag rules. All three end in the same text rules and the
 * same registry.
 *
 * <p><strong>THE DESIGN CONSTRAINT: ONLY LONG FLAGS REDACT THE NEXT ARGUMENT.</strong> {@code
 * AC-PRV-03} requires the exact command argument array to be recorded, and a recorded argument
 * array that has been silently altered is a defect of the same family as the leak this class
 * prevents -- it makes the provenance record disagree with what actually ran. So {@link
 * #redactArgv} redacts the argument following a flag only when that flag is an exact member of
 * {@link #secretBearingLongFlags()}, a list of long, unambiguous {@code --}-prefixed options. It
 * deliberately does <em>not</em> redact the argument after a single-letter flag such as {@code -p},
 * {@code -k} or {@code -s}. Those letters are ordinary options for real scientific tools -- Comet's
 * own {@code -P} names the parameter file, and {@code -k} is a key, a k-mer length or a keep flag
 * depending on the binary -- so a positional rule over them would blank a file path in the
 * provenance record roughly as often as it would blank a password. The registry is what covers a
 * secret passed after a single-letter flag, and it covers it exactly, which is why this class does
 * not need to guess. <b>A later phase that "fixes" this by adding single-letter flags to the
 * positional rule breaks {@code AC-PRV-03}; the correct fix for a leak found there is to register
 * the value.</b>
 *
 * <p><strong>The marker is {@value #REDACTION_MARKER} and the brackets matter.</strong> The obvious
 * alternative, {@code ***REDACTED***}, cannot be used: work unit 10 of this phase generates {@code
 * provenance.rst}, and in reStructuredText a doubled asterisk opens strong emphasis. A manifest
 * field redacted with asterisks would either render as bold text with the asterisks swallowed or,
 * more often, break the document with an "inline strong start-string without end-string" warning --
 * and this project builds its documentation with {@code sphinx-build -n -W}, where a warning is an
 * error. Square brackets are inert in reStructuredText, in JSON string content, and in a plain log
 * line alike.
 *
 * <p><strong>Redaction is idempotent.</strong> Redacting text that has already been redacted
 * returns it unchanged. That is not a nicety: a value may pass through the redactor on its way into
 * the event log and again on its way into the manifest, and a rule that mangled its own output
 * would produce {@code [[REDACTED]]} in one artefact and {@code [REDACTED]} in another for the same
 * field. Every rule below is written so that the marker itself is a legal match for the thing it
 * replaces.
 *
 * <p><strong>Non-secret content survives byte-identical.</strong> A path, a digest, a version
 * number and a Comet parameter line come through untouched. Over-redaction is not a safe failure
 * here; it corrupts the record that {@code R-PROV-01} requires to be complete.
 *
 * <p><strong>The patterns are linear.</strong> A log line can be megabytes long, so a regular
 * expression with catastrophic backtracking here would be a denial of service on the application's
 * own log viewer. Every quantifier below is either possessive or bounded, no quantifier is nested
 * inside another, and each pattern starts with a lookbehind or a literal prefix so that a failed
 * match at a position is rejected without scanning forward from it.
 *
 * <p><strong>Immutable and thread-safe.</strong> The class is final, its one field is final and
 * holds an immutable {@link SecretRegistry}, and every {@link Pattern} is a static final constant
 * -- {@code Pattern} is documented as safe for concurrent use, and a fresh {@link Matcher}, which
 * is not, is created inside each call and never escapes it. {@link #withSecret(String)} returns a
 * new redactor rather than mutating this one. Any number of threads may share one instance.
 */
public final class SecretRedactor {

    /**
     * What replaces a secret: {@code [REDACTED]}.
     *
     * <p>Fixed, public and constant. See the class documentation for why it is not {@code
     * ***REDACTED***}, and why a later change here would be a documentation-build failure rather
     * than a matter of taste.
     */
    public static final String REDACTION_MARKER = "[REDACTED]";

    /**
     * The most characters {@link #PEM_PRIVATE_KEY} will span between its two delimiters: 16384.
     *
     * <p>Generous for the job -- a 4096-bit RSA private key is about 3.2 KB of PEM, an encrypted
     * OpenSSH key about 2.6 KB -- and small enough that the work an unterminated {@code BEGIN}
     * delimiter can cost is a constant rather than the length of the log. See the pattern's own
     * documentation for why an unbounded body would be a denial of service.
     */
    private static final int PEM_BODY_LIMIT = 16384;

    /**
     * Substrings that make a name secret, in the normalised form {@link #normalise} produces: lower
     * case, with {@code _}, {@code -}, {@code .} and spaces removed.
     *
     * <p>Normalising rather than listing every spelling is what lets one entry cover {@code
     * API_KEY}, {@code api-key}, {@code apiKey} and {@code "api.key"} at once.
     *
     * <p>Two entries are deliberate compromises, and both err towards redacting. {@code auth} also
     * matches {@code AUTHOR}, and {@code session} also matches {@code XDG_SESSION_TYPE}; losing
     * those two values from a provenance record is a smaller harm than missing an {@code
     * AUTH_TOKEN}. One candidate was deliberately left out for the opposite reason: {@code pwd}
     * would match {@code PWD}, the shell's current working directory, which is present in
     * essentially every process environment and is exactly the kind of ordinary value this class
     * must not destroy.
     */
    private static final Set<String> SECRET_NAME_KEYWORDS =
            Set.of(
                    "token",
                    "secret",
                    "password",
                    "passwd",
                    "credential",
                    "apikey",
                    "auth",
                    "session",
                    "cookie",
                    "privatekey",
                    "passphrase",
                    "accesskey",
                    // The two entries below arrived from Phase 03's independently written rule set
                    // when the two were merged into this one.  "signature" is a genuine gap in the
                    // original list: an HMAC or request signature is a credential in every sense
                    // that matters, and a variable named REQUEST_SIGNATURE matched nothing above.
                    "signature",
                    // "limelightkey" is FORWARD-LOOKING and product-specific, which is why it is
                    // called out rather than slipped in.  LIMELIGHT_API_KEY is already covered by
                    // "apikey"; a plain LIMELIGHT_KEY was not covered by anything, and Phase 12 is
                    // the phase that DELIVERS R-SEC-03.  It does not make Phase 12 safe on its own:
                    // Phase 12 must still register the actual credential VALUE with a
                    // SecretRegistry, because a name rule cannot see a token that is passed
                    // positionally or embedded in a URL.
                    "limelightkey");

    /**
     * The long flags whose following argument is redacted, compared in lower case.
     *
     * <p>An explicit list, not a predicate over the flag name, and the difference is the point. The
     * predicate {@link #isSecretName} would also match {@code --author}, {@code --session-timeout}
     * and {@code --token-cache-dir}, each of which takes an ordinary argument that {@code
     * AC-PRV-03} requires to be recorded exactly. A list can be read, argued with and extended by
     * someone who has checked the tool's own documentation; a predicate cannot.
     *
     * <p>Every entry is at least four characters long and begins with {@code --}. See the class
     * documentation for why no single-letter flag appears here and must not be added.
     */
    private static final Set<String> SECRET_BEARING_LONG_FLAGS =
            Set.of(
                    "--password",
                    "--passwd",
                    "--pass",
                    "--passphrase",
                    "--token",
                    "--auth-token",
                    "--access-token",
                    "--session-token",
                    "--api-key",
                    "--apikey",
                    "--api_key",
                    "--secret",
                    "--client-secret",
                    "--secret-key",
                    "--credential",
                    "--credentials",
                    "--private-key");

    /**
     * A PEM-encoded private key, delimiters and all.
     *
     * <p>The one credential carrier the other rules cannot see. A private key does not arrive as a
     * name, a header or a recognisable single token: it arrives as a multi-line block that a user
     * pasted into a field, or that a tool printed when it failed to load one. Everything between
     * the two delimiters is the secret, and so are the delimiters, because a provenance record that
     * said {@code -----BEGIN RSA PRIVATE KEY-----} followed by {@code [REDACTED]} would still be
     * announcing that a key was in play at that point in the run. The whole block becomes one
     * marker.
     *
     * <p>The label is matched generically rather than by a list, so {@code RSA}, {@code EC}, {@code
     * DSA}, {@code OPENSSH}, {@code ENCRYPTED} and the bare {@code PRIVATE KEY} form are all
     * covered, as is any future label of at most two upper-case words. It is case-sensitive,
     * because the delimiters are fixed by RFC 7468 and a lower-case one is not a PEM block. The
     * opening and closing labels are deliberately <em>not</em> required to agree: a mismatched pair
     * is malformed, and redacting malformed key material is the safe reading.
     *
     * <p><strong>{@code -----BEGIN CERTIFICATE-----} is not matched, on purpose.</strong> A
     * certificate is public, it is exactly the kind of thing a provenance record should be able to
     * quote, and destroying it would be over-redaction of the record this class exists to protect.
     *
     * <p><strong>Linearity, which for this rule had to be designed rather than asserted.</strong> A
     * reluctant quantifier between two literal anchors is the natural shape, and the naive form
     * {@code BEGIN...[\s\S]*?...END} is a denial of service on a log that captured a truncated key:
     * every unterminated {@code BEGIN} makes the engine scan to the end of the input. Two things
     * prevent that here.
     *
     * <ul>
     *   <li>The body is <b>bounded</b> at {@value #PEM_BODY_LIMIT} characters. That is several
     *       times the size of any PEM key this application can be handed -- a 4096-bit RSA key is
     *       about 3.2 KB and an encrypted OpenSSH key about 2.6 KB -- so the bound costs nothing
     *       real and caps the work per anchor at a constant.
     *   <li>The body <b>cannot cross a five-dash run</b>: each body character carries a {@code
     *       (?!-----)} guard. So an unterminated {@code BEGIN} followed by another one stops at the
     *       next delimiter instead of scanning forward, which is what keeps a log made entirely of
     *       truncated {@code BEGIN} lines linear rather than quadratic.
     * </ul>
     *
     * <p>The guard is a lookahead over a single character, so no quantifier is nested inside
     * another and the worst case is five character comparisons per body character. Both properties
     * are asserted under a timeout in {@code SecretRedactorTest}, because a performance claim that
     * nothing measures is a comment.
     */
    private static final Pattern PEM_PRIVATE_KEY =
            Pattern.compile(
                    "-----BEGIN (?:[A-Z0-9]{1,16} ){0,2}PRIVATE KEY-----"
                            + "(?:(?!-----)[\\s\\S]){0,"
                            + PEM_BODY_LIMIT
                            + "}?"
                            + "-----END (?:[A-Z0-9]{1,16} ){0,2}PRIVATE KEY-----");

    /**
     * The password inside a credential-bearing URL: {@code scheme://user:password@host/...}.
     *
     * <p>Only the password is replaced. The scheme, the user and the host stay, because a
     * provenance record whose upload step says {@code [REDACTED]} instead of naming the server it
     * uploaded to has lost the fact {@code R-SEC-04} cares most about.
     *
     * <p>Linear: the scheme, the user and the password are each a bounded possessive run over a
     * character class that excludes the delimiter that follows it, so no branch can backtrack into
     * another.
     */
    private static final Pattern CREDENTIAL_URL =
            Pattern.compile(
                    "([A-Za-z][A-Za-z0-9+.\\-]{0,31}+://[^\\s:@/]{0,256}+)"
                            + ":[^\\s@/]{1,4096}+@");

    /**
     * An assignment whose name looks secret, in any of the syntaxes this project's artefacts use.
     *
     * <p>One pattern covers {@code NAME=value}, {@code NAME: value}, {@code "name":"value"}, {@code
     * 'name': 'value'} and, because {@code Authorization} is itself a secret-looking name, {@code
     * Authorization: Bearer <token>} and {@code Proxy-Authorization: Basic <blob>}. The optional
     * scheme group is what keeps the word {@code Bearer} in the output while the token after it
     * goes: an {@code Authorization} header that redacted to {@code Authorization: [REDACTED]}
     * would lose the fact that the request was bearer-authenticated at all.
     *
     * <p>The groups are: (1) the quote around the name, if any; (2) the name; (3) the separator
     * with its surrounding blanks; (4) the authentication scheme and the blanks after it, if any;
     * and then exactly one of (5) a double-quoted value body, (6) a single-quoted value body or (7)
     * a bare value. Group 1 is back-referenced so that {@code "name"} needs a closing quote of the
     * same kind and {@code name} needs none.
     *
     * <p><strong>The name is matched, not judged, here.</strong> Whether a name is secret is
     * decided in Java by {@link #isSecretName}, not by a regular expression over the keyword list.
     * That is partly so that the environment rule and the assignment rule provably share one
     * definition, and partly for speed: a pattern of the form {@code [name chars]*(keyword)[name
     * chars]*} is quadratic on a long line that does not match, which is precisely the input a big
     * log file is made of.
     *
     * <p>Linear: the leading lookbehind rejects any start position in the middle of a name in
     * constant time, the name run is bounded and possessive, and every value branch is possessive
     * over a class that excludes its own terminator.
     */
    private static final Pattern SECRET_ASSIGNMENT =
            Pattern.compile(
                    "(?<![A-Za-z0-9_.\\-])"
                            + "([\"']?)"
                            + "([A-Za-z0-9_.\\-]{1,128}+)"
                            + "\\1"
                            + "([ \\t]*+[:=][ \\t]*+)"
                            + "((?i:bearer|basic|digest)[ \\t]++)?"
                            + "(?:\"([^\"\\r\\n]*+)\"|'([^'\\r\\n]*+)'|([^\\s\\r\\n]++))");

    /**
     * A bare {@code Bearer <token>}, with no header name in front of it.
     *
     * <p>The header form is already covered by {@link #SECRET_ASSIGNMENT}; this catches the token
     * in a log line that quotes only the credential part, which is what a client library's debug
     * output usually does. The word {@code Bearer} and the spacing after it are preserved, so
     * applying the rule to its own output is a no-op.
     */
    private static final Pattern BEARER_TOKEN =
            Pattern.compile("(?<![A-Za-z0-9_.\\-])((?i:bearer)[ \\t]++)([^\\s\\r\\n]++)");

    /**
     * Token formats that identify themselves: GitHub, AWS access key ids, Slack and JWTs.
     *
     * <p>These are the credentials that can be recognised with no context at all, which makes them
     * the last line of defence for a token that reached a log line as a bare word. The list is
     * deliberately short: every entry has a fixed, published prefix, so a false positive would have
     * to be a string that begins {@code ghp_}, {@code AKIA}, {@code xoxb-} or {@code eyJ} and
     * continues in the right alphabet for the right length. Formats without such a prefix -- an AWS
     * <em>secret</em> access key, for instance, which is 40 characters of base64 and looks exactly
     * like a hash -- are not here and cannot be, because a rule broad enough to catch them would
     * redact digests. The registry covers those.
     *
     * <p>Linear: each branch is a literal prefix followed by one possessive run, and the trailing
     * lookahead cannot force backtracking into it.
     */
    private static final Pattern KNOWN_TOKEN_SHAPES =
            Pattern.compile(
                    "(?<![A-Za-z0-9_\\-])"
                            + "(?:"
                            + "github_pat_[A-Za-z0-9_]{20,}+"
                            + "|gh[posur]_[A-Za-z0-9]{20,}+"
                            + "|(?:AKIA|ASIA)[0-9A-Z]{16}"
                            + "|xox[abprs]-[A-Za-z0-9\\-]{10,}+"
                            + "|eyJ[A-Za-z0-9_\\-]{4,}+"
                            + "\\.[A-Za-z0-9_\\-]{2,}+\\.[A-Za-z0-9_\\-]{2,}+"
                            + ")"
                            + "(?![A-Za-z0-9\\-])");

    /** The marker with any replacement metacharacter escaped, so it can be used verbatim. */
    private static final String MARKER_AS_REPLACEMENT = Matcher.quoteReplacement(REDACTION_MARKER);

    /** The literal values this redactor also removes. Immutable; see {@link SecretRegistry}. */
    private final SecretRegistry registry;

    private SecretRedactor(SecretRegistry registry) {
        this.registry = registry;
    }

    /**
     * A redactor with the pattern rules and no registered values.
     *
     * <p>Useful where the application holds no credential, and useful in a test that needs to prove
     * what the pattern rules do on their own. It is not the configuration a run that uploads should
     * use: register the credential.
     *
     * @return a redactor over an empty {@link SecretRegistry}, never {@code null}
     */
    public static SecretRedactor patternsOnly() {
        return new SecretRedactor(SecretRegistry.empty());
    }

    /**
     * A redactor with the pattern rules and the given registered values.
     *
     * @param registry the literal credential values this run is holding
     * @return a new redactor
     * @throws NullPointerException if {@code registry} is {@code null}
     */
    public static SecretRedactor with(SecretRegistry registry) {
        return new SecretRedactor(Objects.requireNonNull(registry, "registry"));
    }

    /**
     * This redactor plus one more registered value.
     *
     * <p>Returns a new redactor; this one is unchanged, so an instance already shared with other
     * threads stays valid.
     *
     * @param value the literal credential value to register
     * @return a redactor that also removes {@code value}
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws SecretTooShortException if {@code value} is shorter than {@link
     *     SecretRegistry#MINIMUM_SECRET_LENGTH}
     * @throws IllegalArgumentException if {@code value} is blank
     */
    public SecretRedactor withSecret(String value) {
        return new SecretRedactor(registry.with(value));
    }

    /**
     * The registered values this redactor removes, as an object that does not print them.
     *
     * @return the registry, never {@code null}
     */
    public SecretRegistry registry() {
        return registry;
    }

    /**
     * The names that make a value secret, normalised.
     *
     * <p>Published so that the rule set is observable rather than merely documented: a test can
     * assert the exact list, and a later reader can see what the environment rule and the
     * assignment rule actually agree on.
     *
     * <p>Copied on return, for the reason given on {@code ToolCommand.argv()}: the copy is free for
     * an already-immutable set, and stating it here makes the guarantee visible to a reader and to
     * SpotBugs alike.
     *
     * @return the normalised keyword set, immutable
     */
    public static Set<String> secretNameKeywords() {
        return Set.copyOf(SECRET_NAME_KEYWORDS);
    }

    /**
     * The long flags after which {@link #redactArgv} redacts the next argument.
     *
     * <p>Published for the same reason as {@link #secretNameKeywords()}, and because this
     * particular list is the one a later phase is most likely to want to change. Read the design
     * constraint in this class's documentation before doing so.
     *
     * @return the flag set, immutable, every entry lower case and {@code --}-prefixed
     */
    public static Set<String> secretBearingLongFlags() {
        return Set.copyOf(SECRET_BEARING_LONG_FLAGS);
    }

    /**
     * Whether a name -- of an environment variable, a manifest field or a configuration key --
     * marks its value as secret.
     *
     * <p>The name is normalised (lower cased, with {@code _}, {@code -}, {@code .} and spaces
     * removed) and then tested for any of {@link #secretNameKeywords()} as a substring. Case is
     * therefore irrelevant: {@code GITHUB_TOKEN}, {@code github_token} and {@code GitHubToken} are
     * all secret-bearing names.
     *
     * @param name the name to judge
     * @return {@code true} if the value belonging to this name must be redacted whatever it looks
     *     like
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public static boolean isSecretName(String name) {
        String normalised = normalise(Objects.requireNonNull(name, "name"));
        for (String keyword : SECRET_NAME_KEYWORDS) {
            if (normalised.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Removes credentials from one piece of free text: a log line, a message, a manifest field.
     *
     * <p>This is the method every other entry point ends in, and the one a writer calls for
     * anything it is about to emit. The rules run in a fixed order: <b>the registry</b>, then PEM
     * private-key blocks, credential URLs, secret-named assignments, bare bearer tokens, well-known
     * token shapes, <b>and the registry again</b>.
     *
     * <p><strong>The literal pass runs at both ends, and the first one is not redundant.</strong>
     * It was added after a measurement, not a hunch. The registry works by literal substring
     * replacement, so it only matches a value that is still intact -- and a pattern rule that fires
     * earlier can rewrite one character inside a registered secret and leave the rest of it
     * standing, at which point the literal no longer matches and the "airtight" half of the rule
     * set silently stops applying. That is not hypothetical: the assignment rule sees a name and an
     * {@code =} inside the base64 body of a PEM private key and rewrites its padding, which was
     * enough to hide 62 characters of key material from a registry pass that ran only at the end.
     * Removing the known literals before any rule can touch them makes the class of failure go away
     * rather than requiring every future rule to be careful. The trailing pass stays as the
     * catch-all for anything the pattern rules leave behind.
     *
     * <p>The PEM rule goes first among the patterns because it is the only one whose match spans
     * lines: run later, its base64 body would already have been chewed on by the rules that look
     * for {@code =} signs and token shapes inside it.
     *
     * @param text the text to clean
     * @return the text with every recognised secret replaced by {@value #REDACTION_MARKER}; the
     *     same string, unchanged, when nothing matched
     * @throws NullPointerException if {@code text} is {@code null}
     */
    public String redactText(String text) {
        Objects.requireNonNull(text, "text");
        String cleaned = registry.redactIn(text);
        cleaned = PEM_PRIVATE_KEY.matcher(cleaned).replaceAll(MARKER_AS_REPLACEMENT);
        cleaned = redactCredentialUrls(cleaned);
        cleaned = redactSecretAssignments(cleaned);
        cleaned = BEARER_TOKEN.matcher(cleaned).replaceAll("$1" + MARKER_AS_REPLACEMENT);
        cleaned = KNOWN_TOKEN_SHAPES.matcher(cleaned).replaceAll(MARKER_AS_REPLACEMENT);
        return registry.redactIn(cleaned);
    }

    /**
     * Removes credentials from a captured process environment, driven by the variable names.
     *
     * <p><strong>The names are never redacted.</strong> {@code R-PROV-04} wants the provenance
     * record to say which variables were set for a run, and "a variable whose name I will not tell
     * you was set to a value I will not tell you" records nothing. The names go through untouched;
     * only values are cleaned.
     *
     * <p>A variable whose name {@link #isSecretName} accepts has its <em>whole</em> value replaced,
     * regardless of what the value looks like -- that is the point of a name-driven rule, and it is
     * what catches a password that happens to look like an ordinary word. Every other value goes
     * through {@link #redactText}, so a credential URL or a bearer token in an innocuously named
     * variable is still caught.
     *
     * <p>Iteration order is preserved, so a caller that sorted the environment by name before
     * calling gets a sorted map back.
     *
     * @param environment the captured environment; names and values must not be {@code null}
     * @return a new immutable map with the same names in the same order and cleaned values
     * @throws NullPointerException if the map is {@code null}, or if any name or value in it is
     *     {@code null} -- the message names the offending variable
     */
    public Map<String, String> redactEnvironment(Map<String, String> environment) {
        Objects.requireNonNull(environment, "environment");
        Map<String, String> redacted = LinkedHashMap.newLinkedHashMap(environment.size());
        for (Map.Entry<String, String> variable : environment.entrySet()) {
            String name = Objects.requireNonNull(variable.getKey(), "an environment variable name");
            String value = variable.getValue();
            Objects.requireNonNull(value, () -> "the value of the environment variable " + name);
            redacted.put(name, isSecretName(name) ? REDACTION_MARKER : redactText(value));
        }
        return Collections.unmodifiableMap(redacted);
    }

    /**
     * Removes credentials from a command's argument array.
     *
     * <p>Three rules, in this order for each element:
     *
     * <ol>
     *   <li>if the previous element was one of {@link #secretBearingLongFlags()}, this element is
     *       the credential and is replaced whole;
     *   <li>otherwise the element goes through {@link #redactText}, which handles the inline form
     *       {@code --password=VALUE} through the assignment rule, and catches a recognisable token
     *       or a registered value wherever it sits;
     *   <li>and the element is then examined to decide whether the <em>next</em> one is a
     *       credential.
     * </ol>
     *
     * <p>The flag itself is never replaced -- it is part of the command, not part of the secret --
     * and no single-letter flag makes the next argument a credential. See the design constraint in
     * this class's documentation, which a later phase must read before changing that.
     *
     * @param argv the argument array, executable first; no element may be {@code null}
     * @return a new immutable list of the same length, in the same order
     * @throws NullPointerException if the list is {@code null}, or if any element is {@code null}
     *     -- the message names the offending index
     */
    public List<String> redactArgv(List<String> argv) {
        Objects.requireNonNull(argv, "argv");
        List<String> redacted = new ArrayList<>(argv.size());
        boolean nextIsCredential = false;
        for (int index = 0; index < argv.size(); index++) {
            int position = index;
            String argument =
                    Objects.requireNonNull(argv.get(index), () -> "argv[" + position + "]");
            if (nextIsCredential) {
                redacted.add(REDACTION_MARKER);
                nextIsCredential = false;
            } else {
                redacted.add(redactText(argument));
                nextIsCredential =
                        SECRET_BEARING_LONG_FLAGS.contains(argument.toLowerCase(Locale.ROOT));
            }
        }
        return List.copyOf(redacted);
    }

    /**
     * Describes the redactor without disclosing anything it holds.
     *
     * @return a description safe to put in a log line or an exception message
     */
    @Override
    public String toString() {
        return "SecretRedactor[" + registry + "]";
    }

    /**
     * Replaces the password in every credential-bearing URL, keeping scheme, user and host.
     *
     * @param text the text to clean
     * @return the cleaned text
     */
    private static String redactCredentialUrls(String text) {
        return CREDENTIAL_URL.matcher(text).replaceAll("$1:" + MARKER_AS_REPLACEMENT + "@");
    }

    /**
     * Replaces the value of every assignment whose name looks secret, leaving the rest alone.
     *
     * <p>Written as an explicit scan rather than {@code replaceAll} for a reason that is easy to
     * miss. {@link #SECRET_ASSIGNMENT} matches assignments with <em>any</em> name, because the
     * secret-name test lives in Java; a non-secret match must therefore be skipped without being
     * consumed, or a query string such as {@code url:https://h/?password=x} would be swallowed
     * whole as the value of the harmless name {@code url} and the {@code password=x} inside it
     * would never be examined. So a rejected match rewinds the scan to just after its separator,
     * which is always ahead of where that match started, so the loop still terminates.
     *
     * @param text the text to clean
     * @return the cleaned text, or the identical string when no secret-named assignment was found
     */
    private static String redactSecretAssignments(String text) {
        Matcher matcher = SECRET_ASSIGNMENT.matcher(text);
        StringBuilder cleaned = null;
        int copiedTo = 0;
        int searchFrom = 0;
        while (matcher.find(searchFrom)) {
            if (isSecretName(matcher.group(2))) {
                if (cleaned == null) {
                    cleaned = new StringBuilder(text.length());
                }
                cleaned.append(text, copiedTo, matcher.start());
                appendRedactedAssignment(cleaned, matcher);
                copiedTo = matcher.end();
                searchFrom = matcher.end();
            } else {
                searchFrom = matcher.end(3);
            }
        }
        if (cleaned == null) {
            return text;
        }
        return cleaned.append(text, copiedTo, text.length()).toString();
    }

    /**
     * Writes one matched assignment back out with its value replaced.
     *
     * <p>The name, its quotes, the separator with its exact spacing, and the authentication scheme
     * are all copied verbatim; only the value becomes the marker, and it keeps whatever quotes it
     * arrived in so that a JSON document stays a JSON document.
     *
     * @param cleaned the output being built
     * @param matcher the matcher, positioned on a secret-named assignment
     */
    private static void appendRedactedAssignment(StringBuilder cleaned, Matcher matcher) {
        cleaned.append(matcher.group(1)).append(matcher.group(2)).append(matcher.group(1));
        cleaned.append(matcher.group(3));
        if (matcher.group(4) != null) {
            cleaned.append(matcher.group(4));
        }
        if (matcher.group(5) != null) {
            cleaned.append('"').append(REDACTION_MARKER).append('"');
        } else if (matcher.group(6) != null) {
            cleaned.append('\'').append(REDACTION_MARKER).append('\'');
        } else {
            cleaned.append(REDACTION_MARKER);
        }
    }

    /**
     * Reduces a name to the form the keyword list is written in: lower case, no separators.
     *
     * <p>{@link Character#toLowerCase(char)} rather than {@link String#toLowerCase()}, which is
     * locale-sensitive: under a Turkish default locale {@code "API_KEY".toLowerCase()} is {@code
     * "apı_key"} with a dotless i, which does not contain {@code apikey}, so an application started
     * on a Turkish machine would stop redacting API keys. This is exactly the class where that must
     * not be left to a default.
     *
     * @param name the raw name
     * @return the normalised name
     */
    private static String normalise(String name) {
        StringBuilder normalised = new StringBuilder(name.length());
        for (int index = 0; index < name.length(); index++) {
            char character = name.charAt(index);
            if (character != '_' && character != '-' && character != '.' && character != ' ') {
                normalised.append(Character.toLowerCase(character));
            }
        }
        return normalised.toString();
    }
}
