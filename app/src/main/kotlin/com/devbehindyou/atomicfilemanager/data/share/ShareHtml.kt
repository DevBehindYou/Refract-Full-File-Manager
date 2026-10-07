package com.devbehindyou.atomicfilemanager.data.share

import java.net.URLEncoder
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

/** The pages [ShareServer] sends; plain HTML with escaped names. Pure so it is unit-tested. */
object ShareHtml {
    data class Entry(
        val name: String,
        val rel: String,
        val isDirectory: Boolean,
        val size: Long,
    )

    fun login(wrong: Boolean): String =
        page(
            "Atomic File Manager",
            """
            <h1>Enter the PIN</h1>
            <p>It is shown on the phone sharing these files.</p>
            ${if (wrong) "<p class=\"err\">That PIN is wrong.</p>" else ""}
            <form method="post" action="/login">
              <input name="pin" inputmode="numeric" autocomplete="one-time-code" maxlength="6" autofocus>
              <button>Open</button>
            </form>
            """.trimIndent(),
        )

    fun listing(
        title: String,
        rel: String,
        entries: List<Entry>,
        allowUpload: Boolean,
    ): String {
        val up =
            if (rel.isNotEmpty()) {
                "<li><a href=\"/?p=${link(rel.substringBeforeLast('/', ""))}\">↑ Up</a></li>"
            } else {
                ""
            }
        val rows =
            entries.joinToString("\n") { e ->
                if (e.isDirectory) {
                    "<li><a href=\"/?p=${link(e.rel)}\">${escape(e.name)}/</a></li>"
                } else {
                    "<li><a href=\"/dl?p=${link(e.rel)}\">${escape(e.name)}</a> <span>${size(e.size)}</span></li>"
                }
            }
        val uploadForm =
            if (allowUpload) {
                """
                <h2>Send files to the phone</h2>
                <input type="file" id="f" multiple> <span id="s"></span>
                <script>
                document.getElementById('f').onchange = async (ev) => {
                  const s = document.getElementById('s');
                  for (const file of ev.target.files) {
                    s.textContent = 'Sending ' + file.name + '…';
                    const r = await fetch('/up?p=${link(
                    rel,
                )}&n=' + encodeURIComponent(file.name), { method: 'PUT', body: file });
                    s.textContent = r.ok ? 'Sent ' + file.name : file.name + ': ' + await r.text();
                    if (!r.ok) return;
                  }
                  location.reload();
                };
                </script>
                """.trimIndent()
            } else {
                ""
            }
        val heading = escape(if (rel.isEmpty()) title else "$title/$rel")
        return page(heading, "<h1>$heading</h1>\n<ul>\n$up\n$rows\n</ul>\n$uploadForm")
    }

    /** One path segment a browser may name an upload: no separators, dots-only or hidden names. */
    fun isSafeName(name: String): Boolean =
        name.isNotBlank() && name.length <= MAX_NAME && !name.startsWith(".") &&
            name.none { it == '/' || it == '\\' || it == '\u0000' || it.isISOControl() }

    fun join(
        rel: String,
        name: String,
    ): String = if (rel.isEmpty()) name else "$rel/$name"

    fun escape(text: String): String =
        buildString {
            text.forEach {
                when (it) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&#39;")
                    else -> append(it)
                }
            }
        }

    private fun link(rel: String): String = URLEncoder.encode(rel, "UTF-8").replace("+", "%20")

    private fun size(bytes: Long): String {
        if (bytes < KIB) return "$bytes B"
        val units = listOf("KB", "MB", "GB", "TB")
        val exp = (ln(bytes.toDouble()) / ln(KIB.toDouble())).toInt().coerceIn(1, units.size)
        return String.format(Locale.US, "%.1f %s", bytes / KIB.toDouble().pow(exp), units[exp - 1])
    }

    private fun page(
        title: String,
        body: String,
    ): String =
        """
        <!doctype html>
        <html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
        <title>$title</title>
        <style>
        body{font:16px system-ui,sans-serif;max-width:46rem;margin:2rem auto;padding:0 1rem;color:#1b1b1b;background:#fff}
        a{color:#0b57d0;text-decoration:none} li{padding:.35rem 0;list-style:none} ul{padding:0}
        span{color:#666;font-size:.9em} .err{color:#b3261e} input,button{font:inherit;padding:.4rem}
        @media (prefers-color-scheme:dark){body{background:#121212;color:#eee} a{color:#8ab4f8} span{color:#aaa}}
        </style></head><body>
        $body
        </body></html>
        """.trimIndent()

    private const val KIB = 1024L
    private const val MAX_NAME = 255
}
