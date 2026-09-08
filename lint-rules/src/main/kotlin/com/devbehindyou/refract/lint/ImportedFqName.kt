package com.devbehindyou.refract.lint

import org.jetbrains.uast.UImportStatement

/**
 * Renders a `UImportStatement` back to the fully-qualified name it imports, e.g.
 * `import android.content.Context` -> `"android.content.Context"`, and
 * `import android.content.Context as Ctx` -> `"android.content.Context"`.
 *
 * Implemented against the statement's own rendered source rather than
 * `importReference`'s narrower API surface, so it degrades gracefully if that inner
 * API shape has moved between Lint API versions.
 */
internal fun UImportStatement.importedFqName(): String? {
    val source = asSourceString().trim()
    if (!source.startsWith("import ")) return null
    return source
        .removePrefix("import ")
        .substringBefore(" as ")
        .removeSuffix(";")
        .trim()
        .takeIf { it.isNotEmpty() }
}
