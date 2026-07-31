package io.github.menjoo.cucumberkmp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SourceLocationTest {

    @Test
    fun rendersAsClickableReference() {
        assertEquals(
            "calculator.feature:7:3",
            SourceLocation("calculator.feature", line = 7, column = 3).toString(),
        )
    }

    @Test
    fun defaultsToFirstColumn() {
        assertEquals("a.feature:2:1", SourceLocation("a.feature", line = 2).toString())
    }

    @Test
    fun rejectsZeroBasedLine() {
        assertFailsWith<IllegalArgumentException> { SourceLocation("a.feature", line = 0) }
    }

    @Test
    fun allowsColumnZeroForEndOfInput() {
        // Gherkin reports end-of-file errors at column 0, one line past the last line.
        assertEquals(
            "a.feature:5:0",
            SourceLocation("a.feature", line = 5, column = SourceLocation.END_OF_INPUT_COLUMN)
                .toString(),
        )
    }

    @Test
    fun rejectsNegativeColumn() {
        assertFailsWith<IllegalArgumentException> {
            SourceLocation("a.feature", line = 1, column = -1)
        }
    }
}
