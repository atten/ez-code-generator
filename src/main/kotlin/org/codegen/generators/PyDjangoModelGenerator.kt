package org.codegen.generators

import org.codegen.schema.Constants.Companion.EMPTY
import org.codegen.schema.Constants.Companion.UNSET
import org.codegen.schema.Entity
import org.codegen.utils.CodeFormatRules
import org.codegen.utils.snakeCase

class PyDjangoModelGenerator(proxy: AbstractCodeGenerator? = null) : AbstractCodeGenerator(
    CodeFormatRules.PYTHON,
    AllGeneratorsEnum.PY_DJANGO_MODEL,
    proxy,
) {
    private val plainDataTypes = listOf("bool", "int", "float", "str")

    override fun renderEntity(entity: Entity): String {
        if (entity.fields.isEmpty()) {
            return ""
        }

        val preLines = mutableListOf<String>()
        val className = renderEntityName(entity.name)
        val lines = mutableListOf("class $className(models.Model):")

        entity.description?.also {
            lines.add("    \"\"\"")
            lines.add("    " + entity.description)
            lines.add("    \"\"\"")
        }

        for (field in entity.fields) {
            val dtypeProps = getDtype(field.dtype)
            val fieldName = field.name.snakeCase()
            val attrs = dtypeProps.definitionArguments.toMutableMap()
            val isScalar = plainDataTypes.contains(field.dtype)
            val isDjangoModel = dtypeProps.definition.startsWith("models.")

            if (field.default != UNSET) {
                when {
                    field.default == EMPTY -> {
                        attrs["default"] = if (field.many) "list" else dtypeProps.definition
                    }
                    field.default == null -> {
                        attrs["default"] = "None"
                    }
                    field.default.isNotEmpty() && "[{".contains(field.default[0]) -> {
                        // complex value (list/map/etc) should be inserted via function above class
                        val callableName = "default_$fieldName"
                        val defaultValue = dtypeProps.toGeneratedValue(field.default)
                        preLines.add("def $callableName():")
                        preLines.add("    return $defaultValue\n\n")
                        attrs["default"] = callableName
                    }
                    else -> {
                        // simple value -> insert inline
                        attrs["default"] = dtypeProps.toGeneratedValue(field.default)
                    }
                }

                // 'blank' flag is required for non-scalar data types with default value
                if (!isScalar || field.many) {
                    attrs["blank"] = "True"
                }
            }

            if (field.nullable) {
                attrs["blank"] = "True"
                attrs["null"] = "True"
                // redundant default argument
                if (field.default == null) {
                    attrs.remove("default")
                }
            }

            field.enum?.let {
                val subclass = "models.TextChoices"
                val enumName = renderEnumName(field, "${entity.name} ${field.name}")
                val choicesDefinition =
                    it.map { entry -> "    ${entry.key.snakeCase().uppercase()} = '${entry.key}', _('${entry.value}')" }.joinToString(
                        separator = "\n",
                        prefix = "class $enumName($subclass):\n",
                        postfix = "\n\n",
                    )
                preLines.add(choicesDefinition)

                attrs["choices"] = "$enumName.choices"
                attrs["max_length"] = it.keys.maxOfOrNull { s -> s.length }.toString()
            }

            field.description?.let {
                attrs["verbose_name"] = "_('${it.replace("'", "\\'")}')"
            }

            field.longDescription?.let {
                attrs["help_text"] = "_('${it.replace("'", "\\'")}')"
            }

            val fieldClass =
                when {
                    field.many && isScalar ->
                        "ArrayField".also {
                            headers.add("from django.contrib.postgres.fields import ArrayField")
                            val baseFieldAttributes = mutableMapOf<String, String>()
                            attrs.remove("max_length")?.let { baseFieldAttributes["max_length"] = it }
                            val baseFieldAttributesString =
                                baseFieldAttributes
                                    .map { entry -> "${entry.key}=${entry.value}" }
                                    .joinToString()
                            attrs["base_field"] = "${dtypeProps.definition}($baseFieldAttributesString)"
                        }
                    field.many && !isScalar -> "models.JSONField" // use json field for multiple non-scalar values
                    !isDjangoModel -> "models.JSONField"
                    else -> dtypeProps.definition
                }

            val attrsString =
                attrs
                    .map { entry -> "${entry.key}=${entry.value}" }
                    .sortedBy { it.length }
                    .joinToString()
            lines.add("    $fieldName = $fieldClass($attrsString)")
        }

        if (!entity.prefix.isNullOrEmpty()) {
            lines.add("\n    PREFIX = '${entity.actualPrefix.snakeCase()}'")
        }

        // add Meta section
        lines.add("")
        lines.add("    class Meta:")
        lines.add("        abstract = True")

        return (preLines + lines).joinToString("\n")
    }

    override fun renderHeaders(): String {
        headers.add("from django.utils.translation import gettext_lazy as _")
        return super.renderHeaders()
    }
}
