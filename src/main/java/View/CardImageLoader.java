package View;

import Model.CardsLists.Card;
import Model.CardsLists.CardElement;
import Model.Database.DataBaseUpdate;
import Utils.LruImageCache;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Manages asynchronous card-image loading for a single {@link CardTreeCell}.
 *
 * <p>One instance of this class is created per {@code CardTreeCell} in its
 * constructor and stored as {@link CardTreeCell#imageLoader}. The instance
 * holds references to the cell's size properties so images are loaded at the
 * correct dimensions, and tracks per-cell outstanding loads so they can be
 * cancelled when the cell is reused.</p>
 *
 * <p>The two underlying executor services and the path-resolution cache are
 * static (shared across all cells) because they are global resources: there
 * is only one thread pool for loading images and one for resolving paths,
 * regardless of how many cells are alive.</p>
 */
public final class CardImageLoader {

    /**
     * Cache from image key (typically the card's image-path token) to the
     * resolved {@code file:} URL on disk. Populated lazily as paths are
     * resolved; persists for the lifetime of the application.
     */
    static final ConcurrentHashMap<String, String> imagePathCache =
            new ConcurrentHashMap<>();

    // ── Shared static resources ───────────────────────────────────────────────
    private static final Logger logger = LoggerFactory.getLogger(CardImageLoader.class);
    /**
     * Loads {@link Image} objects from disk. Four threads allow several images
     * to load concurrently without starving the FX thread.
     */
    private static final ExecutorService imageLoadingExecutor =
            Executors.newFixedThreadPool(4);

    /**
     * Resolves image paths via {@link DataBaseUpdate#getAddresses} on a single
     * background thread to avoid hammering the file system with concurrent
     * directory scans.
     */
    private static final ExecutorService pathResolverExecutor =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "image-path-resolver");
                thread.setDaemon(true);
                return thread;
            });

    // ── Per-instance state ────────────────────────────────────────────────────
    /**
     * Tracks outstanding load futures keyed by the {@link ImageView} they will
     * update. Used to cancel stale loads when a cell is recycled before the
     * previous load completes.
     */
    final ConcurrentHashMap<ImageView, Future<?>> outstandingLoads =
            new ConcurrentHashMap<>();
    /**
     * No longer read for decode sizing — that now comes from
     * {@link Utils.CardImageResolution#getActiveDecodeWidth()}, a single global tier shared
     * by every consumer of this loader (see {@link #loadCardImage}). Kept, along with the
     * constructor parameters below, because this constructor is also called from
     * {@code CardScannerArtworkGallery} (itself constructed by
     * {@code Controller.CardScannerCoordinator}); changing the signature would ripple into
     * files outside this fix's scope for a cosmetic gain. Candidate for removal in a
     * follow-up cleanup pass.
     */
    private final DoubleProperty cardWidthProperty;
    private final DoubleProperty cardHeightProperty;

    // ── Placeholder ───────────────────────────────────────────────────────────

    /**
     * Creates a loader. {@code cardWidthProperty}/{@code cardHeightProperty} are stored but
     * no longer used for decode sizing (see the field javadoc above).
     *
     * @param cardWidthProperty  the cell's current card-width property
     * @param cardHeightProperty the cell's current card-height property
     */
    public CardImageLoader(
            DoubleProperty cardWidthProperty,
            DoubleProperty cardHeightProperty) {
        this.cardWidthProperty = cardWidthProperty;
        this.cardHeightProperty = cardHeightProperty;
    }

    /**
     * Returns the shared placeholder image shown while a card image is loading.
     */
    public static Image getPlaceholder() {
        return PlaceholderHolder.PLACEHOLDER;
    }

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * Derives the image-cache key for {@code item}.
     *
     * <p>Returns the card's {@code imagePath} field if available, falling back
     * to the element's {@code toString()} value, or {@code null} if neither
     * yields a usable string.</p>
     *
     * @param item the card element (may be {@code null})
     * @return the cache key, or {@code null}
     */
    public static String safeImageKey(CardElement item) {
        if (item == null) {
            return null;
        }
        try {
            Card card = item.getCard();
            if (card != null) {
                return card.getImagePath();
            }
        } catch (Exception ignored) {
        }
        try {
            String string = item.toString();
            if (string != null && !string.trim().isEmpty()) {
                return string.trim();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Shuts down both executor services immediately. Called from
     * {@link CardTreeCell#shutdownImageLoadingExecutor()} on application exit.
     */
    public static void shutdown() {
        imageLoadingExecutor.shutdownNow();
        pathResolverExecutor.shutdownNow();
    }

    /**
     * @return the current number of entries in {@link #imagePathCache} (diagnostic use — see
     * {@code Controller.CardScannerCoordinator}'s periodic memory-diagnostics log).
     */
    public static int pathCacheSize() {
        return imagePathCache.size();
    }

    /**
     * Loads the image for {@code cardElement} into {@code imageView}, going
     * through the path cache and LRU image cache before hitting disk.
     *
     * <p>This is the primary entry point for callers. It sets the placeholder
     * immediately (so the cell never shows a blank gap) and then kicks off
     * async resolution and loading as needed.</p>
     *
     * <p>The decode width ({@link Utils.CardImageResolution#getActiveDecodeWidth()}) is read
     * exactly once here and threaded through every step of this call, rather than re-read at
     * cache-write time — a zoom change landing mid-flight would otherwise read under one tier
     * and write under another, silently missing the cache on every subsequent lookup.</p>
     *
     * @param cardElement the card whose image to load
     * @param imageView   the view to update when the image is ready
     */
    public void loadCardImage(CardElement cardElement, ImageView imageView) {
        String imageKey = safeImageKey(cardElement);
        String cachedFullPath = imageKey == null ? null : imagePathCache.get(imageKey);
        int decodeWidth = Utils.CardImageResolution.getActiveDecodeWidth();

        if (cachedFullPath != null) {
            Image cached = LruImageCache.getImage(cachedFullPath, decodeWidth);
            if (cached != null) {
                imageView.setImage(cached);
            } else {
                imageView.setImage(getPlaceholder());
                Future<?> future = loadAsync(cardElement, imageView, cachedFullPath, decodeWidth);
                if (future != null) {
                    outstandingLoads.put(imageView, future);
                }
            }
        } else {
            imageView.setImage(getPlaceholder());
            resolvePathAsync(imageKey, resolvedPath -> {
                if (resolvedPath == null) {
                    return;
                }
                Image cached = LruImageCache.getImage(resolvedPath, decodeWidth);
                if (cached != null) {
                    Platform.runLater(() -> {
                        Object expected = imageView.getProperties().get("expectedImagePath");
                        if (Objects.equals(expected, resolvedPath) || expected == null) {
                            imageView.setImage(cached);
                            imageView.getProperties().remove("expectedImagePath");
                        }
                    });
                } else {
                    imageView.getProperties().put("expectedImagePath", resolvedPath);
                    Future<?> future = loadAsync(cardElement, imageView, resolvedPath, decodeWidth);
                    if (future != null) {
                        outstandingLoads.put(imageView, future);
                    }
                }
            });
        }
    }

    /**
     * Cancels {@code imageView}'s outstanding load, if any, and clears the expected-path
     * marker used to guard against a stale load overwriting a recycled cell.
     *
     * <p>Used by the viewport gate ({@code CardCellViewportRegistry}) when a cell scrolls out
     * of the retention band: without this, a fast scroll through several screens would leave
     * every crossed cell's load still queued on {@link #imageLoadingExecutor}, decoding images
     * nobody will see by the time they finish.</p>
     *
     * @param imageView the view whose load should be cancelled
     */
    public void cancelLoad(ImageView imageView) {
        Future<?> pending = outstandingLoads.remove(imageView);
        if (pending != null) {
            // false: the underlying `new Image(...)` decode isn't interruptible anyway —
            // cancel(true) would only add interrupt noise to the pool. This still prevents
            // any load that hasn't started yet from ever starting, which is the case that
            // matters during a fast scroll.
            pending.cancel(false);
        }
        imageView.getProperties().remove("expectedImagePath");
    }

    /**
     * Resolves the on-disk path for {@code imageKey}, first checking
     * {@link #imagePathCache} and then delegating to
     * {@link DataBaseUpdate#getAddresses} on the background resolver thread.
     * The resolved path (or {@code null} if not found) is delivered to
     * {@code callback} from the resolver thread.
     *
     * @param imageKey the cache key (may be {@code null})
     * @param callback receives the resolved {@code file:} URL, or {@code null}
     */
    void resolvePathAsync(String imageKey, Consumer<String> callback) {
        if (imageKey == null) {
            callback.accept(null);
            return;
        }
        String cached = imagePathCache.get(imageKey);
        if (cached != null) {
            callback.accept(cached);
            return;
        }
        pathResolverExecutor.submit(() -> {
            try {
                String[] addresses = DataBaseUpdate.getAddresses(imageKey + ".jpg");
                String resolved = null;
                if (addresses != null && addresses.length > 0) {
                    resolved = "file:" + addresses[0];
                    imagePathCache.put(imageKey, resolved);
                }
                callback.accept(resolved);
            } catch (Exception exception) {
                logger.warn("Failed to resolve image path for key {}", imageKey, exception);
                callback.accept(null);
            }
        });
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Submits a load of {@code resolvedPath} into {@code imageView} on the
     * image-loading executor. Checks the LRU cache first; on a cache miss,
     * creates a background {@link Image} and updates the view when complete.
     * Updates {@link #outstandingLoads} so stale loads can be cancelled.
     *
     * @param cardElement  the card being loaded (used for error logging only)
     * @param imageView    the target view
     * @param resolvedPath the {@code file:} URL to load
     * @param decodeWidth  the decode width to load and cache at (see
     *                     {@link Utils.CardImageResolution}) — captured once by the caller,
     *                     not re-read here, so a load started under one zoom tier can't write
     *                     under a different one if the zoom changes mid-flight
     * @return the submitted {@link Future}, or {@code null} if the image was
     * served from cache synchronously
     */
    Future<?> loadAsync(
            CardElement cardElement,
            ImageView imageView,
            String resolvedPath,
            int decodeWidth) {

        if (resolvedPath == null) {
            Platform.runLater(() -> imageView.setImage(getPlaceholder()));
            return null;
        }

        Image cached = LruImageCache.getImage(resolvedPath, decodeWidth);
        if (cached != null) {
            Platform.runLater(() -> {
                Object expected = imageView.getProperties().get("expectedImagePath");
                if (Objects.equals(expected, resolvedPath) || expected == null) {
                    imageView.setImage(cached);
                    imageView.getProperties().remove("expectedImagePath");
                }
            });
            return null;
        }

        imageView.getProperties().put("expectedImagePath", resolvedPath);

        AtomicReference<Future<?>> futureRef = new AtomicReference<>();
        Future<?> future = imageLoadingExecutor.submit(() -> {
            try {
                Image image = new Image(
                        resolvedPath,
                        decodeWidth,
                        Utils.CardImageResolution.decodeHeightFor(decodeWidth),
                        true, true, true);

                if (image.getProgress() >= 1.0) {
                    LruImageCache.addImage(resolvedPath, decodeWidth, image);
                    Platform.runLater(() -> {
                        Object expected = imageView.getProperties().get("expectedImagePath");
                        if (Objects.equals(expected, resolvedPath)) {
                            imageView.setImage(image);
                            imageView.getProperties().remove("expectedImagePath");
                        }
                    });
                } else {
                    Platform.runLater(() -> {
                        image.progressProperty().addListener((obs, oldValue, newValue) -> {
                            if (newValue.doubleValue() >= 1.0) {
                                LruImageCache.addImage(resolvedPath, decodeWidth, image);
                                Object expected =
                                        imageView.getProperties().get("expectedImagePath");
                                if (Objects.equals(expected, resolvedPath)) {
                                    imageView.setImage(image);
                                    imageView.getProperties().remove("expectedImagePath");
                                }
                            }
                        });
                    });
                }
            } catch (Exception exception) {
                String cardName = (cardElement != null && cardElement.getCard() != null)
                        ? cardElement.getCard().getName_EN()
                        : "unknown";
                logger.error("Error loading image for card {}", cardName, exception);
                Platform.runLater(() -> {
                    Object expected = imageView.getProperties().get("expectedImagePath");
                    if (expected == null || Objects.equals(expected, resolvedPath)) {
                        imageView.setImage(getPlaceholder());
                        imageView.getProperties().remove("expectedImagePath");
                    }
                });
            } finally {
                outstandingLoads.remove(imageView, futureRef.get());
            }
        });

        futureRef.set(future);
        outstandingLoads.put(imageView, future);
        return future;
    }

    /**
     * Holder-class idiom: the placeholder {@link Image} is loaded exactly once
     * on first access, before any background threads need it.
     */
    private static final class PlaceholderHolder {
        static final Image PLACEHOLDER =
                new Image("file:./src/main/resources/placeholder.jpg");
    }
}