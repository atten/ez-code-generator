package org.codegen.generators

import org.codegen.schema.Constants.Companion.EMPTY
import org.codegen.schema.Constants.Companion.UNSET
import org.codegen.schema.Entity
import org.codegen.schema.Field
import org.codegen.utils.CodeFormatRules
import org.codegen.utils.camelCase
import org.codegen.utils.lowercaseFirst
import org.codegen.utils.snakeCase

open class KtDataclassGenerator(includedEntityType: AllGeneratorsEnum, parent: AbstractCodeGenerator? = null) : AbstractCodeGenerator(
    CodeFormatRules.KOTLIN,
    includedEntityType,
    parent,
) {
    private val kotlinKeywords = setOf("open", "fun") // incomplete and contains only known

    protected open fun buildFieldDefinition(field: Field): String {
        val dataType = getDtype(field.dtype)
        val definitionKeyword = "val"
        val fieldName = field.name.camelCase().lowercaseFirst()
        val assignmentExpression =
            when (field.default) {
                UNSET -> ""
                null -> "null"
                else ->
                    if (field.many) {
                        if (field.default == EMPTY) {
                            "listOf()"
                        } else {
                            "listOf(${dataType.toGeneratedValue(field.default)})"
                        }
                    } else {
                        dataType.toGeneratedValue(field.default)
                    }
            }
                .let { if (it.isEmpty()) "" else "= $it" }

        val typeName =
            dataType.definition
                .let { if (field.isEnum) renderEnumName(field) else it }
                .let { if (field.nullable) "$it?" else it }
                .let { if (field.many) "List<$it>" else it }
        return "$definitionKeyword $fieldName: $typeName $assignmentExpression".trim()
    }

    override fun renderEntity(entity: Entity) = buildEntity(entity, setOf())

    open fun buildEntity(
        entity: Entity,
        annotations: Set<String>,
    ): String {
        val preLines = mutableListOf<String>()
        val className = renderEntityName(entity.name)
        val definition = "data class $className("
        val lines = annotations.toMutableList().also { it.add(definition) }

        entity.description?.also {
            preLines.add("/**")
            preLines.add(" * " + entity.description)
            preLines.add(" */")
        }

        // include parent class fields (because data class inheritance is not allowed)
        val includedFields = entity.parent?.let { findEntity(it)?.fields } ?: listOf()

        for (field in entity.fieldsSortedByDefaults + includedFields) {
            val fullDefinition = buildFieldDefinition(field)

            field.enum?.let { enum ->
                val enumName = renderEnumName(field)
                val enumBody =
                    enum.map { row -> buildEnumItem(row.key) }.joinToString(
                        separator = "\n",
                        prefix = "enum class $enumName(val value: String) {\n",
                        postfix = "\n}\n",
                    ) { "    ${it.replace("\n", "\n    ")}," }
                addCodePart(enumBody, enumName)
            }

            field.description?.let { lines.add("    // $it") }
            field.longDescription?.let { lines.add("    // $it") }
            lines.add("    ${fullDefinition.replace("\n", "\n    ")},")
        }

        lines.add(")")
        return (preLines + lines).joinToString("\n", postfix = "\n")
    }

    protected fun buildEnumLiteral(key: String) = key.snakeCase().uppercase()

    protected open fun buildEnumItem(key: String): String {
        val itemName = buildEnumLiteral(key)
        val literal = itemName.let { if (it in kotlinKeywords) "`$it`" else it }
        return "$literal(\"${key}\")"
    }
}
