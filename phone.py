"""API for the Android app (the phone answers the call itself; no Twilio).

POST /phone/start?session=<id>&from=<number>  -> greeting audio
POST /phone/turn?session=<id>   body: audio/wav (16 kHz mono) or empty = silence
POST /phone/end?session=<id>
All require header  X-Agent-Key: <PHONE_AGENT_KEY>
Response: {"reply", "lang", "end_call", "heard", "audio_b64"}  (audio = 16 kHz WAV)
"""
import base64
import logging
import os
import tempfile

from fastapi import APIRouter, Depends, Header, HTTPException, Query, Request
from starlette.concurrency import run_in_threadpool

import config
import conversation
import speech

log = logging.getLogger("phone")
RATE = 16000


def check_key(x_agent_key: str | None = Header(None)):
    if not config.PHONE_AGENT_KEY or x_agent_key != config.PHONE_AGENT_KEY:
        raise HTTPException(status_code=401, detail="Bad or missing X-Agent-Key")


router = APIRouter(prefix="/phone", dependencies=[Depends(check_key)])


def _payload(text: str, lang: str, end: bool, heard: str = "", wav: bytes | None = None) -> dict:
    if wav is None:
        wav = (speech.AUDIO_DIR / speech.synthesize(text, lang, rate=RATE)).read_bytes()
    return {"reply": text, "lang": lang, "end_call": end, "heard": heard,
            "audio_b64": base64.b64encode(wav).decode()}


@router.post("/start")
def start(session: str, from_: str | None = Query(None, alias="from")):
    conversation.get_call(session, from_, channel="phone")
    log.info("Phone call %s from %s", session, from_)
    names = [speech.synthesize(config.text(l, "greeting"), l, rate=RATE) for l in config.GREETING_ORDER]
    text = " ".join(config.text(l, "greeting") for l in config.GREETING_ORDER)
    return _payload(text, "en", False, wav=speech.concat_wavs(names))


@router.post("/turn")
async def turn(request: Request, session: str):
    call = conversation.get_call(session, channel="phone")
    body = await request.body()
    path = None
    try:
        if body:
            fd, path = tempfile.mkstemp(suffix=".wav")
            with os.fdopen(fd, "wb") as f:
                f.write(body)
        r = await run_in_threadpool(conversation.handle_audio, call, path)
    except Exception:
        log.exception("Phone turn failed")
        r = {"say": config.text(call["lang"], "error"), "lang": call["lang"], "end": True, "heard": ""}
    finally:
        if path:
            os.unlink(path)
    if r["end"]:
        conversation.finalize(session)
    return await run_in_threadpool(_payload, r["say"], r["lang"], r["end"], r["heard"])


@router.post("/end")
def end(session: str):
    conversation.finalize(session)
    return {"ok": True}
