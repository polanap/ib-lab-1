package org.example.iblab1.security;

/**
 * Защита от подделки логов (log injection, OWASP A09). Пользовательские данные могут
 * содержать перевод строки, с помощью которого в лог дописывается фальшивая запись —
 * например, строка «alice\n2026-09-19 ERROR Пользователь admin удалён». Перед записью
 * в лог все символы CR и LF заменяются, поэтому одно сообщение всегда остаётся одной строкой.
 */
public final class LogSanitizer {
    private LogSanitizer() {
    }

    public static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("[\\r\\n]", "_");
    }
}
