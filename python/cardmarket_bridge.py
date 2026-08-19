"""Long-lived stdin/stdout JSON sidecar for fetching CardMarket pages via SeleniumBase UC Mode.

Why this exists: CardScraper.java's own Selenium+ChromeDriver setup was investigated at length
(see the project's conversation history) and traced to a block that happens after the very
first request on a CDP-attached session, regardless of headers, cookies, or navigator.webdriver
patching — a layer Selenium's plain Java API doesn't expose a way to change. SeleniumBase's
UC Mode addresses that specific layer by disconnecting the WebDriver/chromedriver connection
during page loads and clicks, then reconnecting, which is Python-only tooling — hence this
process existing as a separate sidecar rather than a rewrite of CardScraper.java itself.

Protocol: one JSON object per line on stdin, one JSON object per line on stdout.
  Request:  {"action": "fetch", "url": "https://..."}
            {"action": "check"}
            {"action": "restart_session"}
            {"action": "shutdown"}
  Response: {"status": "ok", "url": "<final url>", "html": "<page source>"}
            {"status": "blocked", "url": "<final url>", "html": "<page source>", "title": "<title>"}
            {"status": "ok"}                              (restart_session only)
            {"status": "error", "message": "<description>"}

"fetch" navigates to a URL (via UC Mode's disconnect/reconnect) and polls the result. "check"
polls whatever page is already loaded, without navigating anywhere — used by Java's Retry
button while resolving a manual Cloudflare prompt, so a person's just-solved checkbox isn't
thrown away by a fresh navigation (see check_current_page()'s own docstring). "restart_session"
closes the current browser and opens a brand new SB(uc=True) session in its place, for when
Cloudflare's hard "Attention Required" block page turns up and the person wants to try again
with a clean session rather than the one that just got flagged; the next "fetch" after it acts
exactly like the first fetch of a fresh run (see main()/run_session()).

All diagnostic output goes to stderr — stdout is reserved for the JSON protocol. That contract
used to rest entirely on this script's own discipline (every call site uses log(), never a bare
print()), which turned out not to be enough: a live run hit `A JSONObject text must begin with
'{'` on a blank line, immediately after this script's own log line "Challenge page detected;
attempting an automatic solve..." — i.e. right as uc_gui_click_captcha() ran. That method drives
PyAutoGUI, a real OS-level mouse/keyboard automation library, not a WebDriver call; PyAutoGUI is
also an optional dependency SeleniumBase installs at runtime the first time it's needed
(https://seleniumbase.io/help_docs/uc_mode/), and neither that install step nor PyAutoGUI's own
internals are under this script's control or guaranteed to only ever write to stderr. The person
was also moving the browser window at that exact moment, which is exactly the kind of disruption
that can make a screen-coordinate-driven GUI automation library behave unexpectedly. Either way,
something below this script's own code wrote a stray blank line to the real stdout file
descriptor, landing ahead of (or instead of) that request's actual JSON response and breaking
the one-line-in / one-line-out protocol Java depends on.

Because that write happens at the OS file-descriptor level, no amount of "only use log(), never
print()" discipline inside this file can prevent it — a C extension or subprocess can write
directly to fd 1 regardless of what sys.stdout points at in Python. The actual fix: duplicate
the real stdout file descriptor into STDOUT_FD (below) before any third-party import or call,
then repoint the process's own fd 1 at fd 2 (stderr) for the rest of the process's life. Every
send_response() call from here on writes through STDOUT_FD — the untouched duplicate — while
anything else in the process (including a stray print() or a C-level write(1, ...) from
PyAutoGUI, SeleniumBase, or a pip install it triggers) lands on stderr instead, where it's
diagnostic noise Java already drains and logs rather than a corrupting line on the response
channel. This has to happen before `from seleniumbase import SB` in case SeleniumBase or one of
its own dependencies prints anything at import time.

Started once per scraping run (by Java's PythonCardMarketBridge) and reused across every fetch
in the SAME browser session — not restarted per page, except via the "restart_session" action
above, which is deliberately session-scoped (see run_session()/main()) rather than restarting
this whole process, so Java keeps talking to the same bridge the entire time. Session reuse
across many requests is the entire point of UC Mode (that's what the disconnect/reconnect dance
is *for*); CardScraper.java was forced into a fresh session per page specifically to work around
plain Selenium's inability to survive a second request, so re-testing that assumption here is
deliberate, not an oversight. Confirm this holds for a modest batch before trusting it for a
full run — if session reuse turns out not to hold up here either, the fix is restarting the
`with SB(...)` block per fetch, which costs UC-mode startup time per page but is a small,
contained change to make in this file alone.

Runs headed (a real, visible Chrome window), not headless — `headless` is never passed to `SB()`
here, and its documented default only turns on if `--headless` is present in sys.argv, which it
never is for a subprocess started the way `PythonCardMarketBridge.start()` starts this one. This
is deliberate, not an oversight: CardScraper.java's own manual-fallback prompt (Retry/Stop, shown
when a page is still blocked or stuck on a challenge) relies on the person being able to see and
solve whatever Cloudflare is showing in this exact window — unlike the direct-Selenium path,
which starts off-screen and only becomes visible once a manual fallback is actually needed, this
window is on-screen for the entire run.

Confirmed against the real site: two full runs (5 sequential fetches each, one browser session)
both showed the first fetch after a fresh session hit Cloudflare's resolvable challenge and not
clear within this script's own 25s settle deadline, while every fetch after it in the same
session came back with real content straight away — consistent both times, and the reason
CardScraper.java's manual fallback exists on this path too, for whatever fraction of first
fetches don't clear on their own before the deadline. One real bug turned up in that testing:
`SB(uc=True, test=True)` printed an unconditional startup banner to stdout before this script's
own request loop ever started reading — corrupting the very first JSON response. Fixed by
dropping `test=True`, which existed only for SeleniumBase's own optional console
output/logging and turned out to have nothing to do with UC Mode or captcha-click handling.
That earlier fix removed one known source of stray stdout output; the fd-duplication approach
above is the general fix that no longer depends on having found and silenced every such source.
"""

import os
import sys

# Duplicate the real stdout file descriptor before anything else (including the seleniumbase
# import below) gets a chance to write to it. STDOUT_FD is what send_response() writes through;
# everything else in this process — this script's own accidental print(), a stray write from a
# third-party library, or a runtime pip install triggered by uc_gui_click_captcha() — writes to
# fd 1, which is repointed at fd 2 (stderr) a few lines down. See this module's own docstring
# for why this exists: a live run hit a corrupted stdout line during uc_gui_click_captcha().
STDOUT_FD = os.dup(sys.stdout.fileno())
os.dup2(sys.stderr.fileno(), sys.stdout.fileno())
# sys.stdout itself (the Python-level object, distinct from fd 1 above) is also repointed, so
# that a plain print() from Python code — this script's own or a library's — reads as going to
# "stdout" but actually lands on the now-redirected fd 1, i.e. stderr. Buffering is disabled
# (write_through) so nothing sits in a Python-level buffer only to flush at a surprising time.
sys.stdout = open(1, "w", buffering=1, closefd=False)

import json  # noqa: E402 - after the fd redirection above, which must run first
import time  # noqa: E402

from seleniumbase import SB  # noqa: E402

NO_OFFERS_MARKER = "There are no offers for your selected category"
TOO_MANY_RESULTS_MARKER = "300+ results"
CLOUDFLARE_BLOCKED_MARKER = "Attention Required! | Cloudflare"
CHALLENGE_PAGE_MARKER = "challenge-platform"

# Kept in sync with the same-named constants and hasRecognizedContent()/isChallengePage() in
# CardScraper.java on purpose, so a page is classified the same way regardless of which side
# (Java or this script) is doing the looking.

SETTLE_TIMEOUT_SECONDS = 25
POLL_INTERVAL_SECONDS = 0.5
POST_CAPTCHA_ATTEMPT_PAUSE_SECONDS = 2


def log(message):
    """Diagnostic output goes to stderr; STDOUT_FD (the real, untouched stdout) is reserved
    for the JSON protocol via send_response(). Kept even though sys.stdout itself is now also
    repointed at stderr (see the module docstring) — calling this out by name at each log call
    site is clearer than relying on everyone remembering that sys.stdout doesn't mean fd 1
    anymore in this particular script.
    """
    print(message, file=sys.stderr, flush=True)


def send_response(response_dict):
    """The only function in this script allowed to write to the real stdout (STDOUT_FD) — every
    response to Java goes through here, never through print(..., file=sys.stdout) or a bare
    print(), both of which now land on stderr instead (see the module docstring). Writes and
    flushes directly against the duplicated file descriptor, bypassing Python's own stdout
    buffering/object layer entirely, so nothing about how sys.stdout is configured elsewhere in
    the process can affect this write.
    """
    line = (json.dumps(response_dict) + "\n").encode("utf-8")
    os.write(STDOUT_FD, line)


def has_recognized_content(html):
    if not html:
        return False
    return (
        "UserOffersTable" in html
        or NO_OFFERS_MARKER in html
        or TOO_MANY_RESULTS_MARKER in html
        or CLOUDFLARE_BLOCKED_MARKER in html
    )


def is_blocked(html):
    return bool(html) and CLOUDFLARE_BLOCKED_MARKER in html


def is_challenge_page(html):
    return bool(html) and CHALLENGE_PAGE_MARKER in html


def fetch_one_page(sb, url):
    """Navigates to url using UC Mode's disconnect/reconnect navigation, then polls until
    recognized content shows up, a resolvable challenge gets an automatic solve attempt, or
    the settle timeout runs out. Mirrors CardScraper.waitForPageToSettle's shape on the Java
    side, so behavior stays comparable between the two even though the mechanism differs.
    """
    sb.uc_open_with_reconnect(url, reconnect_time=4)
    return poll_current_page(sb)


def check_current_page(sb):
    """Re-reads whatever page is already loaded, polling for it to settle — but without
    navigating anywhere first. This is the counterpart to CardScraper's own
    waitForRecognizedContentOnly() on the direct-Selenium path: after a person manually solves
    Cloudflare's checkbox in the visible browser window, the page they solved is already
    sitting there, and re-navigating (what fetch_one_page/uc_open_with_reconnect does) would
    throw that solved state away and ask Cloudflare fresh again. That was a real bug: Java's
    Retry button used to send a fresh "fetch" (a re-navigation) during manual-fallback
    resolution, which could visibly restart the browser mid-solve and cost another clean
    attempt at the very challenge the person had just solved. Retry now sends "check" instead,
    handled here.
    """
    return poll_current_page(sb)


def poll_current_page(sb):
    """Shared poll loop used by both fetch_one_page (after navigating) and check_current_page
    (without navigating): waits for recognized content, attempts an automatic captcha solve
    if a resolvable challenge shows up, and reports "blocked" once the settle timeout elapses.
    Extracted so the two callers can't drift out of sync on how a page is judged to have
    settled.
    """
    deadline = time.time() + SETTLE_TIMEOUT_SECONDS
    while time.time() < deadline:
        html = sb.get_page_source()
        if has_recognized_content(html):
            if is_blocked(html):
                return {
                    "status": "blocked",
                    "url": sb.get_current_url(),
                    "html": html,
                    "title": sb.get_page_title(),
                }
            return {"status": "ok", "url": sb.get_current_url(), "html": html}

        if is_challenge_page(html):
            log("Challenge page detected; attempting an automatic solve...")
            try:
                sb.uc_gui_click_captcha()
            except Exception as captcha_exception:  # noqa: BLE001 - best-effort, non-fatal
                log(f"Automatic captcha click failed (non-fatal): {captcha_exception}")
            time.sleep(POST_CAPTCHA_ATTEMPT_PAUSE_SECONDS)
            continue

        time.sleep(POLL_INTERVAL_SECONDS)

    html = sb.get_page_source()
    return {
        "status": "blocked",
        "url": sb.get_current_url(),
        "html": html or "",
        "title": sb.get_page_title() if html else "",
    }


class RestartSessionRequested(Exception):
    """Raised out of run_session()'s request loop when a "restart_session" request comes in,
    so main() can close the current `with SB(uc=True) as sb:` block cleanly (running its own
    teardown) and open a brand new one, rather than trying to swap `sb` out from underneath an
    already-open context manager. Not an error — this is the ordinary control-flow path for
    the "start over with a fresh browser after a hard Cloudflare block" feature.
    """


def handle_request(sb, request):
    action = request.get("action")
    if action == "shutdown":
        return None  # signals the caller to stop

    if action == "restart_session":
        raise RestartSessionRequested()

    if action == "check":
        try:
            return check_current_page(sb)
        except Exception as check_exception:  # noqa: BLE001 - reported back to Java, not swallowed
            log(f"check_current_page raised: {check_exception}")
            return {"status": "error", "message": str(check_exception)}

    if action != "fetch":
        return {"status": "error", "message": f"Unknown action: {action!r}"}

    url = request.get("url")
    if not url:
        return {"status": "error", "message": "Missing 'url'"}

    try:
        return fetch_one_page(sb, url)
    except Exception as fetch_exception:  # noqa: BLE001 - reported back to Java, not swallowed
        log(f"fetch_one_page raised: {fetch_exception}")
        return {"status": "error", "message": str(fetch_exception)}


def run_session(sb):
    """Runs the request loop against one already-open browser session until a "shutdown"
    request, end of input, or a "restart_session" request ends it. Returns True if the caller
    should open a fresh session and call this again (restart requested), False if the whole
    bridge process should exit (shutdown requested or stdin closed).
    """
    log("Browser ready. Waiting for requests on stdin.")
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue

        try:
            request = json.loads(line)
        except json.JSONDecodeError as decode_error:
            send_response({"status": "error", "message": f"Bad JSON: {decode_error}"})
            continue

        if request.get("action") == "shutdown":
            log("Shutdown requested.")
            return False

        try:
            response = handle_request(sb, request)
        except RestartSessionRequested:
            log("Session restart requested (after a hard Cloudflare block); "
                "closing this browser and starting a fresh one...")
            send_response({"status": "ok"})
            return True

        send_response(response)

    return False  # stdin closed with no explicit shutdown


def main():
    log("CardMarket bridge starting (SeleniumBase UC Mode)...")
    restart = True
    while restart:
        with SB(uc=True) as sb:
            restart = run_session(sb)
    log("CardMarket bridge exiting.")


if __name__ == "__main__":
    main()