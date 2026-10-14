package io.github.idex.ytrdroid.data.updater;

import org.junit.Test;

import io.github.idex.ytrdroid.ui.AppUpdateDialog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ChangelogParserTest {

    @Test
    public void extractsExplicitAppTagBlock() {
        String md = "# Title\n\nIntro text\n\n<!-- app -->\n- Short feature 1\n- Short feature 2\n<!-- /app -->\n\n## Full Details\n- Detail A\n- Detail B";
        String extracted = AppUpdateDialog.extractShortChangelog(md);
        assertEquals("- Short feature 1\n- Short feature 2", extracted);
    }

    @Test
    public void extractsBulletsFromWhatsNew() {
        String md = "# Title\n\n## Что нового:\n- Feature 1\n- Feature 2\n- Feature 3\n\n## Состав сборки\n- File 1";
        String extracted = AppUpdateDialog.extractShortChangelog(md);
        assertTrue(extracted.contains("- Feature 1"));
        assertTrue(extracted.contains("- Feature 2"));
        assertTrue(extracted.contains("- Feature 3"));
        assertTrue(!extracted.contains("Состав сборки"));
    }
}
