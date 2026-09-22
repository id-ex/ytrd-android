package io.github.idex.ytrdroid.domain.model;

/** Safe UI message is separate from diagnostics (which may contain sensitive URLs). */
public final class DownloadError {
    public enum Category { VALIDATION, NETWORK, TRANSLATION, MEDIA, STORAGE, INTERNAL }
    public enum Stage { QUEUE, TRANSLATION, DOWNLOAD, PROCESSING, PUBLICATION }
    public final Category category;
    public final Stage stage;
    public final boolean recoverable;
    public final String message;
    public final String diagnosticType;

    public DownloadError(Category category, Stage stage, boolean recoverable,
                         String message, String diagnosticType) {
        if (category == null || stage == null || message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("Error category, stage and message are required");
        }
        this.category = category;
        this.stage = stage;
        this.recoverable = recoverable;
        this.message = message;
        this.diagnosticType = diagnosticType;
    }
}
