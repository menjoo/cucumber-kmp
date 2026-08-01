/**
 * A published Kotlin Multiplatform module: the target matrix plus everything Central requires.
 *
 * Split from `cucumberkmp.kmp-targets` so verification-only modules can take the target matrix
 * without also acquiring publications, and from `cucumberkmp.publishing` so the JVM-only modules
 * get identical POMs, javadoc jars and signatures.
 */

plugins {
    id("cucumberkmp.kmp-targets")
    id("cucumberkmp.publishing")
}
