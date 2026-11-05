# Run it on your Android phone (the phone answers the call itself)

No Twilio and no forwarding costs. Your Android phone auto-answers calls you don't pick up, turns on speakerphone, listens to the caller through the mic, sends the audio to your server (Azure STT → Gemini → Azure TTS), and plays the reply through the speaker.

```
Caller ─► your SIM ─► Android phone (Call Agent app)
                        ├─ ringing N s, you don't answer → auto-answer → speaker ON
                        └─ loop: mic → WAV ─HTTPS─► server /phone/turn ─► reply WAV → speaker
```

> **Why an accessibility service?** During a phone call Android gives ordinary apps **silence** from the mic. Only accessibility services may still record. That's why the app asks for it.

## 1. Run the server
In the project folder:
```bash
pip install -r requirements.txt
cp .env.example .env        # set AZURE_*, GEMINI_API_KEY, and PHONE_AGENT_KEY (any long random string)
uvicorn app:app --host 0.0.0.0 --port 8000
```
The phone needs to reach the server:
- **Same Wi-Fi (easiest for testing):** use `http://<laptop-IP>:8000`. Find the IP with `ipconfig` (Windows) or `ip a` (Linux).
- **Anywhere (mobile data):** `ngrok http 8000` gives an `https://…ngrok-free.app` URL. Or deploy to the cloud with the Dockerfile.

Twilio settings aren't needed for this mode.

## 2. Build the APK (pick one)
**A. GitHub (no Android Studio needed):** push this whole folder to a GitHub repo. Then *Actions → Build Android APK → latest run → Artifacts → CallAgent-apk* → download `app-debug.apk`.

**B. Android Studio:** *Open* the `android/` folder → wait for Gradle sync → *Build → Build APK(s)*.

## 3. Install and set up on the phone
1. Copy `app-debug.apk` to the phone and install it (allow "Install unknown apps").
2. Open **Call Agent** and enter the **Server URL** and **Agent key** → **Save**.
3. Tap **1. Grant permissions** and allow microphone, phone, call logs and answering calls.
4. **Android 13+ only:** go to *Settings → Apps → Call Agent → ⋮ (top-right) → Allow restricted settings*. Without this, the accessibility switch stays greyed out for sideloaded apps.
5. Tap **2. Turn on accessibility service** → *Call Agent (auto-answer)* → ON.
6. Tap **3. Test server connection**. It should show `HTTP 200`.
7. Tap **4. Test AI voice**. You should hear the Tamil/Sinhala/English greeting.
8. Turn on **Auto-answer calls with AI** → **Save**.
9. Xiaomi/Redmi, Oppo, Vivo, Realme, Samsung: set *Battery → Call Agent → No restrictions / Unrestricted* and enable *Autostart*. Otherwise the system kills the service.

## 4. Test with a real call
Ask a friend to call you and don't answer. After the delay (default 10 s), the app picks up, turns on the speaker, and the AI talks. Open the app and tap **Refresh log** to see what happened. You also get a Telegram summary if it's configured on the server.

## Troubleshooting (read the log in the app)
| Log says / symptom | Fix |
|---|---|
| `Auto-answer failed` | Grant *Answer phone calls* permission (step 3). |
| `Mic: peak=0 … SILENCE` | This phone blocks the mic during calls even for accessibility services. Check step 4–5, disable then re-enable the service, and keep the in-call screen open. Some heavily customised ROMs (certain MIUI/ColorOS builds) can't do this without root. |
| Caller can't hear the AI | Change **AI voice plays as** (try *Media*, then *Alarm*, then *Voice call*), Save, and test again. Phones differ. |
| Speaker doesn't turn on | The app also presses the dialer's *Speaker* button. If your dialer uses another word, edit `labels` in `CallAgentService.kt`. |
| AI cuts the caller off / waits too long | Change **End of speech = silence of (ms)**: 1200–1500 for slow speakers, 800 for fast. |
| `HTTP 401` | Agent key in the app ≠ `PHONE_AGENT_KEY` in `.env`. |

## Honest limitations
- **Audio quality:** the AI's voice travels speaker → air → mic → network, so the caller hears it slightly echoey and quieter than a normal call. That's the trade-off for "no root, no Twilio".
- **Half-duplex:** the AI doesn't listen while it's talking, so callers who interrupt get ignored until it finishes.
- **Phone must stay on** with internet, and the server must be running.
- For a clean, professional audio path, use the Twilio mode (README.md) or a rooted phone or GSM gateway.
