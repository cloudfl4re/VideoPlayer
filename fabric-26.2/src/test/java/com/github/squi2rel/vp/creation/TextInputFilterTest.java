package com.github.squi2rel.vp.creation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TextInputFilterTest {
    @Test
    void acceptsInsertionWhenCandidateMatchesFilter() {
        assertTrue(TextInputFilter.accepts(
                value -> value.chars().allMatch(Character::isDigit),
                "12",
                2,
                "",
                "3"
        ));
    }

    @Test
    void rejectsInsertionWhenCandidateDoesNotMatchFilter() {
        assertFalse(TextInputFilter.accepts(
                value -> value.chars().allMatch(Character::isDigit),
                "12",
                2,
                "",
                "a"
        ));
    }

    @Test
    void validatesCandidateAfterReplacingSelection() {
        assertTrue(TextInputFilter.accepts(
                value -> value.equals("a9d"),
                "abcd",
                1,
                "bc",
                "9"
        ));
    }
}
