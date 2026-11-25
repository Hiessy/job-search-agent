package com.hiessy.jobagent.cv;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Expected values are what Python 3.11 prints for the same calls. */
class PyTextTest {

    @Test
    void splitLinesFollowsPython() {
        assertThat(PyText.splitLines("a\r\nb\rc\nd")).containsExactly("a", "b", "c", "d");
        assertThat(PyText.splitLines("a\n")).containsExactly("a");
        assertThat(PyText.splitLines("a\n\nb")).containsExactly("a", "", "b");
        assertThat(PyText.splitLines("page1\fpage2")).containsExactly("page1", "page2");
        assertThat(PyText.splitLines("")).isEmpty();
    }

    @Test
    void stripAndNormTreatNonBreakingSpacesAsSpace() {
        assertThat(PyText.strip("  Java \t")).isEqualTo("Java");
        assertThat(PyText.norm("Spring  Boot\n  JAVA")).isEqualTo("spring boot java");
    }

    @Test
    void roundUsesBankersRounding() {
        assertThat(PyText.round(12.5)).isEqualTo(12);
        assertThat(PyText.round(13.5)).isEqualTo(14);
        assertThat(PyText.round(62.4)).isEqualTo(62);
    }

    @Test
    void isUpperNeedsACasedCharacterAndNoLowercase() {
        assertThat(PyText.isUpper("SKILLS")).isTrue();
        assertThat(PyText.isUpper("SKILLS & TOOLS 2")).isTrue();
        assertThat(PyText.isUpper("Skills")).isFalse();
        assertThat(PyText.isUpper("2021")).isFalse();
    }

    @Test
    void lengthAndHeadCountCodePoints() {
        String withEmoji = "ab😀cd";
        assertThat(PyText.len(withEmoji)).isEqualTo(5);
        assertThat(PyText.head(withEmoji, 3)).isEqualTo("ab😀");
    }

    @Test
    void reprQuotesLikePython() {
        assertThat(PyText.repr(List.of("[N]", "it's"))).isEqualTo("['[N]', \"it's\"]");
    }
}
