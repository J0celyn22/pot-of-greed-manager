"""Long-lived stdout-streaming sidecar for the camera card scanner's live preview and detection.

Unit 4 of the camera card-scanner feature (see docs/camera-scanner-plan.md) proved the
Python-to-Java live video path end-to-end with a "dumb" feed only. Unit 6 adds the actual
detection step: a throttled subset of captured frames also gets run through OCR, and whatever
text is read with confidence above OCR_CONFIDENCE_THRESHOLD is pushed to Java as a "detection"
event. This script still only reports raw recognized text — resolving that text into an actual
Card, deciding whether it's confident enough to add, and the add/lock/release debounce logic all
live on the Java side (see Controller.CardScannerCoordinator and Model.CardScanner.
ScanLockDebouncer) so this stays a thin, replaceable OCR sensor rather than growing its own copy
of the app's card-matching logic.

Protocol: unlike python/cardmarket_bridge.py, which is strictly request-in / response-out, this
script's stdout is an ASYNC EVENT STREAM — one JSON object per line, pushed by this process on
its own schedule, not in response to a specific request:
  {"type": "status", "status": "camera_opened"}
  {"type": "frame", "jpeg_base64": "<base64-encoded JPEG bytes>"}
  {"type": "detection", "candidates": [{"text": "...", "confidence": 0.87}, ...]}
  {"type": "error", "message": "<description>"}
A "detection" event's candidates list is ordered highest-confidence first and may be empty (a
detection cycle ran but nothing crossed OCR_CONFIDENCE_THRESHOLD) — it is never omitted or
skipped just because it's empty, since Java's debounce logic needs to see "nothing detected this
cycle" as its own event, not infer it from a gap between frame events.
Java's write side (stdin here) stays request-shaped, but one-way — no response line is paired
with a command:
  {"action": "shutdown"}
Because the main thread's time is spent in the camera-capture loop, not in a blocking read on
stdin, a dedicated background thread listens for stdin commands throughout this process's life;
it does not read every camera frame itself (see listen_for_commands()/main() below).

Which camera to open is set once at startup via a `--camera-index N` argument (default 0), not a
command this long-lived process listens for — Unit 10 added a "choose camera" button on the Java
side, but switching cameras restarts the whole sidecar on a new index rather than reopening the
device mid-session (see Controller.CardScannerCoordinator's own comments for why).

A separate, short-lived invocation with a `--list-cameras` argument skips the whole camera-open/
streaming flow above entirely: it probes a handful of device indices, reports which ones opened
successfully as a single `{"type": "camera_list", "cameras": [...]}` line, and exits. This is
deliberately its own process rather than a command sent to an already-running instance of this
script — that instance's process only exists at all if its own camera already opened
successfully (see main() below), so a command-based approach couldn't discover a working camera
in the case that actually matters most: the default camera failing to open at all. See
list_available_cameras() for the probe itself.

Same stdout-fd-corruption fix as cardmarket_bridge.py, and for the same category of reason:
opencv-python (and, as of Unit 6, rapidocr/onnxruntime) is exactly the kind of native-backed
library (camera-backend init banners, codec warnings, ONNX Runtime session-creation logging) that
can write straight to the real stdout file descriptor regardless of what sys.stdout points at in
Python — see that script's own docstring for the full incident this was traced from. The fix has
to run before the cv2 and rapidocr imports below, for the same reason it has to run before
cardmarket_bridge.py's seleniumbase import: once a third-party import has a chance to write a
startup banner, it is too late to redirect it.
"""

import os
import sys

# Duplicate the real stdout file descriptor before the cv2 import below gets a chance to write
# to it, then repoint fd 1 (and the Python-level sys.stdout object) at stderr for the rest of
# this process's life. send_response() is the only function allowed to write through STDOUT_FD
# — see cardmarket_bridge.py's docstring for the full rationale, which applies here unchanged.
STDOUT_FD = os.dup(sys.stdout.fileno())
os.dup2(sys.stderr.fileno(), sys.stdout.fileno())
sys.stdout = open(1, "w", buffering=1, closefd=False)

import base64  # noqa: E402 - after the fd redirection above, which must run first
import json  # noqa: E402
import threading  # noqa: E402
import time  # noqa: E402

import cv2  # noqa: E402
from rapidocr import RapidOCR  # noqa: E402

# Which OS camera device to open when --camera-index isn't given on the command line — see
# parse_camera_index_arg(). Kept as a named default rather than an inline literal so main() and
# this comment stay in one place.
DEFAULT_CAMERA_INDEX = 0

# How many camera indices list_available_cameras() probes (0 up to, but not including, this
# value). Starting point covering "built-in webcam plus one or two USB cameras" — a machine with
# more than this many cameras attached is an edge case not worth a configurable option for yet.
MAX_CAMERAS_TO_PROBE = 5

# Preview frame rate sent to Java, independent of the detection rate below. Starting point per
# the project's plan doc; tune once this is actually running against real hardware.
TARGET_PREVIEW_FPS = 12

# JPEG quality (0-100) for preview frames. Preview only needs to look reasonable on screen, not
# be detection-grade, so this favors smaller/faster frames over maximum fidelity.
JPEG_QUALITY = 70

# How often OCR actually runs, independent of TARGET_PREVIEW_FPS — OCR is far slower than
# encoding a JPEG, and running it on every previewed frame would drag preview smoothness down to
# OCR speed. Starting value; the real bottleneck here is per-frame OCR inference time, which is
# explicitly a Unit 7 tuning concern per the plan doc, not something to pre-optimize now.
TARGET_DETECTION_FPS = 4
PREVIEW_FRAMES_PER_DETECTION = max(1, round(TARGET_PREVIEW_FPS / TARGET_DETECTION_FPS))

# Minimum OCR confidence (RapidOCR's own per-line score, 0-1) for a recognized text line to be
# forwarded to Java at all. Starting value, not tuned against any real scan yet — see
# Model.Database.CardNameIndex's own javadoc for the matching threshold on the Java side
# (edit-distance for print codes), which has the same "needs real data to tune" caveat.
OCR_CONFIDENCE_THRESHOLD = 0.6

# Caps how many recognized lines get sent per detection cycle, so a frame full of flavor-text
# noise doesn't turn into an oversized "candidates" array Java has to try matching one by one.
MAX_DETECTION_CANDIDATES = 5

# Constructed once, lazily, by get_ocr_engine() below and reused for the process's whole life —
# RapidOCR() loads its models on construction (auto-downloaded to the package directory on the
# very first run anywhere on the machine, then cached locally), which is too slow to redo on
# every detection cycle. Stays None if construction ever fails, so detection can be cleanly
# disabled for the session without taking the whole sidecar (and the still-working preview) down
# with it.
ocr_engine = None
ocr_engine_failed = False

shutdown_event = threading.Event()


def log(message):
    """Diagnostic output goes to stderr; STDOUT_FD (the real, untouched stdout) is reserved for
    the JSON event stream via send_response() — same discipline as cardmarket_bridge.py's log().
    """
    print(message, file=sys.stderr, flush=True)


def send_response(response_dict):
    """The only function in this script allowed to write to the real stdout (STDOUT_FD). Writes
    and flushes directly against the duplicated file descriptor, bypassing Python's own stdout
    buffering/object layer entirely — same approach as cardmarket_bridge.py's send_response().
    """
    line = (json.dumps(response_dict) + "\n").encode("utf-8")
    os.write(STDOUT_FD, line)


def get_ocr_engine():
    """Lazily constructs and caches the module-level OCR engine on first use, so model loading
    happens once, after the webcam is already confirmed open, rather than delaying startup (or
    failing outright) before Java even knows the camera worked. Returns None if construction
    fails, logging the failure once rather than retrying (and re-failing) every detection cycle.

    Uses RapidOCR's default model configuration — Chinese and English text recognition, which
    also covers accented Latin-script text (French, Spanish, German, Italian, Portuguese) at
    reduced accuracy for the accented characters specifically, tolerable here since matching
    on the Java side already strips diacritics before comparing (see
    Utils.CardTextMatcher#normalizeForNameCompare / Utils.CardNameUtils#normalizeForCompare).
    Japanese and Korean card text will not read reliably against this default configuration —
    RapidOCR supports dedicated language packs for those (Rec.lang_type), but wiring one up needs
    a real decision about which non-Latin languages actually matter for this collection, which is
    a Unit 7-style follow-up rather than a default to guess at here.
    """
    global ocr_engine, ocr_engine_failed
    if ocr_engine is not None or ocr_engine_failed:
        return ocr_engine
    try:
        ocr_engine = RapidOCR()
        log("OCR engine loaded.")
    except Exception as ocr_load_error:  # noqa: BLE001 - any failure here should degrade, not crash
        ocr_engine_failed = True
        send_response({
            "type": "error",
            "message": f"Could not load the OCR engine; card detection is disabled for this "
                       f"session, but the preview will keep working. ({ocr_load_error})",
        })
        log(f"Failed to load OCR engine: {ocr_load_error}")
    return ocr_engine


def run_detection(frame):
    """Runs OCR on a single frame and returns the recognized text lines whose confidence clears
    OCR_CONFIDENCE_THRESHOLD, ordered highest-confidence first and capped at
    MAX_DETECTION_CANDIDATES. Returns an empty list (never None) if the engine isn't available or
    nothing on the frame was read confidently — an empty result is a normal, expected outcome for
    most detection cycles (most frames aren't holding a card up to the camera), not an error.
    """
    engine = get_ocr_engine()
    if engine is None:
        return []

    try:
        result = engine(frame)
    except Exception as ocr_call_error:  # noqa: BLE001 - one bad frame shouldn't end detection
        log(f"OCR call failed on a frame, skipping this detection cycle: {ocr_call_error}")
        return []

    if not result or not result.txts:
        return []

    scored_lines = [
        (text.strip(), score) for text, score in zip(result.txts, result.scores)
        if text and text.strip() and score >= OCR_CONFIDENCE_THRESHOLD
    ]
    scored_lines.sort(key=lambda scored_line: scored_line[1], reverse=True)
    return scored_lines[:MAX_DETECTION_CANDIDATES]


def parse_camera_index_arg():
    """Reads a `--camera-index N` argument from sys.argv, defaulting to DEFAULT_CAMERA_INDEX if
    it's absent or its value isn't a plain integer. A malformed value is logged and treated the
    same as an absent one rather than crashing the sidecar over a bad argument.
    """
    for argument_index, argument in enumerate(sys.argv):
        if argument == "--camera-index" and argument_index + 1 < len(sys.argv):
            raw_value = sys.argv[argument_index + 1]
            try:
                return int(raw_value)
            except ValueError:
                log(f"Ignoring malformed --camera-index value {raw_value!r}; using "
                    f"{DEFAULT_CAMERA_INDEX}.")
                return DEFAULT_CAMERA_INDEX
    return DEFAULT_CAMERA_INDEX


def list_available_cameras():
    """Probes camera indices 0 through MAX_CAMERAS_TO_PROBE - 1 by attempting to open, then
    immediately releasing, each one, and returns whichever indices actually opened. Runs to
    completion and returns rather than streaming anything — called once from main()'s
    `--list-cameras` branch, not from the ordinary frame-streaming path.

    Deliberately synchronous and blocking: this only ever runs from the short-lived
    `--list-cameras` invocation (see this script's module docstring), which has nothing else to
    do while probing, unlike the main streaming loop where a slow blocking call would stall live
    preview frames.
    """
    available_camera_indices = []
    for camera_index in range(MAX_CAMERAS_TO_PROBE):
        probe_capture = cv2.VideoCapture(camera_index)
        if probe_capture.isOpened():
            available_camera_indices.append(camera_index)
        probe_capture.release()
    return available_camera_indices


def listen_for_commands():
    """Runs on its own thread for this process's whole life, reading one-way commands from
    stdin. Has to be a separate thread rather than a periodic non-blocking check on the main
    thread, because the main thread spends nearly all of its time blocked inside
    VideoCapture.read() — a blocking stdin readline() there would stall frame capture, and a
    non-blocking poll would need to interrupt that read on every loop iteration for no benefit.
    Currently only understands "shutdown"; unrecognized actions are logged and ignored rather
    than treated as fatal, so a future command type can be added on the Java side first without
    breaking an older running instance of this script.
    """
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            request = json.loads(line)
        except json.JSONDecodeError as decode_error:
            log(f"Bad JSON command, ignoring: {decode_error}")
            continue

        action = request.get("action")
        if action == "shutdown":
            log("Shutdown requested.")
            shutdown_event.set()
            return
        log(f"Unknown action, ignoring: {action!r}")

    # stdin closed without an explicit shutdown command — the parent Java process most likely
    # exited or killed its end of the pipe. Treat that the same as an explicit shutdown rather
    # than leaving the camera loop running forever with no one left to send frames to.
    log("stdin closed without an explicit shutdown request; stopping.")
    shutdown_event.set()


def stream_preview_frames(capture):
    """Reads frames from the already-opened capture and sends each as a throttled JSON "frame"
    event, until shutdown_event is set. A per-frame failed read is reported as a non-fatal
    "error" event and skipped, rather than treated as a reason to stop the whole stream — a
    single dropped frame (camera momentarily busy, a driver hiccup) shouldn't end the session.

    Every PREVIEW_FRAMES_PER_DETECTION-th captured frame also gets run through OCR and sent as a
    "detection" event, in addition to (not instead of) that frame's ordinary "frame" event — see
    TARGET_DETECTION_FPS's own comment for why this runs at a separate, slower cadence than
    preview.
    """
    frame_interval_seconds = 1.0 / TARGET_PREVIEW_FPS
    frames_since_last_detection = 0

    while not shutdown_event.is_set():
        frame_start_time = time.time()

        frame_read_ok, frame = capture.read()
        if not frame_read_ok:
            send_response({"type": "error", "message": "Failed to read a frame from the webcam."})
            time.sleep(frame_interval_seconds)
            continue

        encode_ok, jpeg_bytes = cv2.imencode(".jpg", frame, [cv2.IMWRITE_JPEG_QUALITY, JPEG_QUALITY])
        if not encode_ok:
            log("Failed to JPEG-encode a captured frame; skipping it.")
            continue

        jpeg_base64 = base64.b64encode(jpeg_bytes).decode("ascii")
        send_response({"type": "frame", "jpeg_base64": jpeg_base64})

        frames_since_last_detection += 1
        if frames_since_last_detection >= PREVIEW_FRAMES_PER_DETECTION:
            frames_since_last_detection = 0
            scored_candidates = run_detection(frame)
            send_response({
                "type": "detection",
                "candidates": [
                    {"text": text, "confidence": score} for text, score in scored_candidates
                ],
            })

        elapsed_seconds = time.time() - frame_start_time
        remaining_seconds = frame_interval_seconds - elapsed_seconds
        if remaining_seconds > 0:
            time.sleep(remaining_seconds)


def main():
    if "--list-cameras" in sys.argv:
        log("Probing for available cameras...")
        available_camera_indices = list_available_cameras()
        send_response({"type": "camera_list", "cameras": available_camera_indices})
        log(f"Camera probe found {len(available_camera_indices)} camera(s): "
            f"{available_camera_indices}")
        return

    camera_index = parse_camera_index_arg()
    log("Card scanner bridge starting...")

    command_thread = threading.Thread(
        target=listen_for_commands, name="stdin-command-listener", daemon=True)
    command_thread.start()

    capture = cv2.VideoCapture(camera_index)
    if not capture.isOpened():
        send_response({
            "type": "error",
            "message": f"Could not open the webcam at index {camera_index}.",
        })
        log("Failed to open the webcam; exiting.")
        return

    send_response({"type": "status", "status": "camera_opened"})
    log("Webcam opened. Streaming preview frames...")

    try:
        stream_preview_frames(capture)
    finally:
        capture.release()
        log("Webcam released. Card scanner bridge exiting.")


if __name__ == "__main__":
    main()