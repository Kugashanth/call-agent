# Auto Call Attendant: Tamil · Sinhala · English · Tanglish

> **Ways to run it:**
> 1. **Twilio number + call forwarding** (clean phone-line audio) → this file. Manage it with the **Call Agent dashboard app**, the Google Play version → **[PLAY_STORE.md](PLAY_STORE.md)**
> 2. **Sideload app: your phone answers the call itself.** No Twilio → **[ANDROID.md](ANDROID.md)** (not allowed on Google Play)
>
> Downloads: https://github.com/Kugashanth/call-agent/releases/latest · Security review: [SECURITY.md](SECURITY.md) · Privacy: [PRIVACY.md](PRIVACY.md)

An AI that answers your phone when you can't. It greets callers in all three languages, understands Tamil, Sinhala, English and code-mixed speech (Tanglish/Singlish), replies in the caller's language, answers from `knowledge.md`, takes messages, and sends you a call summary on Telegram.

```
Caller ─► Your SIM (busy / no answer) ─forward─► Twilio number
                                                   │  webhooks
                                                   ▼
                         FastAPI (app.py) ── <Record> caller audio
                           │ Azure STT + language ID (en-IN / ta-IN / si-LK)
                           │ Gemini: understand, reply in caller's language (JSON)
                           │ Azure TTS (ta-LK / si-LK / en-IN voices)
                           └─► <Play> reply ─► loop ─► hang up ─► Telegram summary + calls/*.json
```

| File | Purpose |
|---|---|
| `app.py` | Twilio webhooks: `/voice`, `/turn`, `/noinput`, `/status` |
| `speech.py` | Azure speech-to-text (auto language detection) and text-to-speech (cached WAVs) |
| `brain.py` | Gemini prompt: language rules, phone style, JSON output |
| `knowledge.md` | **The only details the bot may share. Edit this.** |
| `config.py` | Voices, fixed phrases (greeting/reprompt/goodbye), limits |
| `notify.py` | Telegram summary to the owner |

## 1. Get the keys (all have free tiers or trials)
1. **Azure Speech**: Azure portal → create a *Speech* resource (Free F0 tier) → copy the key and region (e.g. `southeastasia`).
2. **Gemini**: aistudio.google.com → *Get API key*.
3. **Twilio**: sign up (trial credit) → buy a phone number with Voice capability.
4. **Telegram (optional)**: create a bot with @BotFather → token; send it a message, then open `https://api.telegram.org/bot<TOKEN>/getUpdates` to find your `chat_id`.

## 2. Run locally
```bash
pip install -r requirements.txt
cp .env.example .env            # fill in the keys
ngrok http 8000                 # copy the https URL into PUBLIC_BASE_URL in .env
uvicorn app:app --port 8000
```
On Linux the Azure SDK needs `libssl3 libasound2` (the Dockerfile installs them).

## 3. Connect Twilio
Twilio Console → Phone Numbers → your number → **Voice configuration**:
- *A call comes in* → Webhook → `POST https://<PUBLIC_BASE_URL>/voice`
- *Call status changes* → `POST https://<PUBLIC_BASE_URL>/status`

Call the Twilio number and talk. (On a trial account Twilio plays a short trial notice first; upgrading removes it.)

## 4. Connect YOUR phone (conditional call forwarding)
Dial these on the phone with your SIM (standard GSM codes; use the Twilio number in full international format):

| When | Code |
|---|---|
| No answer | `**61*+1XXXXXXXXXX#` |
| Busy / call rejected | `**67*+1XXXXXXXXXX#` |
| Switched off / no signal | `**62*+1XXXXXXXXXX#` |
| Cancel all forwarding | `##002#` |

⚠️ Forwarding to a foreign (e.g. US) Twilio number is charged by your operator (Dialog / Mobitel / Hutch / Airtel) **as an international call for every forwarded minute**, and may need IDD enabled. Check rates with your operator before you leave it on. To save money in a demo, have people call the Twilio number directly.

## 5. Deploy (always on)
Any Docker host works: Render, Railway, Fly.io, or a small VPS.
- Build from the `Dockerfile`, set the `.env` values as environment variables, and set `PUBLIC_BASE_URL` to the deployed URL.
- Avoid free tiers that **sleep when idle**: Twilio's webhook times out (~15 s) before a sleeping server wakes up, so the first call fails.
- Update both Twilio webhooks to the new URL.

## Customise
- **What the bot says** → `knowledge.md` (availability, services, FAQs, what not to share).
- **Voices** → `VOICE_TA`, `VOICE_SI`, `VOICE_EN` in `.env` (e.g. `ta-LK-KumarNeural`, `si-LK-SameeraNeural` for male voices).
- **Greeting and fixed phrases** → `TEXTS` in `config.py`.

## Notes and next steps
- **Latency:** the design is turn-based (record → think → speak), about 3–5 s per reply. That's reliable and simple. v2: Twilio Media Streams over WebSocket with streaming STT/TTS gets this down to about 1 s.
- **Tanglish accuracy:** the LLM picks the reply language from the meaning of the words, not from the STT tag, so a Tanglish transcript recognised as `en-IN` still gets a Tamil reply.
- **Privacy:** the greeting says it's an AI assistant. Caller recordings are deleted from Twilio after transcription (`DELETE_RECORDINGS=1`). Transcripts stay in `calls/`.
- **Research angle:** log STT errors on code-mixed calls → fine-tune Whisper or Azure Custom Speech on Tanglish/Singlish → compare word error rate.
