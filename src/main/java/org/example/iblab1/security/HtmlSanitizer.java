package org.example.iblab1.security;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Защита от XSS. Пользовательский текст очищается в два этапа:
 * 1. OWASP-санитайзер удаляет все HTML-теги и атрибуты (разметка не сохраняется),
 * 2. оставшийся простой текст экранируется перед отправкой в ответе API,
 * поэтому сохранённый payload вида {@code <script>alert(1)</script>} никогда не будет
 * отрисован клиентом как разметка.
 */
@Component
public class HtmlSanitizer {
    private static final PolicyFactory PLAIN_TEXT_POLICY = new HtmlPolicyBuilder().toFactory();

    /** Вырезает разметку из входящих данных перед сохранением */
    public String sanitize(String value) {
        if (value == null) {
            return null;
        }
        return HtmlUtils.htmlUnescape(PLAIN_TEXT_POLICY.sanitize(value)).trim();
    }

    /** Экранирует данные, которые записываются в ответ API */
    public String escape(String value) {
        if (value == null) {
            return null;
        }
        return HtmlUtils.htmlEscape(value);
    }
}
