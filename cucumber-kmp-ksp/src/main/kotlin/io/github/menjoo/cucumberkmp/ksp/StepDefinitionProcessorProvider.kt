package io.github.menjoo.cucumberkmp.ksp

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/**
 * Entry point KSP discovers via `META-INF/services`.
 *
 * Supported processor options, set with `ksp { arg(...) }`:
 *
 * - `cucumberkmp.generatedPackage` — package for the generated registry. Defaults to
 *   `io.github.menjoo.cucumberkmp.generated`.
 */
public class StepDefinitionProcessorProvider : SymbolProcessorProvider {

    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        StepDefinitionProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
            options = environment.options,
        )
}
