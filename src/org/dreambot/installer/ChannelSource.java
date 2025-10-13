package org.dreambot.installer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

final class ChannelSource {

    private final String name;
    private final List<String> urls;
    private final String sha256;
    private final String destinationFileName;

    @JsonCreator
    ChannelSource(
        @JsonProperty(value = "name", required = true) String name,
        @JsonProperty(value = "urls", required = true) List<String> urls,
        @JsonProperty(value = "sha256") String sha256,
        @JsonProperty(value = "destinationFileName") String destinationFileName
    ) {
        this.name = Objects.requireNonNull(name, "name");
        this.urls = urls == null ? Collections.emptyList() : new ArrayList<>(urls);
        this.sha256 = sha256 == null ? null : sha256.trim();
        this.destinationFileName = destinationFileName == null ? null : destinationFileName.trim();
    }

    String getName() {
        return name;
    }

    List<String> getUrls() {
        return Collections.unmodifiableList(urls);
    }

    boolean hasChecksum() {
        return sha256 != null && !sha256.isEmpty();
    }

    String getSha256() {
        return sha256;
    }

    String getDestinationFileName() {
        return destinationFileName;
    }

    boolean isValid() {
        return !urls.isEmpty();
    }
}
