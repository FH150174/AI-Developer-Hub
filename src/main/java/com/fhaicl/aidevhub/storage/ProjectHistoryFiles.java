package com.fhaicl.aidevhub.storage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.InvalidPropertiesFormatException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Locates a project's Notes or TODO XML, including v1.0 files keyed by the user's spelling. */
public final class ProjectHistoryFiles {
    public record Entry(Path file, String key) {}

    private static final Pattern HASHED_XML = Pattern.compile("[0-9a-f]{64}\\.xml");
    private final Path directory;
    // A verified SHA-256 filename binds one spelling. Cache only that metadata, never the history.
    private final Map<Path, String> knownOwners = new ConcurrentHashMap<>();

    public ProjectHistoryFiles(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    public Entry resolve(Path project) throws IOException {
        Path requested = project.toAbsolutePath().normalize();
        Entry exact = entry(requested.toString());
        if (File.separatorChar != '\\') return exact;

        Path actual = diskSpelling(requested);
        Entry preferred = actual == null ? exact : entry(actual.toString());
        Entry match = null;
        try (var files = Files.newDirectoryStream(directory, "*.xml")) {
            for (Path file : files) {
                Entry candidate;
                if (file.equals(exact.file())) candidate = exact;
                else if (file.equals(preferred.file())) candidate = preferred;
                else {
                    String owner = legacyOwner(file);
                    if (owner == null || !owner.equalsIgnoreCase(exact.key())) continue;
                    if (actual != null && !sameSpelling(actual, diskSpelling(Path.of(owner)))) continue;
                    candidate = new Entry(file, owner);
                }
                if (match != null) throw new HistoryConflictException(match.file(), file);
                match = candidate;
            }
        } catch (NoSuchFileException missingDirectory) {
            return preferred;
        } catch (DirectoryIteratorException failure) {
            throw failure.getCause();
        }
        if (match != null && actual == null && !match.key().equals(exact.key())) {
            throw new HistoryConflictException(match.file());
        }
        return match == null ? preferred : match;
    }

    // Compare actual component spellings, not Windows Path.equals (which also folds case-sensitive siblings).
    private static boolean sameSpelling(Path first, Path second) {
        return second != null && first.getRoot().equals(second.getRoot())
                && first.getRoot().relativize(first).toString().equals(second.getRoot().relativize(second).toString());
    }

    private static Path diskSpelling(Path path) throws IOException {
        try {
            Path real = path.toRealPath(LinkOption.NOFOLLOW_LINKS);
            return real.toString().equalsIgnoreCase(path.toString()) ? real : path;
        } catch (NoSuchFileException missingProject) {
            return null; // Without filesystem evidence only an exact v1.0 key is safe to use.
        }
    }

    private String legacyOwner(Path file) throws IOException {
        String cached = knownOwners.get(file);
        if (cached != null) return cached;
        if (!HASHED_XML.matcher(file.getFileName().toString()).matches()
                || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return null;
        Properties data = new Properties();
        try (var input = Files.newInputStream(file)) {
            data.loadFromXML(input);
        } catch (InvalidPropertiesFormatException | NoSuchFileException notAnAlias) {
            // Unrelated broken files have no usable owner; exact files are validated by their own store.
            return null;
        }
        String owner = data.getProperty("project.path");
        if (owner == null || !entry(owner).file().equals(file)) return null;
        knownOwners.put(file, owner);
        return owner;
    }

    private Entry entry(String key) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return new Entry(directory.resolve(HexFormat.of().formatHex(hash) + ".xml"), key);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Required SHA-256 algorithm is unavailable", exception);
        }
    }
}
