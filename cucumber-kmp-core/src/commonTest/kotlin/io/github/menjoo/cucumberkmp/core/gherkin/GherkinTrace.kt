package io.github.menjoo.cucumberkmp.core.gherkin

import io.github.menjoo.cucumberkmp.core.SourceLocation

/**
 * Renders a [GherkinDocument] as a canonical line-based trace.
 *
 * This is the comparison format for the upstream conformance corpus. We compare traces rather
 * than upstream's `.ast.ndjson` directly because our AST deliberately differs in shape — sealed
 * interfaces instead of records with three nullable fields, no synthetic `id`s — while the
 * semantics the trace captures must match exactly.
 *
 * `tools/update-gherkin-corpus.py` produces the expected side of the comparison in the same
 * format. **The two implementations must stay in lockstep**; changing one without the other turns
 * the whole corpus red.
 */
// trimEnd matches the generator, which joins lines without a trailing newline.
internal fun GherkinDocument.toTrace(): String = buildString {
    appendLine("document")
    for (comment in comments) {
        line(1, "comment ${comment.location.at()} ${comment.text.esc()}")
    }
    val feature = feature ?: return@buildString
    line(
        1,
        "feature ${feature.location.at()} language=${feature.language} " +
            "keyword=${feature.keyword.esc()} name=${feature.name.esc()}",
    )
    tags(feature.tags, 2)
    line(2, "description ${feature.description.esc()}")
    children(feature.children, 2)
}.trimEnd('\n')

private fun StringBuilder.children(children: List<Any>, depth: Int) {
    for (child in children) {
        when (child) {
            is Background -> background(child, depth)
            is Scenario -> scenario(child, depth)
            is Rule -> rule(child, depth)
        }
    }
}

private fun StringBuilder.background(background: Background, depth: Int) {
    line(
        depth,
        "background ${background.location.at()} keyword=${background.keyword.esc()} " +
            "name=${background.name.esc()}",
    )
    line(depth + 1, "description ${background.description.esc()}")
    steps(background.steps, depth + 1)
}

private fun StringBuilder.scenario(scenario: Scenario, depth: Int) {
    line(
        depth,
        "scenario ${scenario.location.at()} keyword=${scenario.keyword.esc()} " +
            "name=${scenario.name.esc()}",
    )
    tags(scenario.tags, depth + 1)
    line(depth + 1, "description ${scenario.description.esc()}")
    steps(scenario.steps, depth + 1)
    examples(scenario.examples, depth + 1)
}

private fun StringBuilder.rule(rule: Rule, depth: Int) {
    line(
        depth,
        "rule ${rule.location.at()} keyword=${rule.keyword.esc()} name=${rule.name.esc()}",
    )
    tags(rule.tags, depth + 1)
    line(depth + 1, "description ${rule.description.esc()}")
    children(rule.children, depth + 1)
}

private fun StringBuilder.steps(steps: List<Step>, depth: Int) {
    for (step in steps) {
        line(
            depth,
            "step ${step.location.at()} keyword=${step.keyword.esc()} " +
                "type=${step.keywordType} text=${step.text.esc()}",
        )
        step.docString?.let { docString ->
            line(
                depth + 1,
                "docstring ${docString.location.at()} delimiter=${docString.delimiter.esc()} " +
                    "mediaType=${docString.mediaType?.esc() ?: "-"} " +
                    "content=${docString.content.esc()}",
            )
        }
        step.dataTable?.let { table ->
            line(depth + 1, "datatable ${table.location.at()}")
            rows(table.rows, depth + 2)
        }
    }
}

private fun StringBuilder.examples(examples: List<Examples>, depth: Int) {
    for (example in examples) {
        line(
            depth,
            "examples ${example.location.at()} keyword=${example.keyword.esc()} " +
                "name=${example.name.esc()}",
        )
        tags(example.tags, depth + 1)
        line(depth + 1, "description ${example.description.esc()}")
        example.tableHeader?.let { header ->
            line(depth + 1, "header")
            rows(listOf(header), depth + 2)
        }
        line(depth + 1, "body")
        rows(example.tableBody, depth + 2)
    }
}

private fun StringBuilder.rows(rows: List<TableRow>, depth: Int) {
    for (row in rows) {
        line(depth, "row ${row.location.at()}")
        for (cell in row.cells) {
            line(depth + 1, "cell ${cell.location.at()} ${cell.value.esc()}")
        }
    }
}

private fun StringBuilder.tags(tags: List<Tag>, depth: Int) {
    for (tag in tags) {
        line(depth, "tag ${tag.location.at()} ${tag.name.esc()}")
    }
}

private fun StringBuilder.line(depth: Int, text: String) {
    append("  ".repeat(depth))
    append(text)
    append('\n')
}

/**
 * Canonical trace of compiled pickles, matching what `tools/update-gherkin-corpus.py` emits.
 *
 * Upstream's synthetic `id`/`astNodeIds` are omitted: they are its own bookkeeping, not semantics
 * our compiler needs to reproduce.
 */
internal fun List<Pickle>.toTrace(): String = buildString {
    for (pickle in this@toTrace) {
        line(0, "pickle ${pickle.location.at()} language=${pickle.language} name=${pickle.name.esc()}")
        for (tag in pickle.tags) {
            line(1, "tag ${tag.esc()}")
        }
        for (step in pickle.steps) {
            // Step locations are omitted: upstream's pickle steps have none, referring back to
            // AST nodes by synthetic id instead.
            line(1, "step type=${step.type} text=${step.text.esc()}")
            step.docString?.let {
                line(2, "docstring mediaType=${it.mediaType?.esc() ?: "-"} content=${it.content.esc()}")
            }
            step.dataTable?.let { table ->
                line(2, "datatable")
                for (row in table.rows) {
                    line(3, "row " + row.cells.joinToString(" | ") { it.value.esc() })
                }
            }
        }
    }
}.trimEnd('\n')

private fun SourceLocation.at(): String = "$line:$column"

private fun String.esc(): String =
    replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
