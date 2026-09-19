package cn.apikeyscan.state;

import cn.apikeyscan.util.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ScanState {
    private static final int MAX_ENTRIES = 200_000;
    private final LinkedHashSet<String> objectKeys = new LinkedHashSet<>();
    private final LinkedHashSet<String> contentHashes = new LinkedHashSet<>();
    private String lastSuccessfulScan = "";

    public static ScanState load(Path path) throws IOException {
        ScanState state = new ScanState();
        if (!Files.exists(path)) return state;
        Object raw = Json.read(path);
        if (!(raw instanceof Map<?, ?> map)) return state;
        addStrings(state.objectKeys, map.get("object_keys"));
        addStrings(state.contentHashes, map.get("content_hashes"));
        Object last = map.get("last_successful_scan");
        if (last != null) state.lastSuccessfulScan = String.valueOf(last);
        return state;
    }

    public boolean hasObject(String key) { return objectKeys.contains(key); }
    public boolean hasContent(String hash) { return contentHashes.contains(hash); }
    public void addObject(String key) { addCapped(objectKeys, key); }
    public void addContent(String hash) { addCapped(contentHashes, hash); }
    public String lastSuccessfulScan() { return lastSuccessfulScan; }
    public int objectCount() { return objectKeys.size(); }
    public int contentCount() { return contentHashes.size(); }

    public void markSuccessfulNow() { lastSuccessfulScan = Instant.now().toString(); }

    public void save(Path path) throws IOException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("version", 1);
        data.put("last_successful_scan", lastSuccessfulScan);
        data.put("object_keys", new ArrayList<>(objectKeys));
        data.put("content_hashes", new ArrayList<>(contentHashes));
        Json.write(path, data);
    }

    private static void addStrings(Set<String> target, Object value) {
        if (value instanceof Iterable<?> list) {
            for (Object item : list) if (item != null) addCapped(target, String.valueOf(item));
        }
    }

    private static void addCapped(Set<String> target, String value) {
        if (value == null || value.isBlank()) return;
        target.add(value);
        while (target.size() > MAX_ENTRIES) {
            String first = target.iterator().next();
            target.remove(first);
        }
    }
}

