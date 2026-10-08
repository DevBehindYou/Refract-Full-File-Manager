package com.devbehindyou.atomicfilemanager.data.backend.shizuku

/** One file or folder as the Shizuku service reports it. */
data class ShizukuEntry(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val modifiedAt: Long,
)

/**
 * The text the Shizuku service and the app pass across the binder, and the folders the service
 * agrees to touch (ALL_IN_ONE_PLAN.md 4.3). Pure so both rules are unit-tested.
 */
object ShizukuEntries {
    private const val ROW = '\u001E'
    private const val FIELD = '\u001F'

    fun encode(entries: List<ShizukuEntry>): String =
        entries.joinToString(ROW.toString()) {
            listOf(it.name, if (it.isDirectory) "d" else "f", it.size.toString(), it.modifiedAt.toString())
                .joinToString(FIELD.toString())
        }

    /** Rows that don't parse are skipped; names can't contain the separators. */
    fun decode(text: String?): List<ShizukuEntry> =
        text.orEmpty().split(ROW).mapNotNull { row ->
            val f = row.split(FIELD)
            if (f.size != FIELDS || f[0].isEmpty()) return@mapNotNull null
            ShizukuEntry(
                name = f[0],
                isDirectory = f[1] == "d",
                size = f[2].toLongOrNull() ?: return@mapNotNull null,
                modifiedAt = f[3].toLongOrNull() ?: return@mapNotNull null,
            )
        }

    /**
     * True only for `Android/data` and `Android/obb` (and what is inside them) on a user's shared
     * storage, after `..` and `.` are resolved. Everything else the shell user could reach stays
     * out of reach.
     */
    fun allowed(path: String): Boolean {
        if (!path.startsWith("/")) return false
        val parts = ArrayDeque<String>()
        path.split('/').filter { it.isNotEmpty() && it != "." }.forEach { part ->
            if (part == "..") parts.removeLastOrNull() else parts.addLast(part)
        }
        val p = parts.toList()
        val user = p.getOrNull(USER)
        return p.size >= ROOT_DEPTH &&
            p[0] == "storage" && p[1] == "emulated" &&
            user != null && user.all(Char::isDigit) &&
            p[ANDROID] == "Android" && p[KIND] in KINDS
    }

    /** The two roots offered on the Storage screen for [userId] (0 on most phones). */
    fun roots(userId: Int = 0): List<String> = KINDS.map { "/storage/emulated/$userId/Android/$it" }

    private val KINDS = listOf("data", "obb")
    private const val FIELDS = 4
    private const val USER = 2
    private const val ANDROID = 3
    private const val KIND = 4
    private const val ROOT_DEPTH = 5
}
