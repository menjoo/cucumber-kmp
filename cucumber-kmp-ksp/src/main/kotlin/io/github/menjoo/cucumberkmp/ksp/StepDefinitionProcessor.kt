package io.github.menjoo.cucumberkmp.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.FileLocation
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import io.github.menjoo.cucumberkmp.core.expression.CucumberExpressionParser
import io.github.menjoo.cucumberkmp.core.expression.ExpressionNode
import io.github.menjoo.cucumberkmp.core.expression.ExpressionNodeType
import io.github.menjoo.cucumberkmp.core.tag.TagExpressionParser

/**
 * Turns `@Given`/`@When`/`@Then` annotations into a statically generated `StepRegistry`.
 *
 * This is the step that removes Cucumber's dependency on runtime reflection: instead of scanning a
 * classpath and calling `Method.invoke`, the processor emits explicit, typed calls. See
 * ARCHITECTURE.md §4b.
 *
 * The processor validates far more than it strictly must, because everything it catches here is a
 * build error with a source location rather than a test failure someone has to debug:
 *
 * - the expression must parse;
 * - its placeholder count must match the function's parameter count;
 * - each placeholder's type must match the declared parameter type;
 * - the parameter type must exist;
 * - no two definitions may share an expression;
 * - hook tag expressions must parse;
 * - step functions must be callable (not private, on an instantiable receiver).
 */
internal class StepDefinitionProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    options: Map<String, String>,
) : SymbolProcessor {

    private val generatedPackage =
        options["cucumberkmp.generatedPackage"] ?: "io.github.menjoo.cucumberkmp.generated"

    private var emitted = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (emitted) return emptyList()

        val steps = mutableListOf<StepBinding>()
        val hooks = mutableListOf<HookBinding>()

        for (function in annotatedFunctions(resolver, STEP_ANNOTATIONS)) {
            val expressions = function.annotations
                .filter { it.shortName.asString() in STEP_ANNOTATION_NAMES }
                .mapNotNull { it.stringArgument("value") }
                // `@Given("x") @Then("x")` on one function is redundant, not ambiguous: Gherkin
                // does not match on the keyword, so both annotations mean the same definition.
                .distinct()
                .toList()
            for (expression in expressions) {
                bindStep(function, expression)?.let { steps += it }
            }
        }

        for (function in annotatedFunctions(resolver, HOOK_ANNOTATIONS)) {
            for (annotation in function.annotations.filter { it.shortName.asString() in HOOK_ANNOTATION_NAMES }) {
                bindHook(function, annotation)?.let { hooks += it }
            }
        }

        reportDuplicateExpressions(steps)

        // Nothing annotated: the consumer may be using the steps { } DSL instead, which is a
        // supported choice, not an omission. Emitting an empty registry would shadow it.
        if (steps.isEmpty() && hooks.isEmpty()) return emptyList()

        emit(steps, hooks)
        emitted = true
        return emptyList()
    }

    private fun annotatedFunctions(resolver: Resolver, annotations: List<String>): List<KSFunctionDeclaration> =
        annotations
            .flatMap { resolver.getSymbolsWithAnnotation(it).toList() }
            .filterIsInstance<KSFunctionDeclaration>()
            .distinct()

    // ------------------------------------------------------------------ binding

    private fun bindStep(function: KSFunctionDeclaration, expression: String): StepBinding? {
        val receiver = resolveReceiver(function) ?: return null
        if (!isCallable(function)) return null

        val isRegex = expression.startsWith("^") && expression.endsWith("$")
        val parameterTypes = function.parameters.map { it.type.toTypeName() }

        // A trailing DataTable or DocString parameter receives the step's attachment rather than
        // one of the expression's captures, so it sits outside the placeholder checks below.
        val hasAttachment = parameterTypes.lastOrNull()
            ?.copy(nullable = false)?.toString() in ATTACHMENT_TYPES
        val capturedTypes = if (hasAttachment) parameterTypes.dropLast(1) else parameterTypes

        if (isRegex) {
            // A raw regular expression opts out of placeholder checking: its capture groups carry
            // no type information for us to check against.
            runCatching { Regex(expression) }.onFailure {
                logger.error("Invalid regular expression: ${it.message}", function)
                return null
            }
        } else {
            val placeholders = parsePlaceholders(function, expression) ?: return null
            if (!checkArity(function, expression, placeholders, capturedTypes)) return null
            if (!checkTypes(function, placeholders, capturedTypes)) return null
        }

        return StepBinding(
            expression = expression,
            isRegex = isRegex,
            receiver = receiver,
            functionName = function.simpleName.asString(),
            parameterTypes = parameterTypes,
            location = sourceLocationOf(function),
            containingFile = function.containingFile,
            declaration = function,
        )
    }

    private fun bindHook(function: KSFunctionDeclaration, annotation: KSAnnotation): HookBinding? {
        val receiver = resolveReceiver(function) ?: return null
        if (!isCallable(function)) return null

        if (function.parameters.isNotEmpty()) {
            logger.error("A hook must take no parameters, but this one takes ${function.parameters.size}", function)
            return null
        }

        val tagExpression = annotation.stringArgument("tagExpression").orEmpty()
        if (tagExpression.isNotEmpty()) {
            runCatching { TagExpressionParser.parse(tagExpression) }.onFailure {
                logger.error("Invalid tag expression: ${it.message}", function)
                return null
            }
        }

        return HookBinding(
            kind = if (annotation.shortName.asString() == "Before") "BEFORE" else "AFTER",
            tagExpression = tagExpression.ifEmpty { null },
            receiver = receiver,
            functionName = function.simpleName.asString(),
            location = sourceLocationOf(function),
            containingFile = function.containingFile,
        )
    }

    /**
     * Works out how to reach the function: directly, on an object, or on a fresh class instance.
     *
     * Requiring a no-arg constructor is what replaces Cucumber's DI container. Constructor
     * injection of other glue classes is described in ARCHITECTURE.md §8 but not implemented yet.
     */
    private fun resolveReceiver(function: KSFunctionDeclaration): Receiver? {
        val parent = function.parentDeclaration
            ?: return Receiver.TopLevel(
                MemberName(function.packageName.asString(), function.simpleName.asString()),
            )

        if (parent !is KSClassDeclaration) {
            logger.error("Step definitions must be top-level functions or members of a class or object", function)
            return null
        }

        val className = parent.toClassName() ?: run {
            logger.error("Cannot determine the class name of this step definition's owner", function)
            return null
        }

        return when {
            parent.classKind == ClassKind.OBJECT -> Receiver.Object(className)

            parent.classKind != ClassKind.CLASS -> {
                logger.error(
                    "Step definitions may not live in a ${parent.classKind.name.lowercase()}",
                    function,
                )
                null
            }

            Modifier.ABSTRACT in parent.modifiers -> {
                logger.error("A step definition class may not be abstract", parent)
                null
            }

            Modifier.INNER in parent.modifiers -> {
                logger.error("A step definition class may not be an inner class", parent)
                null
            }

            parent.primaryConstructor?.parameters?.isNotEmpty() != false -> {
                logger.error(
                    "A step definition class needs a no-argument constructor so a fresh instance " +
                        "can be created for each scenario. Constructor injection is not supported yet; " +
                        "use the steps { } DSL if you need it.",
                    parent,
                )
                null
            }

            else -> Receiver.Instance(className)
        }
    }

    private fun isCallable(function: KSFunctionDeclaration): Boolean {
        if (Modifier.PRIVATE in function.modifiers) {
            logger.error("A step definition may not be private — the generated registry cannot call it", function)
            return false
        }
        if (Modifier.ABSTRACT in function.modifiers) {
            logger.error("A step definition may not be abstract", function)
            return false
        }
        return true
    }

    /** Placeholder type names, in the order they appear in the expression. */
    private fun parsePlaceholders(function: KSFunctionDeclaration, expression: String): List<String>? =
        try {
            val names = mutableListOf<String>()
            collectParameters(CucumberExpressionParser.parse(expression), names)
            names
        } catch (failure: Throwable) {
            // The message already contains upstream's caret diagram, so it is worth showing whole.
            logger.error("Invalid Cucumber Expression:\n${failure.message}", function)
            null
        }

    private fun collectParameters(node: ExpressionNode, into: MutableList<String>) {
        if (node.type == ExpressionNodeType.PARAMETER_NODE) {
            into += node.text
            return
        }
        node.nodes?.forEach { collectParameters(it, into) }
    }

    private fun checkArity(
        function: KSFunctionDeclaration,
        expression: String,
        placeholders: List<String>,
        parameterTypes: List<TypeName>,
    ): Boolean {
        if (placeholders.size == parameterTypes.size) return true
        logger.error(
            "Expression '$expression' captures ${placeholders.size} argument(s) " +
                "but the function takes ${parameterTypes.size}",
            function,
        )
        return false
    }

    private fun checkTypes(
        function: KSFunctionDeclaration,
        placeholders: List<String>,
        parameterTypes: List<TypeName>,
    ): Boolean {
        var valid = true
        placeholders.forEachIndexed { index, placeholder ->
            val expected = BUILT_IN_PARAMETER_TYPES[placeholder]
            if (expected == null) {
                logger.error(
                    "Undefined parameter type '{$placeholder}'. Built-in types are " +
                        BUILT_IN_PARAMETER_TYPES.keys.filter { it.isNotEmpty() }.sorted().joinToString() +
                        ". Custom parameter types are only available through the steps { } DSL so far.",
                    function,
                )
                valid = false
                return@forEachIndexed
            }
            val actual = parameterTypes[index].copy(nullable = false).toString()
            if (actual != expected) {
                logger.error(
                    "Parameter ${index + 1} is declared $actual but '{$placeholder}' captures $expected",
                    function,
                )
                valid = false
            }
        }
        return valid
    }

    private fun reportDuplicateExpressions(steps: List<StepBinding>) {
        steps.groupBy { it.expression }
            // Only distinct functions can be ambiguous; one function is one definition however
            // many equivalent annotations it carries.
            .filterValues { group -> group.distinctBy { it.declaration }.size > 1 }
            .forEach { (expression, duplicates) ->
                val locations = duplicates.joinToString(", ") { it.location }
                duplicates.forEach { duplicate ->
                    logger.error(
                        "Expression '$expression' is defined more than once: $locations. " +
                            "Two definitions matching one step means the author cannot know which runs.",
                        duplicate.declaration,
                    )
                }
            }
    }

    private fun sourceLocationOf(function: KSFunctionDeclaration): String {
        val location = function.location
        return if (location is FileLocation) {
            "${location.filePath.substringAfterLast('/')}:${location.lineNumber}"
        } else {
            function.simpleName.asString()
        }
    }

    // ------------------------------------------------------------------ codegen

    private fun emit(steps: List<StepBinding>, hooks: List<HookBinding>) {
        // One property per distinct expression, compiled once at class-init rather than per
        // scenario — the generated registry is strictly cheaper than the DSL here.
        val expressionProperties = steps.map { it.expression }.distinct()
            .withIndex()
            .associate { (index, expression) -> expression to "EXPRESSION_$index" }

        val glueTypes = (steps.mapNotNull { it.receiver.instanceType() } +
            hooks.mapNotNull { it.receiver.instanceType() }).distinct()
        val glueProperties = glueTypes.associateWith { it.simpleName.replaceFirstChar { c -> c.lowercaseChar() } }

        val file = FileSpec.builder(generatedPackage, GENERATED_FILE_NAME)
            .addFileComment(
                "Generated by cucumber-kmp KSP from @Given/@When/@Then annotations. Do not edit.",
            )
            .addProperty(
                PropertySpec.builder("PARAMETER_TYPES", PARAMETER_TYPE_REGISTRY, com.squareup.kotlinpoet.KModifier.PRIVATE)
                    .initializer("%T()", PARAMETER_TYPE_REGISTRY)
                    .build(),
            )

        for ((expression, propertyName) in expressionProperties) {
            val isRegex = steps.first { it.expression == expression }.isRegex
            file.addProperty(
                PropertySpec.builder(propertyName, EXPRESSION, com.squareup.kotlinpoet.KModifier.PRIVATE)
                    .initializer(
                        if (isRegex) {
                            CodeBlock.of("%T(%T(%S), PARAMETER_TYPES)", REGULAR_EXPRESSION, REGEX, expression)
                        } else {
                            CodeBlock.of("%T(%S, PARAMETER_TYPES)", CUCUMBER_EXPRESSION, expression)
                        },
                    )
                    .build(),
            )
        }

        file.addProperty(
            PropertySpec.builder("generatedStepRegistry", STEP_REGISTRY_FACTORY)
                .addKdoc(
                    "Every step definition and hook found by KSP, as a factory that yields a fresh\n" +
                        "registry — and therefore fresh glue instances — for each scenario.\n",
                )
                .initializer(registryFactory(steps, hooks, expressionProperties, glueProperties))
                .build(),
        )

        val dependencies = com.google.devtools.ksp.processing.Dependencies(
            aggregating = true,
            *(steps.mapNotNull { it.containingFile } + hooks.mapNotNull { it.containingFile })
                .distinct()
                .toTypedArray(),
        )
        file.build().writeTo(codeGenerator, dependencies)
    }

    private fun registryFactory(
        steps: List<StepBinding>,
        hooks: List<HookBinding>,
        expressionProperties: Map<String, String>,
        glueProperties: Map<ClassName, String>,
    ): CodeBlock {
        val body = CodeBlock.builder()
        body.add("%T {\n", STEP_REGISTRY_FACTORY).indent()

        for ((type, propertyName) in glueProperties) {
            body.addStatement("val %N = %T()", propertyName, type)
        }

        body.add("object : %T {\n", STEP_REGISTRY).indent()

        body.add("override val definitions: List<%T> = listOf(\n", STEP_DEFINITION).indent()
        for (step in steps) {
            body.add(
                "%T(%N, %T(%S, %L)) { %L ->\n",
                STEP_DEFINITION,
                expressionProperties.getValue(step.expression),
                SOURCE_LOCATION,
                step.location.substringBeforeLast(':'),
                step.location.substringAfterLast(':'),
                // Naming an unused parameter would warn in the consumer's own build.
                if (step.parameterTypes.isEmpty()) "_" else "arguments",
            )
            body.indent()
            body.addStatement("%L", step.invocation(glueProperties))
            body.unindent()
            body.add("},\n")
        }
        body.unindent().add(")\n")

        body.add("override val hooks: List<%T> = listOf(\n", HOOK).indent()
        for (hook in hooks) {
            body.add(
                "%T(%T.%L, %L, %T(%S, %L)) {\n",
                HOOK,
                HOOK_KIND,
                hook.kind,
                hook.tagExpressionCode(),
                SOURCE_LOCATION,
                hook.location.substringBeforeLast(':'),
                hook.location.substringAfterLast(':'),
            )
            body.indent()
            body.addStatement("%L", hook.invocation(glueProperties))
            body.unindent()
            body.add("},\n")
        }
        body.unindent().add(")\n")

        body.unindent().add("}\n")
        body.unindent().add("}")
        return body.build()
    }

    private companion object {
        const val GENERATED_FILE_NAME = "GeneratedStepRegistry"

        const val ANNOTATIONS_PACKAGE = "io.github.menjoo.cucumberkmp.annotations"
        val STEP_ANNOTATION_NAMES = setOf("Given", "When", "Then", "Step")
        val HOOK_ANNOTATION_NAMES = setOf("Before", "After")
        val STEP_ANNOTATIONS = STEP_ANNOTATION_NAMES.map { "$ANNOTATIONS_PACKAGE.$it" }
        val HOOK_ANNOTATIONS = HOOK_ANNOTATION_NAMES.map { "$ANNOTATIONS_PACKAGE.$it" }

        const val CORE = "io.github.menjoo.cucumberkmp.core"
        val SOURCE_LOCATION = ClassName(CORE, "SourceLocation")
        val EXPRESSION = ClassName("$CORE.expression", "Expression")
        val CUCUMBER_EXPRESSION = ClassName("$CORE.expression", "CucumberExpression")
        val REGULAR_EXPRESSION = ClassName("$CORE.expression", "RegularExpression")
        val PARAMETER_TYPE_REGISTRY = ClassName("$CORE.expression", "ParameterTypeRegistry")
        val TAG_EXPRESSION_PARSER = ClassName("$CORE.tag", "TagExpressionParser")
        val STEP_DEFINITION = ClassName("$CORE.runner", "StepDefinition")
        val STEP_REGISTRY = ClassName("$CORE.runner", "StepRegistry")
        val STEP_REGISTRY_FACTORY = ClassName("$CORE.runner", "StepRegistryFactory")
        val HOOK = ClassName("$CORE.runner", "Hook")
        val HOOK_KIND = ClassName("$CORE.runner", "HookKind")
        val REGEX = ClassName("kotlin.text", "Regex")

        /**
         * What each built-in parameter type produces, so a binding can be checked at compile time.
         *
         * `biginteger` and `bigdecimal` yield `String` because Kotlin's common stdlib has no
         * arbitrary-precision numerics — see DEVIATIONS.md.
         */
        /**
         * Types a trailing step parameter may have to receive the step's attachment.
         *
         * A data table or doc string written under a step is an argument to it, supplied by the
         * runner after the expression's captures.
         */
        val ATTACHMENT_TYPES = setOf(
            "$CORE.gherkin.DataTable",
            "$CORE.gherkin.DocString",
        )

        val BUILT_IN_PARAMETER_TYPES = mapOf(
            "int" to "kotlin.Int",
            "long" to "kotlin.Long",
            "short" to "kotlin.Short",
            "byte" to "kotlin.Byte",
            "float" to "kotlin.Float",
            "double" to "kotlin.Double",
            "biginteger" to "kotlin.String",
            "bigdecimal" to "kotlin.String",
            "word" to "kotlin.String",
            "string" to "kotlin.String",
            "" to "kotlin.String",
        )

        fun KSAnnotation.stringArgument(name: String): String? =
            arguments.firstOrNull { it.name?.asString() == name }?.value as? String

        fun KSClassDeclaration.toClassName(): ClassName? {
            val qualified = qualifiedName?.asString() ?: return null
            val packageName = packageName.asString()
            val simpleNames = qualified.removePrefix("$packageName.").split('.')
            return ClassName(packageName, simpleNames)
        }
    }

    /** How the generated code reaches a step function. */
    private sealed interface Receiver {
        /** A top-level function, referenced by [MemberName] so KotlinPoet imports it. */
        data class TopLevel(val member: MemberName) : Receiver
        data class Object(val type: ClassName) : Receiver
        data class Instance(val type: ClassName) : Receiver

        /** The class that needs a per-scenario instance, if any. */
        fun instanceType(): ClassName? = (this as? Instance)?.type

        /** Emits the callee, up to and including the opening parenthesis. */
        fun callee(functionName: String, glueProperties: Map<ClassName, String>): CodeBlock = when (this) {
            is TopLevel -> CodeBlock.of("%M(", member)
            is Object -> CodeBlock.of("%T.%N(", type, functionName)
            is Instance -> CodeBlock.of("%N.%N(", glueProperties.getValue(type), functionName)
        }
    }

    private class StepBinding(
        val expression: String,
        val isRegex: Boolean,
        val receiver: Receiver,
        val functionName: String,
        val parameterTypes: List<TypeName>,
        val location: String,
        val containingFile: KSFile?,
        val declaration: KSFunctionDeclaration,
    ) {
        fun invocation(glueProperties: Map<ClassName, String>): CodeBlock {
            val call = CodeBlock.builder()
            call.add(receiver.callee(functionName, glueProperties))
            parameterTypes.forEachIndexed { index, type ->
                if (index > 0) call.add(", ")
                // An explicit typed cast is what replaces Method.invoke's reflective coercion.
                call.add("arguments[%L] as %T", index, type)
            }
            call.add(")")
            return call.build()
        }
    }

    private class HookBinding(
        val kind: String,
        val tagExpression: String?,
        val receiver: Receiver,
        val functionName: String,
        val location: String,
        val containingFile: KSFile?,
    ) {
        fun tagExpressionCode(): CodeBlock =
            if (tagExpression == null) {
                CodeBlock.of("null")
            } else {
                CodeBlock.of("%T.parse(%S)", TAG_EXPRESSION_PARSER, tagExpression)
            }

        fun invocation(glueProperties: Map<ClassName, String>): CodeBlock =
            CodeBlock.builder()
                .add(receiver.callee(functionName, glueProperties))
                .add(")")
                .build()
    }
}
