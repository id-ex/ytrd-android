package io.github.idex.ytrdroid.domain.quality;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class QualitySelectorTest {
    @Test
    public void selectsHighestAvailableNotAbovePreference() {
        assertEquals("720", QualitySelector.select("1080", Arrays.asList("480", "720", "1440")));
        assertEquals("480", QualitySelector.select("720", Arrays.asList("360", "480", "1080")));
    }

    @Test
    public void selectsMinimumWhenAllAvailableHeightsAreHigher() {
        assertEquals("1440", QualitySelector.select("1080", Arrays.asList("1440", "2160")));
    }

    @Test
    public void selectsExactMatch() {
        assertEquals("1080", QualitySelector.select("1080", Arrays.asList("720", "1080", "1440")));
    }

    @Test
    public void autoOrMissingAvailableQualitiesReturnsAuto() {
        assertEquals("auto", QualitySelector.select("auto", Arrays.asList("720", "1080")));
        assertEquals("auto", QualitySelector.select("1080", null));
        assertEquals("auto", QualitySelector.select("1080", Collections.<String>emptyList()));
    }

    @Test
    public void ignoresInvalidValuesAndDuplicates() {
        List<String> available = Arrays.asList("noise", "0", "-720", "1080", "1080", "720p", "720");
        assertEquals("720", QualitySelector.select("900", available));
        assertEquals("auto", QualitySelector.select("invalid", available));
        assertEquals("auto", QualitySelector.select("1080", Arrays.asList("bad", "0", "2147483648")));
    }
}
