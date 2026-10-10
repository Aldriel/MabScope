/*
 * Copyright 2026 Maxime Ethier - Consultant en Bioinformatique/Biocomputing Consultant
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bioinfo.mabscope.report;

import java.text.MessageFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;

/**
 * Texts of the report in one language, read from {@code report.properties} (English) and {@code
 * report_fr.properties} (French). Numbers and dates follow the conventions of the language.
 */
public final class ReportTexts {

    private static final String BUNDLE = "com.bioinfo.mabscope.report.report";

    private final Locale locale;
    private final ZoneId zone;
    private final ResourceBundle bundle;

    /**
     * Loads the texts of a language.
     *
     * @param locale language of the report; languages other than French fall back to English
     * @param zone time zone of the dates
     */
    public ReportTexts(Locale locale, ZoneId zone) {
        this.locale = Objects.requireNonNull(locale, "locale");
        this.zone = Objects.requireNonNull(zone, "zone");
        // No fallback to the default locale of the JVM: English must stay English on a French PC.
        this.bundle =
                ResourceBundle.getBundle(
                        BUNDLE,
                        locale,
                        ResourceBundle.Control.getNoFallbackControl(
                                ResourceBundle.Control.FORMAT_PROPERTIES));
    }

    /** Returns the language of the texts. */
    public Locale locale() {
        return locale;
    }

    /**
     * Returns a text.
     *
     * @param key key in the properties files
     * @param args values replacing {@code {0}}, {@code {1}}, ...
     * @return the formatted text
     * @throws java.util.MissingResourceException if the key is unknown
     */
    public String get(String key, Object... args) {
        return new MessageFormat(bundle.getString(key), locale).format(args);
    }

    /**
     * Returns the text of an enumerated value, such as {@code risk.HIGH}.
     *
     * @param prefix group of the key
     * @param value the value
     * @return the text
     */
    public String of(String prefix, Enum<?> value) {
        return get(prefix + "." + value.name());
    }

    /** Formats a percentage with one decimal, such as {@code 12.5%} or {@code 12,5 %}. */
    public String percent(double value) {
        return get("format.percent", String.format(locale, "%.1f", value));
    }

    /** Formats a difference in percentage points with one decimal. */
    public String points(double value) {
        return get("format.points", String.format(locale, "%.1f", value));
    }

    /** Formats a mass in Da with four decimals. */
    public String mass(double value) {
        return String.format(locale, "%.4f", value);
    }

    /** Formats a date and time. */
    public String dateTime(Instant instant) {
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(locale)
                .withZone(zone)
                .format(instant);
    }
}
