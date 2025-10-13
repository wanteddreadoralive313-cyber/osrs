package org.dreambot.installer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class UpdateReport {

    private final List<String> errors = new ArrayList<>();
    private boolean success;
    private boolean upToDate;
    private String successfulChannelName;
    private long downloadedBytes;

    void addError(String error) {
        errors.add(error);
    }

    void markSuccess(String channelName, long bytes) {
        this.success = true;
        this.successfulChannelName = channelName;
        this.downloadedBytes = bytes;
    }

    void markUpToDate(String channelName) {
        this.upToDate = true;
        this.successfulChannelName = channelName;
    }

    boolean isSuccess() {
        return success || upToDate;
    }

    boolean isUpToDate() {
        return upToDate;
    }

    String getSuccessfulChannelName() {
        return successfulChannelName;
    }

    long getDownloadedBytes() {
        return downloadedBytes;
    }

    List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }
}
