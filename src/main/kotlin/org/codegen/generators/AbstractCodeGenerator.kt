package org.codegen.generators

import okhttp3.internal.toImmutableMap
import org.codegen.schema.DataType
import org.codegen.schema.Entity
import org.codegen.schema.Field
import org.codegen.utils.CodeFormatRules
import org.codegen.utils.EnvironmentUtils.Companion.substituteEnvVariables
import org.codegen.utils.findCommonPart
import org.codegen.utils.pluralize
import java.util.StringJoiner
import kotlin.reflect.full.primaryConstructor

abstract class AbstractCodeGenerator(
    private val codeFormatRules: CodeFormatRules,
    private val includedEntityType: AllGeneratorsEnum,
    private val parent: AbstractCodeGenerator? = null,
) {
    // mapping of data types by name
    private val dataTypes = mutableMapOf<String, DataType>()

    protected val entities = mutableListOf<Entity>()

    // collected headers at the top of the output file
    protected val headers = mutableSetOf<String>()
        get() = parent?.headers ?: field

    // built classes/enums (included entities)
    private val codeParts = mutableListOf<String>()
        get() = parent?.codeParts ?: field

    private val enumNames = mutableMapOf<Map<String, String>, String>()
        get() = parent?.enumNames ?: field

    /**
     * Pick primary corresponding enum value and it's aliases.
     */
    private val target: AllGeneratorsEnum by lazy {
        AllGeneratorsEnum.entries.forEach { enum ->
            if (this::class == enum.generatorClass) {
                return@lazy enum
            }
        }
        throw RuntimeException("enum value is not assigned")
    }

    protected val includedEntityGenerator: AbstractCodeGenerator by lazy {
        if (target == includedEntityType) {
            return@lazy this
        } else {
            includedEntityType.generatorClass.primaryConstructor!!.call(this)
        }
    }

    fun render(): String {
        require(entities.isNotEmpty()) { "Nothing to render, entities list is empty. Check input files and try again." }

        val prefix = renderBodyPrefix()
        val body = renderBody()
        // confirm headers list after body is rendered
        val headers = renderHeaders()
        val suffix = renderBodySuffix()

        return StringJoiner("")
            .add(headers)
            .add(prefix)
            .add(body)
            .add(suffix)
            .toString()
    }

    protected open fun addCodePart(
        body: String,
        vararg names: String,
    ) {
        if (parent != null) {
            parent.addCodePart(body, *names)
        } else if (body.isNotEmpty()) {
            body.trim().let { trimmed ->
                if (!codeParts.contains(trimmed)) {
                    codeParts.add(trimmed)
                }
            }
        }
    }

    fun addDataType(
        dtype: String,
        attrs: DataType,
    ) {
        if (parent != null) {
            parent.addDataType(dtype, attrs)
        } else {
            dataTypes[dtype] = attrs
        }
    }

    fun addEntity(entity: Entity) {
        if (entities.contains(entity)) {
            // found exact match
            return
        }
        if (findEntity(entity.name) != null) {
            // found entity with same name
            throw IllegalArgumentException("Found different entities with same name: ${entity.name}")
        }
        entities.add(entity)
    }

    protected fun findEntity(name: String): Entity? {
        return entities.find { it.name == name } ?: parent?.findEntity(name)
    }

    protected fun getDtype(name: String): DataType {
        val dtype = parent?.getDtype(name) ?: dataTypes[name] ?: findEntity(name)?.toDataType()?.copy(definition = renderEntityName(name))
        requireNotNull(dtype) {
            "${this::class.simpleName}: Missing extension for dtype '$name'. Define it in any of following sections: ${target.dtypeAliases()}."
        }
        useDataType(dtype)
        return dtype
    }

    private fun useDataType(type: DataType) {
        // include headers
        type.requiredHeaders.forEach { headers.add(substituteEnvVariables(it)) }

        // include files
        type.loadIncludedFiles().forEach { addCodePart(it) }

        // add missing entities (if required)
        type.requiredEntities
            .forEach { name ->
                val entity = findEntity(name)
                requireNotNull(entity) {
                    "Missing required entity '$name'. Probably you forgot to include corresponding file: --include=<file>"
                }

                includedEntityGenerator
                    .renderEntity(entity)
                    .also { addCodePart(it, renderEntityName(name)) }
            }
    }

    protected fun renderEnumName(
        enumField: Field,
        vararg nameAliases: String,
    ): String {
        val enum = enumField.enum!!.toImmutableMap()
        return enumNames.getOrElse(enum) {
            listOf(
                enumField.name,
                *nameAliases,
                enum.keys.reduce { result, part -> result.findCommonPart(part) }.pluralize() + " enum",
                listOf(enumField.enumPrefix ?: "", enumField.name).joinToString(separator = " "),
            )
                .map { renderEntityName(it) }
                .filter { !clashesWithBuiltinNames(it) }
                .filter { it !in listOf("Type", "Enum", "Class", "Namespace") } // avoid common names
                .first { !enumNames.values.contains(it) }
        }
    }

    protected fun assignEnumName(
        enumField: Field,
        enumName: String,
    ) {
        enumNames[enumField.enum!!.toImmutableMap()] = enumName
    }

    protected fun renderEntityName(name: String) = codeFormatRules.entityName(name)

    private fun clashesWithBuiltinNames(name: String) =
        headers
            .map { it.split(" ").map { word -> word.trim(',') } }
            .flatten()
            .count { name == it } > 0

    abstract fun renderEntity(entity: Entity): String

    protected open fun renderHeaders(): String {
        // sort alphabetically
        return headers
            .sorted()
            .joinToString(
                "\n",
                prefix = codeFormatRules.filePrefix(),
                postfix = codeFormatRules.headersPostfix,
            )
    }

    protected open fun renderBodyPrefix() = ""

    protected open fun renderBody(): String {
        for (entity in entities) {
            val body = renderEntity(entity)
            addCodePart(body, renderEntityName(entity.name))
        }

        val bodyParts = StringJoiner(codeFormatRules.entitiesSeparator)
        for (codePart in codeParts) {
            bodyParts.add(codePart)
        }
        return bodyParts.toString()
    }

    protected open fun renderBodySuffix() = "\n" // newline at the end by default
}
