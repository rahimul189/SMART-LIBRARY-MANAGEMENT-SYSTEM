package com.smartlibrary.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@DisplayName("Gender - the registration radio buttons")
class GenderTest {

    @Nested
    @DisplayName("fromLabel")
    class FromLabel {

        @ParameterizedTest(name = "\"{0}\" parses to {1}")
        @CsvSource({
                "Male,        MALE",
                "Female,      FEMALE",
                "male,        MALE",
                "female,      FEMALE",
                "MALE,        MALE",
                "FEMALE,      FEMALE",
                "'  Male  ',  MALE",
                "'  female',  FEMALE"
        })
        @DisplayName("the stored label parses, whatever the casing or padding")
        void parsesLabels(String label, Gender expected) {
            assertSame(expected, Gender.fromLabel(label));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "Other", "nonbinary", "M", "mal", "Femalee"})
        @DisplayName("an empty or unknown value maps to null rather than guessing")
        void unknownIsNull(String label) {
            assertNull(Gender.fromLabel(label));
        }

        @ParameterizedTest
        @ValueSource(strings = {"Male", "Female"})
        @DisplayName("trailing and leading spaces still resolve, because the value is trimmed")
        void paddedLabelsResolve(String label) {
            assertSame(Gender.fromLabel(label), Gender.fromLabel("  " + label + "  "));
        }

        @Test
        @DisplayName("a null label maps to null instead of throwing")
        void nullIsSafe() {
            assertNull(Gender.fromLabel(null));
        }
    }

    @Nested
    @DisplayName("labels")
    class Labels {

        @Test
        @DisplayName("each constant knows the text stored in the members table")
        void labelsRoundTrip() {
            for (Gender gender : Gender.values()) {
                assertSame(gender, Gender.fromLabel(gender.label()),
                        gender + " must parse back from its own label");
            }
        }

        @Test
        @DisplayName("the stored text is the one the form shows")
        void labelsAreTheFormText() {
            assertEquals("Male", Gender.MALE.label());
            assertEquals("Female", Gender.FEMALE.label());
        }

        @Test
        @DisplayName("the column permits NULL, which is what an unset radio gives")
        void nullMeansNotChosen() {
            // members.gender is nullable and MemberController stores null when
            // neither radio is selected, so the mapping has to survive it.
            assertNull(Gender.fromLabel(null));
        }
    }

    @Test
    @DisplayName("the two constants are distinct")
    void constantsAreDistinct() {
        assertNotEquals(Gender.MALE, Gender.FEMALE);
        assertNotEquals(Gender.MALE.label(), Gender.FEMALE.label());
    }
}
