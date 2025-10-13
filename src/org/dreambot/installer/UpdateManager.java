package org.dreambot.installer;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Objects;

final class UpdateManager {

    private static final int HTTP_TIMEOUT_MS = 20_000;

    private final InstallerConfig config;

    UpdateManager(InstallerConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    UpdateReport update(ChannelManifest manifest) {
        if (manifest.isEmpty()) {
            throw new InstallerException("Channel manifest does not contain any channels");
        }
        ensureScriptsDirectory();

        UpdateReport report = new UpdateReport();
        for (ChannelSource channel : manifest.getChannels()) {
            if (!channel.isValid()) {
                report.addError("Channel '" + channel.getName() + "' has no URLs configured");
                continue;
            }
            Path destination = determineDestination(manifest, channel);
            for (String url : channel.getUrls()) {
                DownloadResult result = tryDownload(channel, url);
                if (!result.isSuccessful()) {
                    report.addError(result.getErrorMessage());
                    continue;
                }

                if (channel.hasChecksum() && !verifyChecksum(result, channel.getSha256())) {
                    report.addError("Checksum verification failed for channel '" + channel.getName() + "' using " + url);
                    deleteQuietly(result.getFile());
                    continue;
                }

                if (!shouldReplaceExisting(destination, result.getFile())) {
                    deleteQuietly(result.getFile());
                    report.markUpToDate(channel.getName());
                    return report;
                }

                moveToDestination(result.getFile(), destination);
                report.markSuccess(channel.getName(), result.getBytesDownloaded());
                return report;
            }
        }
        return report;
    }

    private void ensureScriptsDirectory() {
        try {
            Files.createDirectories(config.getScriptsDirectory());
        } catch (IOException e) {
            throw new InstallerException("Unable to create DreamBot Scripts directory: " + e.getMessage(), e);
        }
    }

    private Path determineDestination(ChannelManifest manifest, ChannelSource channel) {
        String fileName = channel.getDestinationFileName();
        if (fileName == null || fileName.isEmpty()) {
            fileName = manifest.getEffectiveScriptFileName();
        }
        return config.getTargetScriptPath(fileName);
    }

    private DownloadResult tryDownload(ChannelSource channel, String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(HTTP_TIMEOUT_MS);
            connection.setReadTimeout(HTTP_TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", "RoguesDenInstaller/1.0");
            int status = connection.getResponseCode();
            if (status >= 200 && status < 300) {
                Path tempFile = Files.createTempFile("roguesden", ".jar");
                long bytes = copy(connection.getInputStream(), tempFile);
                return DownloadResult.success(channel.getName(), url, tempFile, bytes);
            }
            return DownloadResult.failure(channel.getName(), url, "HTTP status " + status);
        } catch (Exception e) {
            return DownloadResult.failure(channel.getName(), url, e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private long copy(InputStream inputStream, Path destination) throws IOException {
        try (InputStream in = inputStream) {
            return Files.copy(in, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private boolean verifyChecksum(DownloadResult result, String expectedSha256) {
        try (InputStream in = Files.newInputStream(result.getFile())) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream dis = new DigestInputStream(in, digest)) {
                byte[] buffer = new byte[8192];
                while (dis.read(buffer) != -1) {
                    // consume stream to compute checksum
                }
            }
            String calculated = toHex(digest.digest());
            return calculated.equalsIgnoreCase(expectedSha256);
        } catch (IOException | NoSuchAlgorithmException e) {
            return false;
        }
    }

    private boolean shouldReplaceExisting(Path destination, Path newFile) {
        if (!Files.exists(destination)) {
            return true;
        }
        try {
            long existingSize = Files.size(destination);
            long newSize = Files.size(newFile);
            if (existingSize != newSize) {
                return true;
            }
            byte[] existingHash = calculateSha256(destination);
            byte[] newHash = calculateSha256(newFile);
            return !MessageDigest.isEqual(existingHash, newHash);
        } catch (IOException | NoSuchAlgorithmException e) {
            return true;
        }
    }

    private byte[] calculateSha256(Path path) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(path); DigestInputStream dis = new DigestInputStream(in, digest)) {
            byte[] buffer = new byte[8192];
            while (dis.read(buffer) != -1) {
                // consume stream
            }
        }
        return digest.digest();
    }

    private String toHex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(String.format(Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }

    private void moveToDestination(Path source, Path destination) {
        try {
            Files.createDirectories(destination.getParent());
            Files.move(source, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new InstallerException("Unable to move downloaded file to destination: " + e.getMessage(), e);
        }
    }

    private void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
    }
}
