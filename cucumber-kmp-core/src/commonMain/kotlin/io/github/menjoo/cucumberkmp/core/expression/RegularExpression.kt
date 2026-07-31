package io.github.menjoo.cucumberkmp.core.expression

/**
 * A step definition written as a raw regular expression rather than a Cucumber Expression.
 *
 * Each top-level capture group becomes one parameter. A group whose source exactly matches a
 * registered parameter type's regexp adopts that type, so `(\d+)` yields an `Int`; anything else
 * yields the matched text.
 *
 * ```kotlin
 * RegularExpression(Regex("^I have (\\d+) cucumbers$"), ParameterTypeRegistry())
 * ```
 *
 * A group that did not participate in the match — an optional group such as `(b )?` — produces an
 * [Argument] whose `value` and `group` are both `null`.
 */
public class RegularExpression(
    override val regexp: Regex,
    private val parameterTypeRegistry: ParameterTypeRegistry,
) : Expression {

    override val source: String get() = regexp.pattern

    private val parameters: List<Parameter> =
        topLevelCapturingGroups(regexp.pattern).map { group ->
            Parameter(group.index, parameterTypeRegistry.lookupByRegexp(group.source))
        }

    override fun match(text: String): List<Argument>? {
        val match = regexp.matchEntire(text) ?: return null
        return parameters.map { parameter ->
            val group = match.groups[parameter.groupIndex]?.value
            Argument(
                value = parameter.parameterType?.convert(group) ?: group,
                group = group,
                parameterTypeName = parameter.parameterType?.name ?: ParameterType.ANONYMOUS_NAME,
            )
        }
    }

    private class Parameter(val groupIndex: Int, val parameterType: ParameterType<*>?)

    override fun toString(): String = "RegularExpression($source)"
}
