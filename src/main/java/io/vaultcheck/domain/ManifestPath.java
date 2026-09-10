package io.vaultcheck.domain;

import java.util.Locale;
import java.util.Set;

/** Canonical manifest syntax: relative Windows-compatible names separated by '/'. */
public record ManifestPath(String value) {
    private static final Set<String> DEVICES = Set.of("CON", "PRN", "AUX", "NUL", "CONIN$", "CONOUT$");
    public static final int MAX_LENGTH = 4096;
    public static final int MAX_DEPTH = 128;

    public ManifestPath {
        if (value == null || value.isEmpty() || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Invalid manifest path length");
        }
        String[] components = value.split("/", -1);
        if (components.length > MAX_DEPTH) throw new IllegalArgumentException("Path is too deep");
        for (String component : components) {
            if (component.isEmpty() || component.length() > 255 || component.equals(".")
                    || component.equals("..") || component.endsWith(".") || component.endsWith(" ")) {
                throw new IllegalArgumentException("Invalid path component");
            }
            for (int i = 0; i < component.length(); i++) {
                char c = component.charAt(i);
                if (Character.isISOControl(c) || "\\:<>\"|?*".indexOf(c) >= 0) {
                    throw new IllegalArgumentException("Forbidden path character");
                }
                if (Character.isHighSurrogate(c)) {
                    if (++i >= component.length() || !Character.isLowSurrogate(component.charAt(i))) {
                        throw new IllegalArgumentException("Malformed Unicode");
                    }
                } else if (Character.isLowSurrogate(c)) {
                    throw new IllegalArgumentException("Malformed Unicode");
                }
            }
            String stem = component.split("\\.", 2)[0].stripTrailing().toUpperCase(Locale.ROOT);
            if (DEVICES.contains(stem) || stem.matches("(?:COM|LPT)[1-9¹²³]")) {
                throw new IllegalArgumentException("Reserved Windows device name");
            }
        }
    }
}
