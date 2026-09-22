package io.github.idex.ytrdroid.data.storage;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.UUID;

import static org.junit.Assert.*;

public class StorageTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void workspaceManagesFilesAndMarkers() throws IOException {
        File filesDir = temp.newFolder("files");
        UUID taskId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();

        WorkspaceManager wm = new WorkspaceManager(filesDir, taskId, executionId);
        assertFalse(wm.isAudioComplete());
        assertFalse(wm.isVideoComplete());

        File audioFile = wm.getCompleteAudioFile();
        try (FileWriter w = new FileWriter(audioFile)) { w.write("mp3data"); }
        wm.markAudioComplete();
        assertTrue(wm.isAudioComplete());

        wm.cleanupExecution();
        assertFalse(wm.getExecutionDir().exists());
    }

    @Test
    public void destinationWriterResolvesNonCollidingNames() throws IOException {
        File outDir = temp.newFolder("out");
        File first = DestinationWriter.resolveUniqueFile(outDir, "video", "mp4");
        assertEquals("video.mp4", first.getName());
        assertTrue(first.createNewFile());

        File second = DestinationWriter.resolveUniqueFile(outDir, "video", "mp4");
        assertEquals("video (1).mp4", second.getName());
        assertTrue(second.createNewFile());

        File third = DestinationWriter.resolveUniqueFile(outDir, "video", "mp4");
        assertEquals("video (2).mp4", third.getName());
    }

    @Test
    public void copyAndVerifyCopiesFileAndCleansUpPart() throws IOException {
        File src = temp.newFile("source.mp4");
        try (FileWriter w = new FileWriter(src)) { w.write("content 12345"); }

        File destDir = temp.newFolder("dest");
        File dest = new File(destDir, "final.mp4");

        DestinationWriter.copyAndVerify(src, dest);
        assertTrue(dest.exists());
        assertEquals(src.length(), dest.length());
        assertFalse(new File(destDir, "final.mp4.part").exists());
    }

    @Test
    public void sanitizeReplacesForbiddenCharsAndTruncates() {
        assertEquals("valid_name", DestinationWriter.sanitize("valid/name"));
        assertEquals("a_b_c", DestinationWriter.sanitize("a:b*c"));
        String longName = "a".repeat(100);
        assertEquals(60, DestinationWriter.sanitize(longName).length());
    }
}
