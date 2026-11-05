# Publishing "Call Agent" on Google Play

The **play** flavor is your AI call agent's app. It works with the phone's normal call app: unanswered, busy or unreachable calls are forwarded (standard GSM codes) to your agent in the cloud (Twilio mode), which talks to the caller, collects the information you ask for, and records the conversation with an announced recording notice. The app shows a notification for each call, plus the **voice recording**, the **script** and the **information collected**. It requests only INTERNET and notification permissions: no accessibility service, microphone, call log or phone permissions, so it avoids the policy problems of the sideload app.

> The **sideload** auto-answer app (CallAgent.apk) must **never** be uploaded to Play. Google Play does not allow the Accessibility API for call audio.

---

## Step 1: Create your Google Play developer account (only you can do this)
1. Go to https://play.google.com/console/signup and sign in with your Google account.
2. Choose **Personal** account. Pay the **one-time US$25** fee and complete **identity verification** (ID document).
3. **New personal accounts must run a closed test with at least 12 testers for 14 days in a row** before they can publish to production. Line up 12+ friends or classmates with Android phones early. (Check the Play Console for the current numbers.)

## Step 2: Create your upload key and add it to GitHub (one time)
On your laptop, with Java installed:
```bash
keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
# choose a strong password and remember it
base64 -w0 upload.jks > upload.jks.b64        # Windows PowerShell: [Convert]::ToBase64String([IO.File]::ReadAllBytes("upload.jks")) > upload.jks.b64
```
GitHub → **call-agent → Settings → Secrets and variables → Actions → New repository secret**:

| Name | Value |
|---|---|
| `KEYSTORE_BASE64` | contents of `upload.jks.b64` |
| `KEYSTORE_PASSWORD` | your keystore password |
| `KEY_ALIAS` | `upload` |
| `KEY_PASSWORD` | your key password (same as keystore password if you pressed Enter) |

Then **Actions → Build Android apps → Run workflow**. The release page will then also contain **CallAgent-play.aab**, the file you upload to Play.

⚠️ Back up `upload.jks` and its password somewhere safe (not in git). If you lose it, you must ask Google to reset the upload key.
Keep **Play App Signing** turned on (the default), so Google holds the real app signing key.

## Step 3: Create the app in Play Console
**Create app** → name `Call Agent – AI Call Assistant` → App → Free → accept declarations.

### Store listing (copy-paste)
**App name (≤30):** `Call Agent – AI Call Assistant`

**Short description (≤80):**
`AI agent answers calls you miss, collects details & gives you recording + script`

**Full description:**
```
Call Agent is your personal AI call agent. It works with your phone's normal call app: calls you don't
answer, reject or can't take are forwarded to your agent, which talks to the caller for you.

Your agent:
• Speaks Tamil, Sinhala and English, and understands mixed speech like Tanglish and Singlish
• Tells every caller it is an AI assistant and that the call is recorded
• Answers only the questions you allow, and asks the questions YOU choose
  (name, reason, callback number, urgency, anything you add)
• Takes a message and ends the call politely

In the app you get, for every call:
• A notification with the caller and their message
• The full VOICE RECORDING of the conversation (play, save, share)
• The SCRIPT: a complete written transcript
• The INFORMATION COLLECTED, neatly listed
• One-tap call back

Also:
• Edit what your agent may say and which questions it asks
• Turn call forwarding on/off (when busy, unanswered or unreachable) with one tap
• Delete any call record any time

Privacy first:
• The app connects only to YOUR OWN Call Agent server; the developer receives no data
• No microphone, contacts or call-log access on your phone
• HTTPS required; your key is never backed up to the cloud

Requires your own Call Agent server (open source: github.com/Kugashanth/call-agent).
Try it instantly with built-in demo data: enter "demo" as the server address.
Call forwarding charges depend on your mobile operator.
```
**Category:** Communication. **Tags:** call assistant, voicemail.
**Contact email:** your email. **Privacy policy URL:** `https://github.com/Kugashanth/call-agent/blob/main/PRIVACY.md`

**Graphics:**
- App icon 512×512: `store/icon-512.png`
- Feature graphic 1024×500: `store/feature-1024x500.png`
- Phone screenshots (at least 2): install CallAgent-Dashboard.apk, tap **Try demo**, and take screenshots of the calls list, a call detail (recording + info + script) and the knowledge editor.

### App content (Policy → App content)
| Section | Answer |
|---|---|
| Privacy policy | URL above |
| Ads | No ads |
| **App access** | *All or some functionality is restricted* → give instructions: "Enter `demo` as Server URL and tap Save & connect. No account needed." |
| Content rating | Questionnaire → category *Utility/Productivity/Communication*, answer **No** to all violence/sexual/etc. questions → likely rated Everyone/3+ |
| Target audience | 18+ |
| News app | No |
| Government app | No |
| Financial features | None |
| Health | No |

### Data safety (honest answers)
The app sends data **only to a server the user configures and controls**; the developer collects nothing. Answer the form as follows:
- *Does your app collect or share any of the required user data types?* → **Yes**. The app displays call records fetched from the user's server.
- Data types → **Personal info: Phone number, Name**, **Messages: Other in-app messages** (messages, transcripts, collected info), **Audio: Voice or sound recordings** (call recordings)
  - Collected: **Yes** / Shared: **No**
  - Processed ephemerally: **No** (stored on the user's own server)
  - Required or optional: **Optional** (the app works in demo mode without it)
  - Purpose: **App functionality**
- *Is data encrypted in transit?* → **Yes** (HTTPS enforced for non-LAN servers)
- *Can users request deletion?* → **Yes** (delete in app, or on their server)

If you're unsure, describe the setup in the free-text fields.

## Step 4: Release
1. **Testing → Closed testing** → create a track → upload **CallAgent-play.aab** → add your 12+ testers' emails → roll out.
2. After the testing period: **Production → Create release** → upload the latest .aab → submit for review. Review typically takes from a few days to a week.
3. For updates, push to GitHub. The build number increases automatically, then you upload the new .aab.

## Before you submit (checklist)
- [ ] Server deployed on **HTTPS**, and `TWILIO_NUMBER` set in `.env`
- [ ] Demo mode tested on a real phone
- [ ] Screenshots taken from demo mode
- [ ] Privacy policy URL opens in a browser
- [ ] Signed .aab built (secrets added)
- [ ] 12+ closed testers lined up
