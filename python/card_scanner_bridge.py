"""Long-lived stdout-streaming sidecar for the camera card scanner's live preview.

Unit 4 of the camera card-scanner feature (see docs/camera-scanner-plan.md): proves the
Python-to-Java live video path end-to-end. This script owns the webcam directly (opens it via
opencv-python's VideoCapture) and pushes preview frames to Java continuously and unprompted —
no OCR or card detection here, that is a later unit's scope.

Protocol: unlike python/cardmarket_bridge.py, which is strictly request-in / response-out, this
script's stdout is an ASYNC EVENT STREAM — one JSON object per line, pushed by this process on
its own schedule, not in response to a specific request:
  {"type": "status", "status": "camera_opened"}
  {"type": "frame", "jpeg_base64": "<base64-encoded JPEG bytes>"}
  {"type": "error", "message": "<description>"}
Java's write side (stdin here) stays request-shaped, but one-way — no response line is paired
with a command:
  {"action": "shutdown"}
Because the main thread's time is spent in the camera-capture loop, not in a blocking read on
stdin, a dedicated background thread listens for stdin commands throughout this process's life;
it does not read every camera frame itself (see listen_for_commands()/main() below).

Same stdout-fd-corruption fix as cardmarket_bridge.py, and for the same category of reason:
opencv-python is exactly the kind of native-backed library (camera-backend init banners, codec
warnings) that can write straight to the real stdout file descriptor regardless of what
sys.stdout points at in Python — see that script's own docstring for the full incident this
was traced from. The fix has to run before the cv2 import below, for the same reason it has to
run before cardmarket_bridge.py's seleniumbase import: once a third-party import has a chance to
write a startup banner, it is too late to redirect it.
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

# Which OS camera device to open. Hardcoded to the default webcam for this unit — picking a
# specific camera among several is out of scope until it's an actual problem.
CAMERA_INDEX = 0

# Preview frame rate sent to Java, independent of whatever rate a later unit's OCR/detection
# might run at. Starting point per the project's plan doc; tune once this is actually running
# against real hardware.
TARGET_PREVIEW_FPS = 12

# JPEG quality (0-100) for preview frames. Preview only needs to look reasonable on screen, not
# be detection-grade, so this favors smaller/faster frames over maximum fidelity.
JPEG_QUALITY = 70

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
    """
    frame_interval_seconds = 1.0 / TARGET_PREVIEW_FPS

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

        elapsed_seconds = time.time() - frame_start_time
        remaining_seconds = frame_interval_seconds - elapsed_seconds
        if remaining_seconds > 0:
            time.sleep(remaining_seconds)


def main():
    log("Card scanner bridge starting...")

    command_thread = threading.Thread(
        target=listen_for_commands, name="stdin-command-listener", daemon=True)
    command_thread.start()

    capture = cv2.VideoCapture(CAMERA_INDEX)
    if not capture.isOpened():
        send_response({
            "type": "error",
            "message": f"Could not open the webcam at index {CAMERA_INDEX}.",
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
