package io.github.idex.ytrdroid.domain.quality;

import java.util.List;
import java.util.TreeSet;

/** Selects the closest available video height not exceeding the preferred height. */
public final class QualitySelector {
    private QualitySelector() {
    }

    public static String select(String preferred, List<String> available) {
        Integer preferredHeight = parseHeight(preferred);
        if (preferredHeight == null || available == null || available.isEmpty()) {
            return "auto";
        }

        TreeSet<Integer> heights = new TreeSet<>();
        for (String value : available) {
            Integer height = parseHeight(value);
            if (height != null) {
                heights.add(height);
            }
        }
        if (heights.isEmpty()) {
            return "auto";
        }
        if (heights.contains(preferredHeight)) {
            return preferredHeight.toString();
        }

        Integer lowerOrEqual = heights.floor(preferredHeight);
        return (lowerOrEqual != null ? lowerOrEqual : heights.first()).toString();
    }

    private static Integer parseHeight(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) < '0' || value.charAt(i) > '9') {
                return null;
            }
        }
        try {
            int height = Integer.parseInt(value);
            return height > 0 ? height : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
