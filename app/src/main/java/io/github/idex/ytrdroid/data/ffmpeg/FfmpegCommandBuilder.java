package io.github.idex.ytrdroid.data.ffmpeg;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure Java builder for FFmpeg merging commands.
 * Handles Mix, Dual, subtitles mapping and language metadata.
 */
public final class FfmpegCommandBuilder {
    private FfmpegCommandBuilder() {}

    public static List<String> buildMix(File videoInput, File audioInput, File subtitleInput,
                                        String originalLang, String container, File outputFile) {
        if (videoInput == null || audioInput == null || outputFile == null) {
            throw new IllegalArgumentException("Inputs and output must not be null");
        }
        List<String> args = new ArrayList<>();
        args.add("-y");
        args.add("-i"); args.add(videoInput.getAbsolutePath());
        args.add("-i"); args.add(audioInput.getAbsolutePath());

        boolean hasSub = subtitleInput != null && subtitleInput.exists();
        if (hasSub) {
            args.add("-i"); args.add(subtitleInput.getAbsolutePath());
        }

        // Keep duration of original video/audio (duration=first) so video isn't truncated
        args.add("-filter_complex");
        args.add("[0:a]volume=0.2[orig];[1:a]volume=1.2[dub];[orig][dub]amix=inputs=2:duration=first:dropout_transition=2[out]");
        args.add("-map"); args.add("0:v");
        args.add("-map"); args.add("[out]");
        args.add("-c:v"); args.add("copy");
        args.add("-c:a"); args.add("aac");
        args.add("-b:a"); args.add("128k");

        appendSubtitles(args, hasSub, subtitleInput, container);

        args.add("-progress");
        args.add("pipe:1");
        args.add(outputFile.getAbsolutePath());

        return Collections.unmodifiableList(args);
    }

    public static List<String> buildDual(File videoInput, File audioInput, File subtitleInput,
                                         String originalLang, String container, File outputFile) {
        if (videoInput == null || audioInput == null || outputFile == null) {
            throw new IllegalArgumentException("Inputs and output must not be null");
        }
        List<String> args = new ArrayList<>();
        args.add("-y");
        args.add("-i"); args.add(videoInput.getAbsolutePath());
        args.add("-i"); args.add(audioInput.getAbsolutePath());

        boolean hasSub = subtitleInput != null && subtitleInput.exists();
        if (hasSub) {
            args.add("-i"); args.add(subtitleInput.getAbsolutePath());
        }

        args.add("-map"); args.add("0:v");
        args.add("-map"); args.add("0:a");
        args.add("-map"); args.add("1:a");
        args.add("-c:v"); args.add("copy");
        args.add("-c:a"); args.add("copy");

        String origIso = (originalLang != null && !originalLang.trim().isEmpty())
                ? toIso639_2(originalLang.trim()) : "und";
        args.add("-metadata:s:a:0"); args.add("title=Оригинал");
        args.add("-metadata:s:a:0"); args.add("language=" + origIso);
        args.add("-metadata:s:a:1"); args.add("title=Перевод");
        args.add("-metadata:s:a:1"); args.add("language=rus");

        appendSubtitles(args, hasSub, subtitleInput, container);

        args.add("-progress");
        args.add("pipe:1");
        args.add(outputFile.getAbsolutePath());

        return Collections.unmodifiableList(args);
    }

    private static void appendSubtitles(List<String> args, boolean hasSub, File subtitleInput, String container) {
        if (!hasSub) return;
        args.add("-map");
        args.add("2:s:0");
        boolean isMkv = "mkv".equalsIgnoreCase(container);
        args.add("-c:s");
        args.add(isMkv ? "srt" : "mov_text");
        args.add("-metadata:s:s:0");
        String name = subtitleInput.getName().toLowerCase(java.util.Locale.ROOT);
        args.add("language=" + (name.contains(".ru") ? "rus" : "eng"));
    }

    private static String toIso639_2(String code) {
        String c = code.toLowerCase(java.util.Locale.ROOT);
        switch (c) {
            case "en": return "eng";
            case "ru": return "rus";
            case "de": return "deu";
            case "fr": return "fra";
            case "es": return "spa";
            case "it": return "ita";
            case "ja": return "jpn";
            case "zh": return "zho";
            case "ko": return "kor";
            case "ar": return "ara";
            default: return c.length() == 3 ? c : "und";
        }
    }
}
