package com.dsq.rebackground.paint.document

/** Minimal semantic document version tag. */
data class DocumentVersion(
    val major: Int = 1,
    val minor: Int = 0
) {
    init {
        require(major >= 0 && minor >= 0)
    }

    fun encode(): String = "$major.$minor"

    companion object {
        fun parse(value: String): DocumentVersion {
            val parts = value.split('.')
            require(parts.size == 2) { "Document version must be major.minor" }
            return DocumentVersion(
                parts[0].toIntOrNull() ?: error("Invalid major version"),
                parts[1].toIntOrNull() ?: error("Invalid minor version")
            )
        }
    }
}
