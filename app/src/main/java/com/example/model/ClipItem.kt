package com.example.model

import java.util.UUID

data class ClipItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val category: String = detectCategory(text)
) {
    val charCount: Int
        get() = text.length

    val wordCount: Int
        get() = if (text.isBlank()) 0 else text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }

    val lineCount: Int
        get() = text.lines().size

    companion object {
        fun detectCategory(content: String): String {
            val trimmed = content.trim()
            if (trimmed.isEmpty()) return "text"

            // URL detection
            if (trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) ||
                trimmed.startsWith("www.", ignoreCase = true) ||
                Regex("^(https?://|www\\.)[a-zA-Z0-9-]+\\.[a-zA-Z]{2,}(/\\S*)?$").matches(trimmed)
            ) {
                return "url"
            }

            // Email detection
            if (Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$").matches(trimmed)) {
                return "email"
            }

            // Phone number
            if (Regex("^(\\+?\\d{1,4}[\\s-]?)?(\\(?\\d{2,4}\\)?[\\s-]?)?[\\d\\s-]{6,15}$").matches(trimmed) &&
                trimmed.count { it.isDigit() } >= 7
            ) {
                return "phone"
            }

            // Code / JSON / Markup detection
            if ((trimmed.startsWith("{") && trimmed.endsWith("}")) ||
                (trimmed.startsWith("[") && trimmed.endsWith("]")) ||
                (trimmed.startsWith("<") && trimmed.endsWith(">")) ||
                trimmed.contains("fun ") ||
                trimmed.contains("val ") ||
                trimmed.contains("var ") ||
                trimmed.contains("const ") ||
                trimmed.contains("function(") ||
                trimmed.contains("import ") ||
                trimmed.contains("SELECT ") ||
                trimmed.contains("class ")
            ) {
                return "code"
            }

            return "text"
        }
    }
}
