package org.codegen.parser.openapi

import kotlinx.serialization.Serializable

@Serializable
internal data class Path(
    val get: Method? = null,
    val post: Method? = null,
    val put: Method? = null,
    val patch: Method? = null,
    val delete: Method? = null,
    val parameters: List<Parameter> = listOf(),
)
