# Publishing "Call Agent" on Google Play

The **play** flavor is a dashboard app. Calls are answered in the cloud by your server (Twilio mode), and the app shows calls, messages and transcripts, edits what the AI may say, and sets up call forwarding. It requests **only INTERNET**: no accessibility service, microphone, call log or phone permissions, so it avoids the policy problems of the sideload app.

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
`AI answers your missed calls in Tamil, Sinhala & English and saves messages.`

**Full description:**
```
Call Agent is the dashboard for your own AI call assistant.

When you can't pick up, calls you forward to your assistant are answered by an AI that speaks
Tamil, Sinhala and English — and understands mixed speech like Tanglish and Singlish. It tells
callers it is an AI, answers only the questions you allow, and takes a message for you.

With the Call Agent app you can:
• See every call your assistant answered: caller, time and language
• Read the message the caller left, plus the full conversation transcript
• Call back with one tap
• Edit what your assistant is allowed to tell callers (availability, services, FAQs)
• Turn call forwarding on or off with one tap (uses your phone's dialer)
• Delete call records any time

Privacy first:
• The app connects only to YOUR OWN Call Agent server — the developer receives no data
• No microphone, contacts or call-log access
• HTTPS required for remote servers; your key is never backed up to the cloud

Requires your own Call Agent server (open source: github.com/Kugashanth/call-agent).
Try the app instantly with built-in demo data: enter "demo" as the server address.

Call forwarding charges depend on your mobile operator.
```
**Category:** Communication. **Tags:** call assistant, voicemail.
**Contact email:** your email. **Privacy policy URL:** `https://github.com/Kugashanth/call-agent/blob/main/PRIVACY.md`

**Graphics:**
- App icon 512×512: `store/icon-512.png`
- Feature graphic 1024×500: `store/feature-1024x500.png`
- Phone screenshots (at least 2): install CallAgent-Dashboard.apk, tap **Try demo**, and take screenshots of the calls list, a call detail and the knowledge editor.

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
- Data types → **Personal info: Phone number**, **Messages: Other in-app messages** (call messages/transcripts)
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
