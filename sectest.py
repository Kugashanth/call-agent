"""Attack tests against the server with external services mocked. Run: python sectest.py <project_dir>"""
import json, os, sys, wave, pathlib
proj = sys.argv[1]
sys.path.insert(0, proj)
os.chdir(proj)
os.environ.update(VALIDATE_TWILIO="0", PUBLIC_BASE_URL="https://x.test", GEMINI_API_KEY="fake",
                  PHONE_AGENT_KEY="k" * 32)
import speech, brain, app as A
from fastapi.testclient import TestClient

def fake_tts(t, l, rate=8000):
    n = f"{l}_{rate}_{abs(hash(t))}.wav"
    with wave.open(str(speech.AUDIO_DIR / n), "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(rate); w.writeframes(b"\0\0" * (rate // 10))
    return n
speech.synthesize = fake_tts
speech.transcribe = lambda p: ("hello", "en-IN")
def fake(h, t, l):
    d = {"lang": "en", "reply": "ok", "end_call": False}; d["_raw"] = json.dumps(d); return d
brain.respond = fake

c = TestClient(A.app)
H = {"X-Agent-Key": "k" * 32}
res = {}
for target in ["../../PWNED", "..%2F..%2FPWNED", "a/b", "x" * 300]:
    r = c.post(f"/phone/turn?session={target}", headers=H, content=b"RIFFx")
    res[f"traversal {target[:20]!r}"] = r.status_code
res["file written outside calls/"] = any(pathlib.Path(p).exists() for p in ["/home/claude/PWNED.json", "PWNED.json", "/PWNED.json"])
res["no key"] = c.post("/phone/start?session=s1").status_code
res["wrong key"] = c.post("/phone/start?session=s1", headers={"X-Agent-Key": "nope"}).status_code
res["huge body (3MB)"] = c.post("/phone/turn?session=s1", headers=H, content=b"0" * 3_000_000).status_code
res["/docs exposed"] = c.get("/docs").status_code
res["/openapi.json exposed"] = c.get("/openapi.json").status_code
res["normal start"] = c.post("/phone/start?session=good-1&from=%2B94771234567", headers=H).status_code
res["normal turn"] = c.post("/phone/turn?session=good-1", headers=H, content=b"RIFFx").status_code
for k, v in res.items():
    print(f"{k:32} {v}")
for p in ["/home/claude/PWNED.json", os.path.join(proj, "PWNED.json")]:
    if os.path.exists(p): os.remove(p)
