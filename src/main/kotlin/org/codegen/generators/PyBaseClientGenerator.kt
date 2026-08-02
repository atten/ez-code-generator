package org.codegen.generators

import org.codegen.schema.Constants.Companion.EMPTY
import org.codegen.schema.Constants.Companion.UNSET
import org.codegen.schema.Endpoint
import org.codegen.schema.Entity
import org.codegen.schema.MethodArgument
import org.codegen.utils.CodeFormatRules
import org.codegen.utils.Reader
import org.codegen.utils.snakeCase

abstract class PyBaseClientGenerator(proxy: AbstractCodeGenerator? = null) : AbstractCodeGenerator(
    CodeFormatRules.PYTHON,
    AllGeneratorsEnum.PY_MARSHMALLOW_DATACLASS,
    proxy,
) {
    protected val atomicJsonTypes = listOf("str", "float", "int", "None", "bool")

    // list if __all__ items
    protected val definedNames = mutableSetOf<String>()
    private val definedDataclasses = mutableSetOf<String>()
    protected val definedExceptions = mutableSetOf<String>()
    private val definedConstants = mutableSetOf<String>()

    private fun buildArgumentDefaultValue(argument: MethodArgument): String {
        val dtypeProps = getDtype(argument.dtype)
        return if (argument.default == UNSET) {
            ""
        } else if (argument.many) {
            // use immutable tuple for default value
            "()"
        } else {
            when (argument.default) {
                EMPTY -> "${dtypeProps.definition}()".replace("str()", "''")
                null -> "None"
                else -> dtypeProps.toGeneratedValue(argument.default)
            }
        }
    }

    override fun addCodePart(
        body: String,
        vararg names: String,
    ) {
        if (body.startsWith("@dataclass")) {
            definedDataclasses.addAll(names)
        } else if (body.contains("Enum):")) {
            definedConstants.addAll(names)
        } else {
            definedNames.addAll(names)
        }
        super.addCodePart(body, *names)
    }

    protected open fun buildMethodDefinition(
        name: String,
        arguments: List<String>,
        returnStatement: String,
        singleLine: Boolean? = null,
    ): String {
        when (singleLine) {
            true -> {
                val argumentsString = arguments.joinToString(separator = ", ")
                return "def $name($argumentsString)$returnStatement"
            }
            false -> {
                val argumentsString =
                    arguments.joinToString(separator = ",\n    ", prefix = "\n    ", postfix = ",\n") {
                        it.replace("\n", "\n    ")
                    }
                return "def $name($argumentsString)$returnStatement"
            }
            else -> {
                // auto-choice
                val oneLiner = buildMethodDefinition(name, arguments, returnStatement, singleLine = true)
                if (oneLiner.length > 120) {
                    return buildMethodDefinition(name, arguments, returnStatement, singleLine = false)
                }
                return oneLiner
            }
        }
    }

    protected open fun renderEndpointHeader(endpoint: Endpoint): String {
        val name = endpoint.name.snakeCase()
        val returnDtypeProps = getDtype(endpoint.dtype)
        val returnStatement =
            returnDtypeProps.definition
                .let {
                    if (endpoint.many) {
                        headers.add("import typing as t")
                        if (endpoint.cacheable) "list[$it]" else "t.Iterator[$it]"
                    } else if (endpoint.nullable) {
                        "$it | None"
                    } else {
                        it
                    }
                }
                .let { if (it == "None") ":" else " -> $it:" }

        val arguments = mutableListOf("self")

        for (argument in endpoint.argumentsSortedByDefaults) {
            val argName = argument.name.snakeCase()
            val dtypeProps = getDtype(argument.dtype)
            val argTypeName =
                dtypeProps.definition
                    .let {
                        if (argument.isEnum) {
                            headers.add("from enum import StrEnum")
                            val enumName = renderEnumName(argument.toField()).also { name -> assignEnumName(argument.toField(), name) }
                            val choices =
                                argument.enum!!.keys.associate { key ->
                                    key.snakeCase().uppercase() to dtypeProps.toGeneratedValue(key)
                                }
                            val choicesDefinition =
                                choices.map { entry ->
                                    "    ${entry.key} = ${entry.value}"
                                }.joinToString(separator = "\n")
                            val body = "class $enumName(StrEnum):\n$choicesDefinition"
                            addCodePart(body, enumName)
                            enumName
                        } else {
                            it
                        }
                    }
                    .let {
                        if (argument.many) {
                            headers.add("import typing as t")
                            "t.Sequence[$it]"
                        } else {
                            it
                        }
                    }
                    .let { if (argument.nullable) "$it | None" else it }

            if (!dtypeProps.isNative || argument.isEnum) {
                // custom types are referenced before definition
                headers.add("from __future__ import annotations") // redundant since python 3.12
            }

            val argDefaultValue =
                buildArgumentDefaultValue(argument)
                    .let { if (it.isEmpty()) "" else "= $it" }

            val argDescription =
                if (!argument.description.isNullOrEmpty()) {
                    "# ${argument.description}".trim()
                } else {
                    ""
                }

            val argumentString = "$argDescription\n$argName: $argTypeName $argDefaultValue".trim()
            arguments.add(argumentString)
        }

        val lines = mutableListOf<String>()

        if (endpoint.cacheable) {
            headers.add("from funcy import memoize")
            lines.add("@memoize")
        }

        val singleLine =
            if (arguments.any { "\n" in it }) {
                false
            } else {
                null // auto-choice
            }

        lines.add(buildMethodDefinition(name, arguments, returnStatement, singleLine = singleLine))
        endpoint.description
            ?.also { lines.add("    \"\"\"") }
            ?.split("\n")
            ?.map { line -> line.trimEnd { it.isWhitespace() } }
            ?.forEach { lines.add("    $it") }
            ?.also { lines.add("    \"\"\"") }

        if (!returnDtypeProps.isNative) {
            // custom types are referenced before definition
            headers.add("from __future__ import annotations") // redundant since python 3.12
        }

        return lines.joinToString(separator = "\n")
    }

    abstract fun renderEndpointBody(endpoint: Endpoint): String

    private fun renderEndpoint(endpoint: Endpoint) =
        renderEndpointHeader(endpoint) +
            renderEndpointBody(endpoint)
                .prependIndent("    ")
                .let { if (it.isNotEmpty()) "\n$it" else it }

    override fun renderEntity(entity: Entity): String {
        if (entity.fields.isNotEmpty()) {
            return PyMarshmallowDataclassGenerator(this).renderEntity(entity)
        }

        // client class with endpoints
        val className = renderEntityName(entity.name)
        val classDefinition = "class $className:"
        val classBody = getMainApiClassBody()
        val classMethods = entity.endpoints.map { renderEndpoint(it) }
        return classMethods.joinToString(
            separator = "\n\n",
            prefix = "${classDefinition}\n${classBody}\n\n",
            postfix = CodeFormatRules.PYTHON.entitiesSeparator,
        ) {
            it
                .prependIndent("    ")
                .replace("\n    \n", "\n\n") // remove blanks, keep linters silent
        }
    }

    override fun renderBodyPrefix(): String {
        listOf(
            "resource:/templates/python/baseSchema.py",
        )
            .map { Reader.readFileOrResourceOrUrl(it) }
            .map { addCodePart(it) }

        // put main client class on top of the file
        val clientEntity = entities.first { it.endpoints.isNotEmpty() }
        val clientBody = renderEntity(clientEntity)
        definedNames.add(renderEntityName(clientEntity.name))

        // do not render entities not included into client
        entities.clear()
        return clientBody
    }

    override fun renderBody(): String {
        getBodyIncludedFiles()
            .map { Reader.readFileOrResourceOrUrl(it) }
            .map { addCodePart(it) }

        if (definedConstants.isNotEmpty()) {
            addCodePart(
                "class AllConstantsCollection:\n" +
                    definedConstants
                        .sorted()
                        .joinToString("\n") { "    $it = $it" },
                "AllConstantsCollection",
            )
        }

        if (definedDataclasses.isNotEmpty()) {
            addCodePart(
                "class AllDataclassesCollection:\n" +
                    definedDataclasses
                        .sorted()
                        .joinToString("\n") { "    $it = $it" },
                "AllDataclassesCollection",
            )
        }

        if (definedExceptions.isNotEmpty()) {
            addCodePart(
                "class AllExceptionsCollection:\n" +
                    definedExceptions
                        .sorted()
                        .joinToString("\n") { "    $it = $it" },
                "AllExceptionsCollection",
            )
        }

        addCodePart(
            "__all__ = [\n" +
                definedNames
                    .filter { it.isNotEmpty() }
                    .sorted()
                    .joinToString("\n") { "    \"${it}\"," } + "\n]",
        )

        return super.renderBody()
    }

    abstract fun getMainApiClassBody(): String

    abstract fun getBodyIncludedFiles(): List<String>
}
