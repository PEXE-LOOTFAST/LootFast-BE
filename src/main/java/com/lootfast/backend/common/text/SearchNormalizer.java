package com.lootfast.backend.common.text;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class SearchNormalizer {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private SearchNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        return WHITESPACE.matcher(COMBINING_MARKS.matcher(decomposed)
                .replaceAll("")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT)
                .trim())
                .replaceAll(" ");
    }
}
