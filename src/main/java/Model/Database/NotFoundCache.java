package Model.Database;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static Model.FilePaths.databaseDir;

/**
 * Persistent cache of Konami IDs that the remote database does not recognise
 * (HTTP 400 / 404).  Avoids hammering the server with the same failing request
 * on every startup.
 *
 * <p>The cache is stored as a JSON file at
 * {@code <databaseDir>/ygoprodeck/konamiId/not_found_cache.json} in the form
 * {@code {"9248": <epoch-millis>, ...}}.  An entry expires after
 * {@link #RETRY_DAYS} days, at which point the ID is tried again automatically.
 *
 * <p>Call {@link #clearAll()} to force an immediate retry of every cached ID
 * (hook this to the "force reload" button when it is added).
 */
public class NotFoundCache {

    private static final Logger logger = LoggerFactory.getLogger(NotFoundCache.class);

    /**
     * Number of days to wait before retrying a previously-failed Konami ID.
     * Change this constant to adjust the retry window.
     */
    public static final int RETRY_DAYS = 7;

    private static final Path CACHE_FILE = databaseDir.resolve(
            Paths.get("ygoprodeck", "konamiId", "not_found_cache.json"));

    // Lazy-loaded; null means "not yet read from disk".
    private static JSONObject cache = null;

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Returns {@code true} when {@code konamiId} is in the cache <em>and</em>
     * its entry has not yet expired, meaning we should skip the fetch entirely.
     */
    public static boolean isKnownNotFound(int konamiId) {
        String key = String.valueOf(konamiId);
        JSONObject c = getCache();
        if (!c.has(key)) return false;
        Instant recorded = Instant.ofEpochMilli(c.getLong(key));
        // Still within the retry window → treat as not-found.
        return Instant.now().isBefore(recorded.plus(RETRY_DAYS, ChronoUnit.DAYS));
    }

    /**
     * Records {@code konamiId} as not found at the current time and persists
     * the cache to disk immediately.
     */
    public static void markAsNotFound(int konamiId) {
        getCache().put(String.valueOf(konamiId), Instant.now().toEpochMilli());
        save();
        logger.debug("NotFoundCache: marked Konami ID {} as not found (will retry in {} days).", konamiId, RETRY_DAYS);
    }

    /**
     * Removes all entries from the cache and persists the empty state.
     * Every Konami ID will be retried on the next startup.
     * Hook this to the "force reload" button.
     */
    public static void clearAll() {
        cache = new JSONObject();
        save();
        logger.debug("NotFoundCache: cleared — all IDs will be retried.");
    }

    /**
     * Returns the number of IDs currently in the cache (expired or not).
     * Useful for diagnostics.
     */
    public static int size() {
        return getCache().length();
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private static JSONObject getCache() {
        if (cache == null) load();
        return cache;
    }

    private static void load() {
        cache = new JSONObject();
        if (!Files.exists(CACHE_FILE)) return;
        try {
            String content = Files.readString(CACHE_FILE, StandardCharsets.UTF_8);
            cache = new JSONObject(content);
            logger.debug("NotFoundCache: loaded {} {} from disk.", cache.length(), cache.length() == 1 ? "entry" : "entries");
        } catch (Exception e) {
            logger.warn("NotFoundCache: could not load cache — {}", e.getMessage());
        }
    }

    private static void save() {
        try {
            Files.createDirectories(CACHE_FILE.getParent());
            Files.writeString(CACHE_FILE, cache.toString(2), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.warn("NotFoundCache: could not save cache — {}", e.getMessage());
        }
    }
}