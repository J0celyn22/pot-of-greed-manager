package Model.CardScanner;

import javafx.application.Platform;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for the frame-delivery coalescing added to
 * {@link PythonCardScannerBridge#handleFrameEvent} while investigating the scanner slowing down
 * over a long session (2026-08-24). Before this fix, every decoded frame got its own
 * {@link Platform#runLater} call regardless of whether the JavaFX application thread had caught
 * up on the last one — a burst of frames arriving faster than the FX thread drains them queued up
 * unboundedly instead of collapsing to "just show the latest one."
 */
class PythonCardScannerBridgeFrameCoalescingTest {

    @BeforeAll
    static void startFxToolkit() throws InterruptedException {
        CountDownLatch toolkitReady = new CountDownLatch(1);
        try {
            Platform.startup(toolkitReady::countDown);
        } catch (IllegalStateException alreadyStarted) {
            toolkitReady.countDown();
        }
        toolkitReady.await();
    }

    private static String tinyJpegBase64() throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream jpegBytes = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", jpegBytes);
        return Base64.getEncoder().encodeToString(jpegBytes.toByteArray());
    }

    private static JSONObject frameEvent(String jpegBase64) {
        JSONObject event = new JSONObject();
        event.put("type", "frame");
        event.put("jpeg_base64", jpegBase64);
        return event;
    }

    /**
     * Simulates the real scenario: the FX thread is busy with unrelated work (a card-add's view
     * refresh, in the real app) when a burst of frame events arrives. Occupies the FX thread with
     * a blocking {@code runLater} task first, so {@code deliverPendingFrame} is queued but can't
     * start yet — exactly the window where {@code frameDeliveryScheduled} must stay {@code true}
     * across the whole burst, coalescing it to exactly one delivery instead of one per event.
     */
    @Test
    void burstOfFrames_whileFxThreadBusyElsewhere_coalescesToOneDelivery() throws Exception {
        CountDownLatch fxThreadBusyStarted = new CountDownLatch(1);
        CountDownLatch releaseFxThread = new CountDownLatch(1);
        Platform.runLater(() -> {
            fxThreadBusyStarted.countDown();
            try {
                releaseFxThread.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(fxThreadBusyStarted.await(5, TimeUnit.SECONDS), "FX thread never picked up the blocking task");

        AtomicInteger deliveryCount = new AtomicInteger(0);
        CountDownLatch delivered = new CountDownLatch(1);
        PythonCardScannerBridge bridge = new PythonCardScannerBridge(
                image -> {
                    deliveryCount.incrementAndGet();
                    delivered.countDown();
                },
                errorMessage -> { },
                candidates -> { });

        String jpegBase64 = tinyJpegBase64();
        for (int frameIndex = 0; frameIndex < 20; frameIndex++) {
            bridge.handleFrameEvent(frameEvent(jpegBase64));
        }

        releaseFxThread.countDown();
        assertTrue(delivered.await(5, TimeUnit.SECONDS), "the coalesced frame was never delivered");
        // Give the FX thread a moment past the first delivery in case (incorrectly) more than one
        // was scheduled — asserting immediately after the latch could pass even with a bug if the
        // second delivery just hadn't run yet.
        Thread.sleep(200);
        assertEquals(1, deliveryCount.get(),
                "20 frames sent while the FX thread was busy elsewhere should coalesce to exactly 1 delivery");
        bridge.close(); // no-op (never started a subprocess), just releases the instance cleanly
    }

    /**
     * Once a burst has fully drained, the next frame event schedules its own new delivery rather
     * than being silently dropped because {@code frameDeliveryScheduled} was left stuck.
     */
    @Test
    void afterDelivery_nextFrame_isDeliveredToo() throws Exception {
        AtomicInteger deliveryCount = new AtomicInteger(0);
        CountDownLatch firstDelivered = new CountDownLatch(1);
        CountDownLatch secondDelivered = new CountDownLatch(2);
        PythonCardScannerBridge bridge = new PythonCardScannerBridge(
                image -> {
                    deliveryCount.incrementAndGet();
                    firstDelivered.countDown();
                    secondDelivered.countDown();
                },
                errorMessage -> { },
                candidates -> { });

        String jpegBase64 = tinyJpegBase64();
        bridge.handleFrameEvent(frameEvent(jpegBase64));
        assertTrue(firstDelivered.await(5, TimeUnit.SECONDS), "the first frame was never delivered");

        bridge.handleFrameEvent(frameEvent(jpegBase64));
        assertTrue(secondDelivered.await(5, TimeUnit.SECONDS),
                "a frame sent after the previous delivery finished should be delivered too");
        assertEquals(2, deliveryCount.get());
        bridge.close(); // no-op (never started a subprocess), just releases the instance cleanly
    }
}
