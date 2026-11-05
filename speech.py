"""Azure Speech: speech-to-text with language ID, and text-to-speech per language."""
import hashlib
import logging
import threading
from pathlib import Path

import azure.cognitiveservices.speech as speechsdk

import config

log = logging.getLogger("speech")
AUDIO_DIR = Path(__file__).parent / "audio"
AUDIO_DIR.mkdir(exist_ok=True)


def _cfg() -> speechsdk.SpeechConfig:
    return speechsdk.SpeechConfig(subscription=config.AZURE_SPEECH_KEY, region=config.AZURE_SPEECH_REGION)


def transcribe(wav_path: str) -> tuple[str, str | None]:
    """Return (transcript, detected_locale) for a WAV file (Twilio sends 8 kHz 16-bit mono)."""
    auto = speechsdk.languageconfig.AutoDetectSourceLanguageConfig(languages=config.STT_LOCALES)
    rec = speechsdk.SpeechRecognizer(
        speech_config=_cfg(),
        auto_detect_source_language_config=auto,
        audio_config=speechsdk.audio.AudioConfig(filename=wav_path),
    )
    parts: list[str] = []
    locales: list[str] = []
    done = threading.Event()

    def on_recognized(evt):
        r = evt.result
        if r.reason == speechsdk.ResultReason.RecognizedSpeech and r.text:
            parts.append(r.text)
            loc = speechsdk.AutoDetectSourceLanguageResult(r).language
            if loc:
                locales.append(loc)

    def on_canceled(evt):
        d = evt.cancellation_details
        if d.reason == speechsdk.CancellationReason.Error:
            log.error("STT error: %s", d.error_details)
        done.set()

    rec.recognized.connect(on_recognized)
    rec.canceled.connect(on_canceled)
    rec.session_stopped.connect(lambda _: done.set())
    rec.start_continuous_recognition()
    done.wait(timeout=40)
    rec.stop_continuous_recognition()

    text = " ".join(parts).strip()
    locale = max(set(locales), key=locales.count) if locales else None
    log.info("STT [%s]: %s", locale, text)
    return text, locale


def synthesize(text: str, lang: str, rate: int = 8000) -> str:
    """Synthesize text in the voice for `lang`; returns the file name inside AUDIO_DIR (cached).
    rate=8000 for Twilio phone lines, 16000 for the Android app."""
    voice = config.VOICES.get(lang, config.VOICES["en"])
    name = hashlib.sha1(f"{voice}|{rate}|{text}".encode()).hexdigest()[:24] + ".wav"
    path = AUDIO_DIR / name
    if path.exists():
        return name

    fmt = (speechsdk.SpeechSynthesisOutputFormat.Riff16Khz16BitMonoPcm if rate == 16000
           else speechsdk.SpeechSynthesisOutputFormat.Riff8Khz16BitMonoPcm)
    cfg = _cfg()
    cfg.speech_synthesis_voice_name = voice
    cfg.set_speech_synthesis_output_format(fmt)
    synth = speechsdk.SpeechSynthesizer(speech_config=cfg, audio_config=None)  # keep audio in memory
    r = synth.speak_text_async(text).get()
    if r.reason != speechsdk.ResultReason.SynthesizingAudioCompleted:
        details = getattr(r, "cancellation_details", None)
        raise RuntimeError(f"TTS failed ({voice}): {details.error_details if details else r.reason}")
    path.write_bytes(r.audio_data)
    return name


def concat_wavs(names: list[str]) -> bytes:
    """Join same-format WAV files from AUDIO_DIR into one WAV (bytes)."""
    import io
    import wave
    buf = io.BytesIO()
    with wave.open(buf, "wb") as out:
        for i, n in enumerate(names):
            with wave.open(str(AUDIO_DIR / n), "rb") as w:
                if i == 0:
                    out.setparams(w.getparams())
                out.writeframes(w.readframes(w.getnframes()))
    return buf.getvalue()
