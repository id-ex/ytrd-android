package io.github.idex.ytrdroid.data.persistence;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class LegacyHistoryMigratorTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private static class FakeArtifactDao implements ArtifactDao {
        final List<ArtifactEntity> inserted = new ArrayList<>();
        @Override public void upsert(ArtifactEntity artifact) { inserted.add(artifact); }
        @Override public void upsertAll(List<ArtifactEntity> artifacts) { inserted.addAll(artifacts); }
        @Override public ArtifactEntity get(String id) { return null; }
        @Override public List<ArtifactEntity> getAllVisible() { return inserted; }
        @Override public void hide(String id) {}
        @Override public void delete(String id) {}
        @Override public int count() { return inserted.size(); }
    }

    @Test
    public void migratesValidJsonAndCreatesBackup() throws IOException {
        File jsonFile = temp.newFile("history.json");
        try (FileWriter w = new FileWriter(jsonFile)) {
            w.write("[\n" +
                    "  {\n" +
                    "    \"id\": \"12345\",\n" +
                    "    \"url\": \"https://youtube.com/watch?v=abcdefghijk\",\n" +
                    "    \"title\": \"Заголовок 1\",\n" +
                    "    \"filePath\": \"/storage/emulated/0/Download/ytrd/video1.mp4\",\n" +
                    "    \"timestamp\": 1600000000000,\n" +
                    "    \"isTranslated\": true\n" +
                    "  }\n" +
                    "]");
        }

        FakeArtifactDao dao = new FakeArtifactDao();
        LegacyHistoryMigrator migrator = new LegacyHistoryMigrator();
        boolean result = migrator.migrate(jsonFile, dao);

        assertTrue(result);
        assertEquals(1, dao.inserted.size());
        ArtifactEntity entity = dao.inserted.get(0);
        assertNotNull(entity.id);
        assertEquals("https://youtube.com/watch?v=abcdefghijk", entity.url);
        assertEquals("Заголовок 1", entity.title);
        assertEquals("/storage/emulated/0/Download/ytrd/video1.mp4", entity.uri);
        assertTrue(entity.isTranslated);

        // history.json should be renamed to history.json.bak
        assertFalse(jsonFile.exists());
        File bakFile = new File(jsonFile.getParentFile(), "history.json.bak");
        assertTrue(bakFile.exists());
    }

    @Test
    public void ignoresCorruptedOrEmptyJsonGracefully() throws IOException {
        File jsonFile = temp.newFile("corrupt.json");
        try (FileWriter w = new FileWriter(jsonFile)) {
            w.write("{not a list");
        }

        FakeArtifactDao dao = new FakeArtifactDao();
        LegacyHistoryMigrator migrator = new LegacyHistoryMigrator();
        boolean result = migrator.migrate(jsonFile, dao);

        assertFalse(result);
        assertEquals(0, dao.inserted.size());
        assertTrue(jsonFile.exists()); // not backed up on parse failure
    }

    @Test
    public void skipsInvalidOrEmptyItems() throws IOException {
        File jsonFile = temp.newFile("mixed.json");
        try (FileWriter w = new FileWriter(jsonFile)) {
            w.write("[\n" +
                    "  null,\n" +
                    "  {\"id\": \"1\", \"url\": \"\", \"filePath\": \"\"},\n" +
                    "  {\"id\": \"2\", \"url\": \"https://youtu.be/test\", \"title\": \"Valid\"}\n" +
                    "]");
        }

        FakeArtifactDao dao = new FakeArtifactDao();
        LegacyHistoryMigrator migrator = new LegacyHistoryMigrator();
        boolean result = migrator.migrate(jsonFile, dao);

        assertTrue(result);
        assertEquals(1, dao.inserted.size());
        assertEquals("Valid", dao.inserted.get(0).title);
    }
}
