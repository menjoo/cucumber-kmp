package io.github.menjoo.cucumberkmp.gradle

import org.gradle.api.DefaultTask
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals

class LintTaskWiringTest {

    @Test
    fun `matches Android lint tasks for a compilation`() {
        assertEquals(true, "generateAndroidHostTestLintModel".isLintTaskFor("AndroidHostTest"))
        assertEquals(true, "generateAndroidHostTestLintVitalModel".isLintTaskFor("AndroidHostTest"))
        assertEquals(true, "lintAnalyzeAndroidHostTest".isLintTaskFor("AndroidHostTest"))
        assertEquals(true, "lintVitalAnalyzeAndroidHostTest".isLintTaskFor("AndroidHostTest"))
        assertEquals(true, "updateAndroidHostTestLintBaseline".isLintTaskFor("AndroidHostTest"))
        assertEquals(false, "kspAndroidHostTest".isLintTaskFor("AndroidHostTest"))
        assertEquals(false, "lintAnalyzeAndroidDeviceTest".isLintTaskFor("AndroidHostTest"))
        assertEquals(false, "cleanupAndroidHostTestLintOutputs".isLintTaskFor("AndroidHostTest"))
    }

    @Test
    fun `wires only the matching lint tasks when step definitions are visible`() {
        val project = ProjectBuilder.builder().build()
        val generate = project.tasks.register("generateCucumberTests")
        project.tasks.register("kspAndroidHostTest")
        val lintModel = project.tasks.register("generateAndroidHostTestLintModel", DefaultTask::class.java)
        val lintVitalModel =
            project.tasks.register("generateAndroidHostTestLintVitalModel", DefaultTask::class.java)
        val lintAnalyze = project.tasks.register("lintAnalyzeAndroidHostTest", DefaultTask::class.java)
        val lintVital = project.tasks.register("lintVitalAnalyzeAndroidHostTest", DefaultTask::class.java)
        val lintBaseline = project.tasks.register("updateAndroidHostTestLintBaseline", DefaultTask::class.java)
        val otherLint = project.tasks.register("lintAnalyzeAndroidDeviceTest", DefaultTask::class.java)

        project.wireLintTasksToGeneratedSources(
            compilationTaskSuffix = "AndroidHostTest",
            canSeeStepDefinitions = { true },
            generate = generate,
            generatedRegistryTaskName = { "kspAndroidHostTest" },
        )

        assertEquals(
            setOf("generateCucumberTests", "kspAndroidHostTest"),
            lintModel.get().taskDependencies.getDependencies(lintModel.get()).map { it.name }.toSet(),
        )
        assertEquals(
            setOf("generateCucumberTests", "kspAndroidHostTest"),
            lintVitalModel.get().taskDependencies
                .getDependencies(lintVitalModel.get())
                .map { it.name }
                .toSet(),
        )
        assertEquals(
            setOf("generateCucumberTests", "kspAndroidHostTest"),
            lintAnalyze.get().taskDependencies.getDependencies(lintAnalyze.get()).map { it.name }.toSet(),
        )
        assertEquals(
            setOf("generateCucumberTests", "kspAndroidHostTest"),
            lintVital.get().taskDependencies.getDependencies(lintVital.get()).map { it.name }.toSet(),
        )
        assertEquals(
            setOf("generateCucumberTests", "kspAndroidHostTest"),
            lintBaseline.get().taskDependencies.getDependencies(lintBaseline.get()).map { it.name }.toSet(),
        )
        assertEquals(
            emptySet(),
            otherLint.get().taskDependencies.getDependencies(otherLint.get()).map { it.name }.toSet(),
        )
    }

    @Test
    fun `leaves lint tasks alone when the compilation cannot see the step definitions`() {
        val project = ProjectBuilder.builder().build()
        val generate = project.tasks.register("generateCucumberTests")
        project.tasks.register("kspAndroidHostTest")
        val lintTask = project.tasks.register("lintAnalyzeAndroidHostTest", DefaultTask::class.java)

        project.wireLintTasksToGeneratedSources(
            compilationTaskSuffix = "AndroidHostTest",
            canSeeStepDefinitions = { false },
            generate = generate,
            generatedRegistryTaskName = { "kspAndroidHostTest" },
        )

        assertEquals(
            emptySet(),
            lintTask.get().taskDependencies.getDependencies(lintTask.get()).map { it.name }.toSet(),
        )
    }
}
