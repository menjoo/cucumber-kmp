package io.github.menjoo.cucumberkmp.core.gherkin

import io.github.menjoo.cucumberkmp.core.SourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The conversions a step definition uses to read a table, mirroring cucumber-jvm's
 * `io.cucumber.datatable.DataTable`.
 */
class DataTableConversionsTest {

    private val path = "sizes.feature"

    private fun table(vararg rows: List<String>): DataTable = DataTable(
        location = SourceLocation(path, line = 1),
        rows = rows.mapIndexed { rowIndex, cells ->
            TableRow(
                location = SourceLocation(path, line = rowIndex + 1),
                cells = cells.mapIndexed { cellIndex, value ->
                    TableCell(SourceLocation(path, rowIndex + 1, cellIndex + 1), value)
                },
            )
        },
    )

    @Test
    fun `asList flattens a single column`() {
        assertEquals(listOf("S", "M", "L"), table(listOf("S"), listOf("M"), listOf("L")).asList())
    }

    @Test
    fun `asList reads a wider table row by row`() {
        assertEquals(
            listOf("a", "b", "c", "d"),
            table(listOf("a", "b"), listOf("c", "d")).asList(),
        )
    }

    @Test
    fun `asLists keeps the row structure and the header`() {
        assertEquals(
            listOf(listOf("name", "size"), listOf("shirt", "M")),
            table(listOf("name", "size"), listOf("shirt", "M")).asLists(),
        )
    }

    @Test
    fun `asMap pairs the two columns with no header row`() {
        assertEquals(
            mapOf("enabled" to "true", "timeout" to "30"),
            table(listOf("enabled", "true"), listOf("timeout", "30")).asMap(),
        )
    }

    @Test
    fun `asMap rejects a table that is not two columns wide`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            table(listOf("a", "b", "c")).asMap()
        }
        assertTrue("3 wide" in failure.message.orEmpty(), failure.message.orEmpty())
        // The message should point at the conversion that does fit.
        assertTrue("asMaps()" in failure.message.orEmpty(), failure.message.orEmpty())
    }

    @Test
    fun `asMap rejects a repeated key rather than dropping a row`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            table(listOf("size", "M"), listOf("size", "L")).asMap()
        }
        assertTrue("'size'" in failure.message.orEmpty(), failure.message.orEmpty())
    }

    @Test
    fun `asMaps keys every row by the header`() {
        assertEquals(
            listOf(
                mapOf("name" to "shirt", "size" to "M"),
                mapOf("name" to "coat", "size" to "L"),
            ),
            table(
                listOf("name", "size"),
                listOf("shirt", "M"),
                listOf("coat", "L"),
            ).asMaps(),
        )
    }

    @Test
    fun `asMaps rejects a repeated header cell`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            table(listOf("size", "size"), listOf("M", "L")).asMaps()
        }
        assertTrue("'size'" in failure.message.orEmpty(), failure.message.orEmpty())
    }

    @Test
    fun `a header on its own has no rows`() {
        assertEquals(emptyList(), table(listOf("name", "size")).asMaps())
    }

    @Test
    fun `an empty table converts to empty everything`() {
        val empty = table()
        assertEquals(emptyList(), empty.asList())
        assertEquals(emptyList(), empty.asLists())
        assertEquals(emptyMap(), empty.asMap())
        assertEquals(emptyList(), empty.asMaps())
    }
}
