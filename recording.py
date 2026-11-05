"""Full-call voice recordings: caller utterances + agent replies -> one WAV per call.

Each turn adds segments (caller audio, then the agent's reply audio). When the call ends they are
joined into recordings/<sid>.wav (16-bit mono, resampled to a common rate) and the pieces deleted.
"""
import array
import shutil
import time
import wave
from pathlib import Path

import config

REC_DIR = Path(__file__).parent / "recordings"
REC_DIR.mkdir(exist_ok=True)


def _seg_dir(sid: str) -> Path:
    d = REC_DIR / f"{sid}.parts"
    d.mkdir(exist_ok=True)
    return d


def path_for(sid: str) -> Path:
    return REC_DIR / f"{sid}.wav"


def add_segment(call: dict, src: Path | str) -> None:
    """Copy a WAV piece into the call's segment folder (no-op if recording is off)."""
    if not config.RECORD_CALLS:
        return
    try:
        n = call.setdefault("_segments", 0)
        dst = _seg_dir(call["sid"]) / f"{n:04d}.wav"
        shutil.copyfile(src, dst)
        call["_segments"] = n + 1
    except Exception:
        pass


def _read_mono16(p: Path) -> tuple[array.array, int] | None:
    try:
        with wave.open(str(p), "rb") as w:
            if w.getsampwidth() != 2:
                return None
            ch, rate, frames = w.getnchannels(), w.getframerate(), w.readframes(w.getnframes())
    except Exception:
        return None
    a = array.array("h")
    a.frombytes(frames)
    if ch == 2:                                   # stereo -> mono
        a = array.array("h", ((a[i] + a[i + 1]) // 2 for i in range(0, len(a) - 1, 2)))
    return a, rate


def _resample(a: array.array, src: int, dst: int) -> array.array:
    if src == dst or not a:
        return a
    n = int(len(a) * dst / src)
    out = array.array("h", [0]) * n
    step = src / dst
    for i in range(n):
        x = i * step
        j = int(x)
        f = x - j
        nxt = a[j + 1] if j + 1 < len(a) else a[j]
        out[i] = int(a[j] * (1 - f) + nxt * f)
    return out


def finalize(sid: str) -> bool:
    """Join segments into recordings/<sid>.wav. Returns True if a recording was written."""
    d = REC_DIR / f"{sid}.parts"
    if not d.exists():
        return False
    try:
        parts = [_read_mono16(p) for p in sorted(d.glob("*.wav"))]
        parts = [p for p in parts if p]
        if not parts:
            return False
        rate = max(r for _, r in parts)           # 16 kHz if any piece is 16 kHz, else 8 kHz
        gap = array.array("h", [0]) * int(rate * 0.25)
        with wave.open(str(path_for(sid)), "wb") as out:
            out.setnchannels(1)
            out.setsampwidth(2)
            out.setframerate(rate)
            for a, r in parts:
                out.writeframes(_resample(a, r, rate).tobytes())
                out.writeframes(gap.tobytes())
        return True
    finally:
        shutil.rmtree(d, ignore_errors=True)


def delete(sid: str) -> None:
    path_for(sid).unlink(missing_ok=True)
    shutil.rmtree(REC_DIR / f"{sid}.parts", ignore_errors=True)


def prune() -> None:
    """Delete recordings older than RECORDING_TTL_DAYS."""
    cutoff = time.time() - config.RECORDING_TTL_DAYS * 86400
    for f in REC_DIR.glob("*.wav"):
        try:
            if f.stat().st_mtime < cutoff:
                f.unlink()
        except OSError:
            pass
