"""Channel-independent call state + one conversational turn (used by Twilio and the Android app)."""
import json
import time
from pathlib import Path

import brain
import config
import notify
import speech

CALL_DIR = Path(__file__).parent / "calls"
CALL_DIR.mkdir(exist_ok=True)
CALLS: dict[str, dict] = {}  # live calls keyed by session / CallSid


def get_call(sid: str, from_: str | None = None, channel: str = "twilio") -> dict:
    if sid not in CALLS:
        CALLS[sid] = {"sid": sid, "from": from_, "channel": channel, "started": time.time(), "lang": "en",
                      "history": [], "turns": [], "messages": [], "caller_name": None,
                      "no_input": 0, "finalized": False}
    elif from_ and not CALLS[sid]["from"]:
        CALLS[sid]["from"] = from_
    return CALLS[sid]


def save(call: dict) -> None:
    data = {k: v for k, v in call.items() if k != "history"}
    (CALL_DIR / f"{call['sid']}.json").write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")


def finalize(sid: str) -> None:
    call = CALLS.pop(sid, None)
    if not call or call["finalized"]:
        return
    call["finalized"] = True
    call["ended"] = time.time()
    save(call)
    if call["turns"]:
        notify.send(call)


def handle_audio(call: dict, wav_path: str | None) -> dict:
    """Process one caller utterance (or silence if wav_path is None).
    Returns {"say": text, "lang": ta|si|en, "end": bool, "heard": transcript}."""
    heard, locale = speech.transcribe(wav_path) if wav_path else ("", None)

    if not heard:
        call["no_input"] += 1
        if call["no_input"] > config.MAX_NO_INPUT:
            return {"say": config.text(call["lang"], "goodbye"), "lang": call["lang"], "end": True, "heard": ""}
        return {"say": config.text(call["lang"], "reprompt"), "lang": call["lang"], "end": False, "heard": ""}

    call["no_input"] = 0
    out = brain.respond(call["history"], heard, locale)
    call["lang"] = out["lang"]
    call["caller_name"] = out.get("caller_name") or call["caller_name"]
    if out.get("message_for_owner"):
        call["messages"].append(out["message_for_owner"])
    call["history"] += [{"role": "user", "text": f"[STT locale: {locale}] {heard}"},
                        {"role": "model", "text": out["_raw"]}]
    call["turns"].append({"caller": heard, "locale": locale, "agent": out["reply"], "lang": out["lang"]})
    save(call)

    say, end = out["reply"], out["end_call"]
    if not end and len(call["turns"]) >= config.MAX_TURNS:
        say, end = f"{say} {config.text(out['lang'], 'goodbye')}", True
    return {"say": say, "lang": out["lang"], "end": end, "heard": heard}
