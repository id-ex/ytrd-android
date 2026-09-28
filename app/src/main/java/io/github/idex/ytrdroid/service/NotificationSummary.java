package io.github.idex.ytrdroid.service;

import io.github.idex.ytrdroid.domain.model.TaskSnapshot;

import java.util.Locale;

/** Pure-Java formatting for concise download notification text. */
public final class NotificationSummary {
    private static final double BYTES_PER_MB = 1024.0 * 1024.0;

    private NotificationSummary() { }

    public static String title(TaskSnapshot task) {
        String videoTitle = task.request.title;
        if (videoTitle != null && !videoTitle.trim().isEmpty()) return videoTitle.trim();
        return task.request.url;
    }

    public static String stage(TaskSnapshot task) {
        if (task.state == TaskSnapshot.State.PAUSING || task.state == TaskSnapshot.State.PAUSED) {
            return "На паузе";
        }
        if (task.state == TaskSnapshot.State.ANALYZING) return "Анализ видео";
        String stage = task.stageText;
        if (stage == null || stage.trim().isEmpty()) stage = defaultStage(task.state);
        return stage.trim();
    }

    public static String metrics(TaskSnapshot task) {
        StringBuilder result = new StringBuilder();
        if (task.totalBytes > 0) {
            result.append(String.format(Locale.ROOT, "%.1f/%.1f МБ",
                    task.downloadedBytes / BYTES_PER_MB, task.totalBytes / BYTES_PER_MB));
        } else if (task.downloadedBytes > 0) {
            result.append(String.format(Locale.ROOT, "%.1f МБ",
                    task.downloadedBytes / BYTES_PER_MB));
        }
        if (task.state == TaskSnapshot.State.DOWNLOADING && task.speed > 0) {
            appendMetric(result, String.format(Locale.ROOT, "%.1f МБ/с", task.speed / BYTES_PER_MB));
        }
        if (task.progress >= 0 && task.state != TaskSnapshot.State.ANALYZING
                && task.state != TaskSnapshot.State.TRANSLATING) {
            appendMetric(result, String.format(Locale.ROOT, "%.0f%%", task.progress));
        }
        return result.toString();
    }

    public static String details(TaskSnapshot task) {
        String stage = stage(task);
        String metrics = metrics(task);
        return metrics.isEmpty() ? stage : stage + " · " + metrics;
    }

    private static void appendMetric(StringBuilder result, String metric) {
        if (result.length() > 0) result.append(" · ");
        result.append(metric);
    }

    private static String defaultStage(TaskSnapshot.State state) {
        switch (state) {
            case PAUSING:
            case PAUSED: return "На паузе";
            case ANALYZING: return "Анализ видео";
            case TRANSLATING: return "Перевод…";
            case DOWNLOADING: return "Скачивание…";
            case PROCESSING: return "Обработка…";
            case ERROR: return "Ошибка";
            case DONE: return "Готово";
            case CANCELLING: return "Отмена…";
            case CANCELLED: return "Отменено";
            case INTERRUPTED: return "Прервано";
            case QUEUED: return "В очереди";
            default: return state.name();
        }
    }
}
