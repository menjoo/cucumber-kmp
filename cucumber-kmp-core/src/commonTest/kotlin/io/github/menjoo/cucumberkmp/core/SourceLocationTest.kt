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
    fun rejectsZeroBasedColumn() {
        assertFailsWith<IllegalArgumentException> {
            SourceLocation("a.feature", line = 1, column = 0)
        }
    }
}
