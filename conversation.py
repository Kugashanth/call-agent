"""Channel-independent call state + one conversational turn (used by Twilio and the Android app)."""
import json
import re
import threading
import time
from pathlib import Path

import brain
import config
import notify
import speech

CALL_DIR = Path(__file__).parent / "calls"
CALL_DIR.mkdir(exist_ok=True)
CALLS: dict[str, dict] = {}  # live calls keyed by session / CallSid
_lock = threading.Lock()

# Session ids become file names: allow only safe characters (blocks ../ path traversal).
SAFE_ID = re.compile(r"^[A-Za-z0-9_-]{1,64}$")
SESSION_TTL = 20 * 60          # a call can't last longer than this; stale sessions are dropped
MAX_LIVE_CALLS = 50            # memory cap
MAX_TRANSCRIPT_CHARS = 600     # caller text sent to the LLM per turn
AUDIO_TTL = 24 * 3600          # synthesized reply audio kept at most 1 day


def valid_id(sid: str | None) -> bool:
    return bool(sid) and bool(SAFE_ID.match(sid))


def mask_number(n: str | None) -> str:
    """+94771234567 -> +9477****567 (for logs)."""
    return (n[:4] + "****" + n[-3:]) if n and len(n) > 7 else (n or "unknown")


def _expire_stale() -> None:
    now = time.time()
    for sid in [s for s, c in CALLS.items() if now - c["started"] > SESSION_TTL]:
        finalize(sid)


def get_call(sid: str, from_: str | None = None, channel: str = "twilio") -> dict:
    if not valid_id(sid):
        raise ValueError("invalid session id")
    with _lock:
        if sid not in CALLS:
            _expire_stale()
            if len(CALLS) >= MAX_LIVE_CALLS:
                raise RuntimeError("too many live calls")
            CALLS[sid] = {"sid": sid, "from": from_, "channel": channel, "started": time.time(), "lang": "en",
                          "history": [], "turns": [], "messages": [], "caller_name": None,
                          "no_input": 0, "finalized": False}
        elif from_ and not CALLS[sid]["from"]:
            CALLS[sid]["from"] = from_
        return CALLS[sid]


def save(call: dict) -> None:
    if not valid_id(call["sid"]):
        return
    path = (CALL_DIR / f"{call['sid']}.json").resolve()
    if path.parent != CALL_DIR.resolve():   # defence in depth
        return
    data = {k: v for k, v in call.items() if k != "history"}
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")


def prune_audio() -> None:
    """Delete synthesized audio older than AUDIO_TTL (it contains what was said on calls)."""
    cutoff = time.time() - AUDIO_TTL
    for f in speech.AUDIO_DIR.glob("*.wav"):
        try:
            if f.stat().st_mtime < cutoff:
                f.unlink()
        except OSError:
            pass


def finalize(sid: str) -> None:
    call = CALLS.pop(sid, None)
    if not call or call["finalized"]:
        return
    call["finalized"] = True
    call["ended"] = time.time()
    save(call)
    if call["turns"]:
        notify.send(call)
    prune_audio()


def handle_audio(call: dict, wav_path: str | None) -> dict:
    """Process one caller utterance (or silence if wav_path is None).
    Returns {"say": text, "lang": ta|si|en, "end": bool, "heard": transcript}."""
    heard, locale = speech.transcribe(wav_path) if wav_path else ("", None)
    heard = heard[:MAX_TRANSCRIPT_CHARS]

    if not heard:
        call["no_input"] += 1
        if call["no_input"] > config.MAX_NO_INPUT:
            return {"say": config.text(call["lang"], "goodbye"), "lang": call["lang"], "end": True, "heard": ""}
        return {"say": config.text(call["lang"], "reprompt"), "lang": call["lang"], "end": False, "heard": ""}

    call["no_input"] = 0
    out = brain.respond(call["history"], heard, locale)
    call["lang"] = out["lang"]
    call["caller_name"] = (out.get("caller_name") or call["caller_name"] or None)
    if call["caller_name"]:
        call["caller_name"] = str(call["caller_name"])[:80]
    if out.get("message_for_owner"):
        call["messages"].append(str(out["message_for_owner"])[:500])
    call["history"] += [{"role": "user", "text": f"[STT locale: {locale}] {heard}"},
                        {"role": "model", "text": out["_raw"]}]
    call["turns"].append({"caller": heard, "locale": locale, "agent": out["reply"], "lang": out["lang"]})
    save(call)

    say, end = out["reply"][:700], out["end_call"]
    if not end and len(call["turns"]) >= config.MAX_TURNS:
        say, end = f"{say} {config.text(out['lang'], 'goodbye')}", True
    return {"say": say, "lang": out["lang"], "end": end, "heard": heard}
