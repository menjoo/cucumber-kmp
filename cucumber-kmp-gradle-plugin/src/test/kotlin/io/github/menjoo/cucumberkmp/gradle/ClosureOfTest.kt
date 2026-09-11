package io.github.menjoo.cucumberkmp.gradle

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The `dependsOn` walk behind [CucumberKmpPlugin]'s decision to skip a test source set that cannot
 * reach `commonTest`.
 *
 * Tested on plain graphs rather than through Gradle: the interesting behaviour is the traversal,
 * and applying the Kotlin Multiplatform plugin inside a test buys very little for what it costs —
 * the same reasoning as [CucumberKmpPluginFunctionalTest].
 */
class ClosureOfTest {

    private fun graph(vararg edges: Pair<String, Set<String>>): (String) -> Set<String> {
        val map = edges.toMap()
        return { node -> map[node].orEmpty() }
    }

    @Test
    fun `includes the starting node`() {
        assertEquals(setOf("androidDeviceTest"), closureOf("androidDeviceTest", graph()))
    }

    @Test
    fun `follows a chain to the end`() {
        val dependsOn = graph(
            "iosSimulatorArm64Test" to setOf("iosTest"),
            "iosTest" to setOf("appleTest"),
            "appleTest" to setOf("nativeTest"),
            "nativeTest" to setOf("commonTest"),
        )

        assertEquals(
            setOf("iosSimulatorArm64Test", "iosTest", "appleTest", "nativeTest", "commonTest"),
            closureOf("iosSimulatorArm64Test", dependsOn),
        )
    }

    @Test
    fun `visits a diamond once`() {
        val dependsOn = graph(
            "leaf" to setOf("left", "right"),
            "left" to setOf("root"),
            "right" to setOf("root"),
        )

        assertEquals(setOf("leaf", "left", "right", "root"), closureOf("leaf", dependsOn))
    }

    @Test
    fun `terminates on a cycle`() {
        // A dependsOn cycle is a broken consumer build. It should surface as their error rather
        // than as a stack overflow here.
        val dependsOn = graph("a" to setOf("b"), "b" to setOf("c"), "c" to setOf("a"))

        assertEquals(setOf("a", "b", "c"), closureOf("a", dependsOn))
    }

    @Test
    fun `an island reaches nothing but itself`() {
        // The motivating case: an Android device-test source set attached to its own source-set
        // tree has no dependsOn parents at all, so commonTest is not in its closure.
        val dependsOn = graph("androidHostTest" to setOf("commonTest"))

        assertEquals(false, "commonTest" in closureOf("androidDeviceTest", dependsOn))
        assertEquals(true, "commonTest" in closureOf("androidHostTest", dependsOn))
    }
}
