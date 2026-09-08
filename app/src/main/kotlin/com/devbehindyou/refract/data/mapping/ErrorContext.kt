package com.devbehindyou.refract.data.mapping

/**
 * Carries just enough context to make a mapped [com.devbehindyou.refract.domain.model.FileError]
 * useful, without leaking a platform type across the data-layer boundary. Referenced but never
 * concretely defined anywhere in `/docs` (`architecture/DATA_LAYER.md` §4 shows only
 * `context?.name` used in its one example) — same situation as Phase 2's `AccessTarget`/
 * `NameProblem`. This is a minimal, provisional shape; extend it if a mapped error later
 * needs more than a name to produce a good message.
 */
data class ErrorContext(val name: String?)
