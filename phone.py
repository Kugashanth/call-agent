"""API for the Android app (the phone answers the call itself; no Twilio).

POST /phone/start?session=<id>&from=<number>  -> greeting audio
POST /phone/turn?session=<id>   body: audio/wav (16 kHz mono) or empty = silence
POST /phone/end?session=<id>
All require header  X-Agent-Key: <PHONE_AGENT_KEY>   (min 24 chars, compared in constant time)
Response: {"reply", "lang", "end_call", "heard", "audio_b64"}  (audio = 16 kHz WAV)
"""
import base64
import hmac
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
MAX_BODY = 2 * 1024 * 1024   # 2 MB ≈ 60 s of 16 kHz audio; anything bigger is rejected


def check_key(x_agent_key: str | None = Header(None)):
    key = config.PHONE_AGENT_KEY
    if len(key) < 24 or not x_agent_key or not hmac.compare_digest(x_agent_key.encode(), key.encode()):
        raise HTTPException(status_code=401, detail="Unauthorized")


def session_id(session: str = Query(..., max_length=64)) -> str:
    if not conversation.valid_id(session):
        raise HTTPException(status_code=400, detail="Invalid session id")
    return session


router = APIRouter(prefix="/phone", dependencies=[Depends(check_key)])


def _call(session: str, from_: str | None = None) -> dict:
    try:
        return conversation.get_call(session, from_, channel="phone")
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid session id")
    except RuntimeError:
        raise HTTPException(status_code=503, detail="Busy")


def _payload(text: str, lang: str, end: bool, heard: str = "", wav: bytes | None = None) -> dict:
    if wav is None:
        wav = (speech.AUDIO_DIR / speech.synthesize(text, lang, rate=RATE)).read_bytes()
    return {"reply": text, "lang": lang, "end_call": end, "heard": heard,
            "audio_b64": base64.b64encode(wav).decode()}


@router.post("/start")
def start(session: str = Depends(session_id), from_: str | None = Query(None, alias="from", max_length=32)):
    _call(session, from_)
    log.info("Phone call %s from %s", session, conversation.mask_number(from_))
    names = [speech.synthesize(config.text(l, "greeting"), l, rate=RATE) for l in config.GREETING_ORDER]
    text = " ".join(config.text(l, "greeting") for l in config.GREETING_ORDER)
    return _payload(text, "en", False, wav=speech.concat_wavs(names))


@router.post("/turn")
async def turn(request: Request, session: str = Depends(session_id)):
    if int(request.headers.get("content-length") or 0) > MAX_BODY:
        raise HTTPException(status_code=413, detail="Audio too large")
    body = b""
    async for chunk in request.stream():
        body += chunk
        if len(body) > MAX_BODY:
            raise HTTPException(status_code=413, detail="Audio too large")

    call = _call(session)
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
            os.unlink(path)   # caller audio is never kept
    if r["end"]:
        conversation.finalize(session)
    return await run_in_threadpool(_payload, r["say"], r["lang"], r["end"], r["heard"])


@router.post("/end")
def end(session: str = Depends(session_id)):
    conversation.finalize(session)
    return {"ok": True}
