package io.github.menjoo.cucumberkmp.verification

import io.github.menjoo.cucumberkmp.core.runner.StepRegistryFactory

/**
 * The KSP-generated registry, reached from common code.
 *
 * KSP has no way to generate into `commonTest` — its only metadata entry point is
 * `kspCommonMainMetadata` — so the generated property exists once per target compilation. This
 * `expect`/`actual` pair is the whole cost of that, and Phase 3's Gradle plugin should emit the
 * `actual` side so users never see it.
 */
internal expect val generatedRegistry: StepRegistryFactory
