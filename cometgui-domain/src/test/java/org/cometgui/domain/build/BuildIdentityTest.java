package org.cometgui.domain.build;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link BuildIdentity}.
 *
 * <p>Every test asserts a value or a message. "Did not throw" is not an assertion here: this class
 * is one of the ones phase 01 unit 3 will point PIT at, so each branch has a test that changes its
 * answer when the branch changes.</p>
 */
class BuildIdentityTest {

    private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";
    private static final Instant BUILT_AT = Instant.parse("2026-08-29T12:34:56Z");

    private static Properties propertiesOf(String version, String commit, String timestamp) {
        Properties properties = new Properties();
        if (version != null) {
            properties.setProperty(BuildIdentity.VERSION_KEY, version);
        }
        if (commit != null) {
            properties.setProperty(BuildIdentity.COMMIT_KEY, commit);
        }
        if (timestamp != null) {
            properties.setProperty(BuildIdentity.TIMESTAMP_KEY, timestamp);
        }
        return properties;
    }

    @Nested
    @DisplayName("of(..)")
    class Factory {

        @Test
        @DisplayName("keeps the three values it was given")
        void keepsItsValues() {
            BuildIdentity identity = BuildIdentity.of("0.1.0-SNAPSHOT", COMMIT, BUILT_AT);

            assertAll(
                    () -> assertEquals("0.1.0-SNAPSHOT", identity.version()),
                    () -> assertEquals(COMMIT, identity.commitId()),
                    () -> assertEquals(BUILT_AT, identity.buildTimestamp()),
                    () -> assertTrue(identity.isCommitKnown(), "a 40-hex commit id is a known commit"));
        }

        @Test
        @DisplayName("strips surrounding whitespace from the version")
        void stripsTheVersion() {
            assertEquals("0.1.0", BuildIdentity.of("  0.1.0\t", COMMIT, BUILT_AT).version());
        }

        @ParameterizedTest(name = "version=[{0}]")
        @NullAndEmptySource
        @ValueSource(strings = {" ", "\t", "\n", "   "})
        @DisplayName("rejects a missing or blank version, naming the problem")
        void rejectsBlankVersion(String version) {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> BuildIdentity.of(version, COMMIT, BUILT_AT));

            assertEquals("build version must not be blank", thrown.getMessage());
        }

        @Test
        @DisplayName("accepts the literal \"unknown\" commit and reports the commit as not known")
        void acceptsUnknownCommit() {
            BuildIdentity identity = BuildIdentity.of("0.1.0", BuildIdentity.UNKNOWN_COMMIT, BUILT_AT);

            assertAll(
                    () -> assertEquals("unknown", identity.commitId()),
                    () -> assertFalse(identity.isCommitKnown(), "\"unknown\" is not a known commit"));
        }

        @ParameterizedTest(name = "commit=[{0}]")
        @NullAndEmptySource
        @ValueSource(strings = {
            "0123456789abcdef0123456789abcdef0123456", // 39 characters, one short
            "0123456789abcdef0123456789abcdef012345678", // 41 characters, one long
            "0123456789ABCDEF0123456789abcdef01234567", // uppercase hex is not accepted
            "0123456789abcdef0123456789abcdef0123456g", // 'g' is not hex
            " 0123456789abcdef0123456789abcdef01234567", // not stripped, so not 40 characters
            "UNKNOWN", // the literal is lowercase
            "unknown-commit"
        })
        @DisplayName("rejects any commit id that is neither 40 lowercase hex characters nor \"unknown\"")
        void rejectsMalformedCommit(String commitId) {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> BuildIdentity.of("0.1.0", commitId, BUILT_AT));

            assertEquals(
                    "build commit id must be 40 lowercase hex characters or \"unknown\", but was: "
                            + commitId,
                    thrown.getMessage());
        }

        @Test
        @DisplayName("rejects a null timestamp, naming the parameter")
        void rejectsNullTimestamp() {
            NullPointerException thrown = assertThrows(NullPointerException.class,
                    () -> BuildIdentity.of("0.1.0", COMMIT, null));

            assertEquals("buildTimestamp", thrown.getMessage());
        }
    }

    @Nested
    @DisplayName("fromProperties(..)")
    class FromProperties {

        @Test
        @DisplayName("reads all three properties and parses the ISO-8601 instant")
        void readsAllThree() {
            BuildIdentity identity = BuildIdentity.fromProperties(
                    propertiesOf("1.2.3", COMMIT, "2026-08-29T12:34:56Z"));

            assertAll(
                    () -> assertEquals("1.2.3", identity.version()),
                    () -> assertEquals(COMMIT, identity.commitId()),
                    () -> assertEquals(BUILT_AT, identity.buildTimestamp()));
        }

        @Test
        @DisplayName("strips surrounding whitespace before parsing the timestamp")
        void stripsTheTimestamp() {
            assertEquals(BUILT_AT,
                    BuildIdentity.fromProperties(propertiesOf("1.2.3", COMMIT, "  2026-08-29T12:34:56Z  "))
                            .buildTimestamp());
        }

        @Test
        @DisplayName("normalises an offset timestamp to the same instant")
        void acceptsAnOffsetTimestamp() {
            assertEquals(Instant.parse("2026-08-29T12:34:56Z"),
                    BuildIdentity.fromProperties(propertiesOf("1.2.3", COMMIT, "2026-08-29T14:34:56+02:00"))
                            .buildTimestamp());
        }

        @ParameterizedTest(name = "timestamp=[{0}]")
        @NullAndEmptySource
        @ValueSource(strings = {" ", "\t"})
        @DisplayName("rejects a missing or blank timestamp, naming the key")
        void rejectsMissingTimestamp(String timestamp) {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> BuildIdentity.fromProperties(propertiesOf("1.2.3", COMMIT, timestamp)));

            assertEquals("missing build property: cometgui.buildTimestamp", thrown.getMessage());
        }

        @ParameterizedTest(name = "timestamp=[{0}]")
        @ValueSource(strings = {
            "2026-08-29", // a date is not an instant
            "2026-08-29T12:34:56", // no zone
            "yesterday",
            "1756470896" // epoch seconds are not ISO-8601
        })
        @DisplayName("rejects a timestamp that is not an ISO-8601 instant, quoting the value")
        void rejectsMalformedTimestamp(String timestamp) {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> BuildIdentity.fromProperties(propertiesOf("1.2.3", COMMIT, timestamp)));

            assertAll(
                    () -> assertEquals(
                            "build property cometgui.buildTimestamp is not an ISO-8601 instant: "
                                    + timestamp,
                            thrown.getMessage()),
                    () -> assertTrue(thrown.getCause() instanceof java.time.format.DateTimeParseException,
                            "the parse failure is kept as the cause, but was: " + thrown.getCause()));
        }

        @Test
        @DisplayName("rejects a missing version property with the same message as of(..)")
        void rejectsMissingVersion() {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> BuildIdentity.fromProperties(propertiesOf(null, COMMIT, "2026-08-29T12:34:56Z")));

            assertEquals("build version must not be blank", thrown.getMessage());
        }

        @Test
        @DisplayName("rejects a missing commit property rather than defaulting it to \"unknown\"")
        void rejectsMissingCommit() {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> BuildIdentity.fromProperties(propertiesOf("1.2.3", null, "2026-08-29T12:34:56Z")));

            assertEquals(
                    "build commit id must be 40 lowercase hex characters or \"unknown\", but was: null",
                    thrown.getMessage());
        }

        @Test
        @DisplayName("rejects null properties, naming the parameter")
        void rejectsNullProperties() {
            NullPointerException thrown =
                    assertThrows(NullPointerException.class, () -> BuildIdentity.fromProperties(null));

            assertEquals("properties", thrown.getMessage());
        }
    }

    @Nested
    @DisplayName("value semantics")
    class ValueSemantics {

        private final BuildIdentity identity = BuildIdentity.of("0.1.0", COMMIT, BUILT_AT);

        @Test
        @DisplayName("two identities with the same three values are equal and share a hash code")
        void equalWhenAllThreeMatch() {
            BuildIdentity same = BuildIdentity.of("0.1.0", COMMIT, Instant.parse("2026-08-29T12:34:56Z"));

            assertAll(
                    () -> assertEquals(identity, same),
                    () -> assertEquals(same, identity),
                    () -> assertEquals(identity.hashCode(), same.hashCode()),
                    () -> assertEquals(identity, identity));
        }

        @Test
        @DisplayName("a difference in any single field makes them unequal")
        void unequalWhenOneFieldDiffers() {
            String otherCommit = "89abcdef0123456789abcdef0123456789abcdef";

            assertAll(
                    () -> assertNotEquals(identity, BuildIdentity.of("0.1.1", COMMIT, BUILT_AT)),
                    () -> assertNotEquals(identity, BuildIdentity.of("0.1.0", otherCommit, BUILT_AT)),
                    () -> assertNotEquals(identity,
                            BuildIdentity.of("0.1.0", COMMIT, BUILT_AT.plusSeconds(1))));
        }

        @Test
        @DisplayName("is not equal to null or to a different type")
        void unequalToNullAndOtherTypes() {
            assertAll(
                    () -> assertNotEquals(null, identity),
                    () -> assertNotEquals("0.1.0", identity),
                    () -> assertFalse(identity.equals(new Object())));
        }

        @Test
        @DisplayName("toString shows the version, the commit and the build instant")
        void toStringShowsEveryField() {
            assertEquals(
                    "BuildIdentity[version=0.1.0, commit=" + COMMIT + ", built=2026-08-29T12:34:56Z]",
                    identity.toString());
        }
    }
}
