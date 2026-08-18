package com.github.squi2rel.vp.creation;

import java.util.Objects;
import java.util.function.Predicate;

public final class TextInputFilter {
    private TextInputFilter() {
    }

    public static boolean accepts(Predicate<String> filter, String value, int cursor, String highlighted, String insertion) {
        Objects.requireNonNull(filter, "filter");
        String current = value == null ? "" : value;
        String selected = highlighted == null ? "" : highlighted;
        String inserted = insertion == null ? "" : insertion;
        int safeCursor = Math.clamp(cursor, 0, current.length());
        int start = safeCursor;
        int end = safeCursor;

        if (!selected.isEmpty()) {
            if (safeCursor + selected.length() <= current.length()
                    && current.regionMatches(safeCursor, selected, 0, selected.length())) {
                end = safeCursor + selected.length();
            } else if (safeCursor - selected.length() >= 0
                    && current.regionMatches(safeCursor - selected.length(), selected, 0, selected.length())) {
                start = safeCursor - selected.length();
            } else {
                return false;
            }
        }

        return filter.test(current.substring(0, start) + inserted + current.substring(end));
    }
}
