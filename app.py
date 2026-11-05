"""Auto call attendant — server.

Two ways in:
  1. Android app (phone answers the call itself)  -> /phone/*   (see phone.py)
  2. Twilio number (call forwarding)              -> /voice, /turn, /noinput, /status

Per turn: caller audio -> Azure STT (+language ID) -> Gemini (reply in caller's language) -> Azure TTS.
"""
import logging
import os
import tempfile
import time

import requests
from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import Response
from fastapi.staticfiles import StaticFiles
from starlette.concurrency import run_in_threadpool
from twilio.request_validator import RequestValidator
from twilio.twiml.voice_response import VoiceResponse

import config
import conversation
import dashboard
import phone
import recording
import speech

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(levelname)s %(message)s")
log = logging.getLogger("app")

app = FastAPI(title="Auto Call Attendant", docs_url=None, redoc_url=None, openapi_url=None)  # no public API docs

if config.PHONE_AGENT_KEY and len(config.PHONE_AGENT_KEY) < 24:
    log.warning("PHONE_AGENT_KEY is shorter than 24 chars: /phone API is DISABLED until you set a longer key")
app.mount("/audio", StaticFiles(directory=speech.AUDIO_DIR), name="audio")
app.include_router(phone.router)
app.include_router(dashboard.router)


@app.get("/health")
def health():
    return {"ok": True}


# ---------------- Twilio mode ----------------
def url(path: str) -> str:
    return f"{config.PUBLIC_BASE_URL}{path}"


def xml(vr: VoiceResponse) -> Response:
    return Response(str(vr), media_type="application/xml")


async def twilio_form(request: Request) -> dict:
    form = {k: str(v) for k, v in (await request.form()).items()}
    if config.VALIDATE_TWILIO:
        ok = RequestValidator(config.TWILIO_AUTH_TOKEN).validate(
            url(request.url.path), form, request.headers.get("X-Twilio-Signature", ""))
        if not ok:
            raise HTTPException(status_code=403, detail="Invalid Twilio signature")
    return form


async def play(vr: VoiceResponse, text: str, lang: str, call: dict | None = None) -> None:
    """Play Azure TTS audio; fall back to Twilio's built-in English voice if TTS fails."""
    try:
        name = await run_in_threadpool(speech.synthesize, text, lang)
        vr.play(url(f"/audio/{name}"))
        if call is not None:
            recording.add_segment(call, speech.AUDIO_DIR / name)   # agent's voice in the recording
    except Exception:
        log.exception("TTS failed")
        vr.say("Sorry, please hold on." if lang != "en" else text)


def listen(vr: VoiceResponse) -> None:
    # Records until 2 s of silence. Empty recordings skip `action` and fall through to /noinput.
    vr.record(action=url("/turn"), method="POST", timeout=2, max_length=30,
              play_beep=False, trim="trim-silence")
    vr.redirect(url("/noinput"), method="POST")


def download_recording(rec_url: str) -> str:
    auth = (config.TWILIO_ACCOUNT_SID, config.TWILIO_AUTH_TOKEN)
    for _ in range(4):  # recording can take a moment to become available
        r = requests.get(rec_url + ".wav", auth=auth, timeout=15)
        if r.status_code == 200:
            break
        time.sleep(0.5)
    r.raise_for_status()
    fd, path = tempfile.mkstemp(suffix=".wav")
    with os.fdopen(fd, "wb") as f:
        f.write(r.content)
    if config.DELETE_RECORDINGS:
        try:
            requests.delete(rec_url + ".json", auth=auth, timeout=10)
        except Exception:
            log.warning("Could not delete recording %s", rec_url)
    return path


async def respond_twiml(call: dict, result: dict) -> Response:
    vr = VoiceResponse()
    await play(vr, result["say"], result["lang"], call)
    if result["end"]:
        vr.hangup()
        conversation.finalize(call["sid"])
    else:
        listen(vr)
    return xml(vr)


@app.post("/voice")
async def voice(request: Request):
    """Twilio 'A call comes in' webhook."""
    form = await twilio_form(request)
    call = conversation.get_call(form.get("CallSid", "unknown"), form.get("From"))
    log.info("Incoming Twilio call %s from %s", call["sid"], conversation.mask_number(call["from"]))
    vr = VoiceResponse()
    for lang in config.GREETING_ORDER:
        await play(vr, config.text(lang, "greeting"), lang, call)
    listen(vr)
    return xml(vr)


@app.post("/turn")
async def turn(request: Request):
    """Called by <Record> with the caller's audio."""
    form = await twilio_form(request)
    call = conversation.get_call(form.get("CallSid", "unknown"), form.get("From"))
    if form.get("CallStatus") == "completed":  # caller hung up while speaking
        conversation.finalize(call["sid"])
        return xml(VoiceResponse())
    try:
        wav = await run_in_threadpool(download_recording, form["RecordingUrl"])
        try:
            result = await run_in_threadpool(conversation.handle_audio, call, wav)
        finally:
            os.unlink(wav)
    except Exception:
        log.exception("Turn failed for %s", call["sid"])
        result = {"say": config.text(call["lang"], "error"), "lang": call["lang"], "end": True}
    return await respond_twiml(call, result)


@app.post("/noinput")
async def noinput(request: Request):
    form = await twilio_form(request)
    call = conversation.get_call(form.get("CallSid", "unknown"), form.get("From"))
    result = await run_in_threadpool(conversation.handle_audio, call, None)
    return await respond_twiml(call, result)


@app.post("/status")
async def status(request: Request):
    """Twilio 'Call status changes' webhook — makes sure every call gets logged + notified."""
    form = await twilio_form(request)
    if form.get("CallStatus") in {"completed", "busy", "failed", "no-answer", "canceled"}:
        conversation.finalize(form.get("CallSid", ""))
    return Response(status_code=204)
