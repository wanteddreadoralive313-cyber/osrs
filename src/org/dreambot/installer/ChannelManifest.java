package org.dreambot.installer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents the parsed channels.json manifest.
 */
final class ChannelManifest {

    private final String scriptFileName;
    private final List<String> manifestSources;
    private final List<ChannelSource> channels;

    @JsonCreator
    ChannelManifest(
        @JsonProperty(value = "scriptFileName", required = true) String scriptFileName,
        @JsonProperty(value = "manifestSources") List<String> manifestSources,
        @JsonProperty(value = "channels", required = true) List<ChannelSource> channels
    ) {
        this.scriptFileName = Objects.requireNonNull(scriptFileName, "scriptFileName");
        this.manifestSources = manifestSources == null ? Collections.emptyList() : List.copyOf(manifestSources);
        this.channels = channels == null ? Collections.emptyList() : new ArrayList<>(channels);
    }

    String getScriptFileName() {
        return scriptFileName;
    }

    String getEffectiveScriptFileName() {
        return scriptFileName == null || scriptFileName.trim().isEmpty() ? "RoguesDen.jar" : scriptFileName;
    }

    List<String> getManifestSources() {
        return manifestSources;
    }

    List<ChannelSource> getChannels() {
        return Collections.unmodifiableList(channels);
    }

    boolean isEmpty() {
        return channels.isEmpty();
    }
}
