package org.dreambot.installer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class ChannelListLoader {

    private static final String DEFAULT_MANIFEST_RESOURCE = "/org/dreambot/installer/channels/default-channels.json";
    private static final int HTTP_TIMEOUT_MS = 15_000;

    private final InstallerConfig config;
    private final ObjectMapper mapper;

    ChannelListLoader(InstallerConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    ChannelManifest loadAndRefresh() {
        Path manifestPath = config.getChannelManifestPath();
        ensureManifestExists(manifestPath);

        ChannelManifest currentManifest = readManifest(manifestPath);
        ChannelManifest updatedManifest = tryUpdateManifest(manifestPath, currentManifest);
        if (updatedManifest != null) {
            return updatedManifest;
        }
        return currentManifest;
    }

    private void ensureManifestExists(Path manifestPath) {
        if (Files.exists(manifestPath)) {
            return;
        }
        try (InputStream in = ChannelListLoader.class.getResourceAsStream(DEFAULT_MANIFEST_RESOURCE)) {
            if (in == null) {
                throw new InstallerException("Default manifest resource not found: " + DEFAULT_MANIFEST_RESOURCE);
            }
            Files.createDirectories(manifestPath.getParent());
            Files.copy(in, manifestPath);
        } catch (IOException e) {
            throw new InstallerException("Unable to copy default channel manifest: " + e.getMessage(), e);
        }
    }

    private ChannelManifest tryUpdateManifest(Path manifestPath, ChannelManifest currentManifest) {
        List<String> sources = new ArrayList<>();
        sources.addAll(currentManifest.getManifestSources());
        sources.addAll(config.getAdditionalChannelSources());
        if (sources.isEmpty()) {
            return null;
        }

        for (String source : sources) {
            ChannelManifest downloaded = downloadManifest(source);
            if (downloaded == null || downloaded.isEmpty()) {
                continue;
            }
            if (!areManifestsEqual(currentManifest, downloaded)) {
                writeManifest(manifestPath, downloaded);
                return downloaded;
            }
        }
        return null;
    }

    private ChannelManifest readManifest(Path manifestPath) {
        try {
            return mapper.readValue(manifestPath.toFile(), ChannelManifest.class);
        } catch (IOException e) {
            throw new InstallerException("Unable to parse channel manifest: " + e.getMessage(), e);
        }
    }

    private void writeManifest(Path manifestPath, ChannelManifest manifest) {
        try {
            mapper.writeValue(manifestPath.toFile(), manifest);
        } catch (IOException e) {
            throw new InstallerException("Unable to save channel manifest: " + e.getMessage(), e);
        }
    }

    private ChannelManifest downloadManifest(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(HTTP_TIMEOUT_MS);
            connection.setReadTimeout(HTTP_TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", "RoguesDenInstaller/1.0");
            int status = connection.getResponseCode();
            if (status >= 200 && status < 300) {
                try (InputStream in = connection.getInputStream()) {
                    return mapper.readValue(in, ChannelManifest.class);
                }
            }
        } catch (Exception e) {
            // Ignore and fall back to next URL.
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return null;
    }

    private boolean areManifestsEqual(ChannelManifest first, ChannelManifest second) {
        try {
            byte[] firstBytes = mapper.writeValueAsBytes(first);
            byte[] secondBytes = mapper.writeValueAsBytes(second);
            return MessageDigest.isEqual(firstBytes, secondBytes);
        } catch (IOException e) {
            return false;
        }
    }
}
