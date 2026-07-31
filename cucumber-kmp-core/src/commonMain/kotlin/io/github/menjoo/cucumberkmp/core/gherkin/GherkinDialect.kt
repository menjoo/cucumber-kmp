package io.github.menjoo.cucumberkmp.core.gherkin

/**
 * The set of Gherkin keywords for one language.
 *
 * Instances come from [GherkinDialects], which is generated from upstream's
 * `gherkin-languages.json` — see `tools/update-gherkin-dialects.py`. Keyword lists are ordered
 * as upstream orders them, and step keywords keep their trailing space (`"Given "`), because
 * that space is part of what the parser strips before the step text begins.
 *
 * See https://cucumber.io/docs/gherkin/languages/
 */
public class GherkinDialect internal constructor(
    public val language: String,
    public val name: String,
    public val nativeName: String,
    public val featureKeywords: List<String>,
    public val ruleKeywords: List<String>,
    public val backgroundKeywords: List<String>,
    public val scenarioKeywords: List<String>,
    public val scenarioOutlineKeywords: List<String>,
    public val examplesKeywords: List<String>,
    public val givenKeywords: List<String>,
    public val whenKeywords: List<String>,
    public val thenKeywords: List<String>,
    public val andKeywords: List<String>,
    public val butKeywords: List<String>,
) {

    /**
     * Every step keyword in this dialect, longest first.
     *
     * Longest-first matters: in a dialect where one keyword is a prefix of another, matching the
     * shorter one first would leave the remainder in the step text.
     */
    public val stepKeywords: List<String> =
        (givenKeywords + whenKeywords + thenKeywords + andKeywords + butKeywords)
            .distinct()
            .sortedByDescending { it.length }

    private val keywordTypes: Map<String, StepKeywordType> = buildMap {
        fun assign(keywords: List<String>, type: StepKeywordType) {
            for (keyword in keywords) {
                // A keyword shared between categories cannot be classified lexically. The `*`
                // wildcard is in every category, so it lands on UNKNOWN — matching upstream.
                put(keyword, if (containsKey(keyword) && get(keyword) != type) StepKeywordType.UNKNOWN else type)
            }
        }
        assign(givenKeywords, StepKeywordType.CONTEXT)
        assign(whenKeywords, StepKeywordType.ACTION)
        assign(thenKeywords, StepKeywordType.OUTCOME)
        assign(andKeywords, StepKeywordType.CONJUNCTION)
        assign(butKeywords, StepKeywordType.CONJUNCTION)
    }

    /** Classifies a step keyword, or [StepKeywordType.UNKNOWN] if it is ambiguous or unknown. */
    public fun stepKeywordType(keyword: String): StepKeywordType =
        keywordTypes[keyword] ?: StepKeywordType.UNKNOWN

    override fun toString(): String = "GherkinDialect($language, $name)"
}

/** Lookup for the generated dialect table. */
public object GherkinDialects {

    /** The language tag assumed when a `.feature` file has no `# language:` header. */
    public const val DEFAULT_LANGUAGE: String = "en"

    /** All supported language tags, sorted. */
    public val supportedLanguages: List<String> get() = GHERKIN_DIALECTS.keys.sorted()

    /** Returns the dialect for [language], or `null` if upstream does not define it. */
    public fun forLanguageOrNull(language: String): GherkinDialect? = GHERKIN_DIALECTS[language]

    /**
     * Returns the dialect for [language].
     *
     * @throws IllegalArgumentException if the language is not one of [supportedLanguages].
     */
    public fun forLanguage(language: String): GherkinDialect =
        forLanguageOrNull(language)
            ?: throw IllegalArgumentException("Language not supported: $language")

    /** The `en` dialect, used when a document declares no language. */
    public val default: GherkinDialect get() = forLanguage(DEFAULT_LANGUAGE)
}
