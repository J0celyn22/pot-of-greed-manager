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
            {"action": "shutdown"}
  Response: {"status": "ok", "url": "<final url>", "html": "<page source>"}
            {"status": "blocked", "url": "<final url>", "html": "<page source>", "title": "<title>"}
            {"status": "error", "message": "<description>"}

All diagnostic output goes to stderr — stdout is reserved for the JSON protocol, and any stray
print() to stdout (from this script or a library) would corrupt it.

Started once per scraping run (by Java's PythonCardMarketBridge) and reused across every fetch
in the SAME browser session — not restarted per page. Session reuse across many requests is the
entire point of UC Mode (that's what the disconnect/reconnect dance is *for*); CardScraper.java
was forced into a fresh session per page specifically to work around plain Selenium's inability
to survive a second request, so re-testing that assumption here is deliberate, not an oversight.
Confirm this holds for a modest batch before trusting it for a full run — if session reuse turns
out not to hold up here either, the fix is restarting the `with SB(...)` block per fetch, which
costs UC-mode startup time per page but is a small, contained change to make in this file alone.

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
"""

import json
import sys
import time

from seleniumbase import SB

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
    """Diagnostic output goes to stderr; stdout is reserved for the JSON protocol."""
    print(message, file=sys.stderr, flush=True)


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


def handle_request(sb, request):
    action = request.get("action")
    if action == "shutdown":
        return None  # signals the main loop to stop

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


def main():
    log("CardMarket bridge starting (SeleniumBase UC Mode)...")
    with SB(uc=True) as sb:
        log("Browser ready. Waiting for requests on stdin.")
        for line in sys.stdin:
            line = line.strip()
            if not line:
                continue

            try:
                request = json.loads(line)
            except json.JSONDecodeError as decode_error:
                print(json.dumps({"status": "error", "message": f"Bad JSON: {decode_error}"}), flush=True)
                continue

            if request.get("action") == "shutdown":
                log("Shutdown requested.")
                break

            response = handle_request(sb, request)
            print(json.dumps(response), flush=True)

    log("CardMarket bridge exiting.")


if __name__ == "__main__":
    main()