package br.com.holding.payments.config;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.regex.Pattern;

/**
 * Logback converter that masks PII (CPF, CNPJ, credit card numbers, emails)
 * in log messages before they are written to output.
 *
 * Usage in logback-spring.xml:
 *   <conversionRule conversionWord="maskedMsg"
 *       converterClass="br.com.holding.payments.config.PiiMaskingConverter"/>
 *   <pattern>%d ... %maskedMsg%n</pattern>
 */
public class PiiMaskingConverter extends ClassicConverter {

    // CPF: 123.456.789-09 or 12345678909
    private static final Pattern CPF_PATTERN = Pattern.compile(
            "\\b(\\d{3})\\.?(\\d{3})\\.?(\\d{3})[-.]?(\\d{2})\\b");

    // CNPJ: 12.345.678/0001-99 or 12345678000199
    private static final Pattern CNPJ_PATTERN = Pattern.compile(
            "\\b(\\d{2})\\.?(\\d{3})\\.?(\\d{3})[/.]?(\\d{4})[-.]?(\\d{2})\\b");

    // Credit card: 4111 1111 1111 1111 or 4111111111111111
    private static final Pattern CARD_PATTERN = Pattern.compile(
            "\\b(\\d{4})[ -]?(\\d{4})[ -]?(\\d{4})[ -]?(\\d{4})\\b");

    // Email
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "\\b([a-zA-Z0-9._%+-]+)@([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})\\b");

    @Override
    public String convert(ILoggingEvent event) {
        String message = event.getFormattedMessage();
        if (message == null) return null;

        message = CPF_PATTERN.matcher(message).replaceAll("***.$2.***-**");
        message = CNPJ_PATTERN.matcher(message).replaceAll("**.$2.***/$4-**");
        message = CARD_PATTERN.matcher(message).replaceAll("$1-****-****-$4");
        message = EMAIL_PATTERN.matcher(message).replaceAll("***@$2");

        return message;
    }
}
