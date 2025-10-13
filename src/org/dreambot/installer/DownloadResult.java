package org.dreambot.installer;

import java.nio.file.Path;
import java.util.Objects;

final class DownloadResult {

    private final boolean successful;
    private final String channelName;
    private final String url;
    private final Path file;
    private final long bytesDownloaded;
    private final String errorMessage;

    private DownloadResult(
        boolean successful,
        String channelName,
        String url,
        Path file,
        long bytesDownloaded,
        String errorMessage
    ) {
        this.successful = successful;
        this.channelName = channelName;
        this.url = url;
        this.file = file;
        this.bytesDownloaded = bytesDownloaded;
        this.errorMessage = errorMessage;
    }

    static DownloadResult success(String channelName, String url, Path file, long bytesDownloaded) {
        return new DownloadResult(true, channelName, url, Objects.requireNonNull(file), bytesDownloaded, null);
    }

    static DownloadResult failure(String channelName, String url, String error) {
        return new DownloadResult(false, channelName, url, null, 0L, error);
    }

    boolean isSuccessful() {
        return successful;
    }

    Path getFile() {
        return file;
    }

    long getBytesDownloaded() {
        return bytesDownloaded;
    }

    String getErrorMessage() {
        if (errorMessage != null) {
            return String.format("%s (%s): %s", channelName, url, errorMessage);
        }
        return String.format("%s (%s): unknown error", channelName, url);
    }
}
