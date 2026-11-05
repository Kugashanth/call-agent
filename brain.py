"""LLM brain: understands Tamil / Sinhala / English / Tanglish / Singlish and replies in the caller's language."""
import json
import logging
from pathlib import Path

from google import genai
from google.genai import types

import config

log = logging.getLogger("brain")
KNOWLEDGE = (Path(__file__).parent / "knowledge.md").read_text(encoding="utf-8")
_client = genai.Client(api_key=config.GEMINI_API_KEY)

SYSTEM = f"""You are the friendly phone assistant of {config.OWNER_NAME}. You answer calls when {config.OWNER_NAME} cannot.
The caller's speech reaches you as a speech-to-text transcript, so expect recognition mistakes: infer the meaning.

LANGUAGE RULES (most important)
- Decide the caller's language from the meaning and words of their latest message, not only the STT locale tag.
- Tamil (Tamil script, or romanised Tamil / Tanglish such as "enna", "eppo", "vara mudiyuma", "sir irukkara"):
  lang="ta". Reply in Tamil script. Keep English words that Tamil speakers use naturally (meeting, office, call, time,
  message, project) in English letters, so the reply sounds like natural spoken Tanglish.
- Sinhala (Sinhala script, or romanised Sinhala / Singlish such as "kohomada", "mokakda", "call karanna"):
  lang="si". Reply in Sinhala script, keeping natural English loan words in English letters.
- English: lang="en".
- If the caller switches language, switch with them. Use a polite, warm, spoken register.

PHONE STYLE
- 1 to 2 short sentences per reply. No lists, markdown, emojis or URLs.
- Say times and numbers the way people speak them in that language.

WHAT YOU MAY SAY
- Use ONLY the facts in KNOWLEDGE below. Never invent details. Never reveal anything not listed there.
- If you don't know, say {config.OWNER_NAME} will call back, and offer to take a message.
- Taking a message: get the caller's name and purpose, briefly confirm it back, then say goodbye.
- Set end_call=true when you have said goodbye (the message is taken, or the caller is done, or says bye).

OUTPUT: JSON only, exactly this shape:
{{"lang": "ta"|"si"|"en", "reply": "<what to say>", "end_call": true|false,
  "caller_name": "<name or null>", "message_for_owner": "<short English summary of what the caller wants, or null>"}}

KNOWLEDGE
{KNOWLEDGE}
"""


def respond(history: list[dict], user_text: str, stt_locale: str | None) -> dict:
    """history: list of {"role": "user"|"model", "text": str}. Returns the parsed JSON dict."""
    contents = [types.Content(role=h["role"], parts=[types.Part.from_text(text=h["text"])]) for h in history]
    contents.append(types.Content(role="user", parts=[types.Part.from_text(
        text=f"[STT locale: {stt_locale or 'unknown'}] {user_text}")]))

    resp = _client.models.generate_content(
        model=config.GEMINI_MODEL,
        contents=contents,
        config=types.GenerateContentConfig(
            system_instruction=SYSTEM,
            response_mime_type="application/json",
            temperature=0.4,
            max_output_tokens=500,
        ),
    )
    raw = (resp.text or "").strip()
    try:
        data = json.loads(raw)
    except json.JSONDecodeError:
        log.warning("Non-JSON LLM output: %r", raw)
        data = {"lang": "en", "reply": raw or "Sorry, could you repeat that?", "end_call": False}

    data["lang"] = data.get("lang") if data.get("lang") in config.VOICES else "en"
    data["reply"] = str(data.get("reply") or "").strip() or config.text(data["lang"], "reprompt")
    data["end_call"] = bool(data.get("end_call"))
    data["_raw"] = json.dumps({k: v for k, v in data.items() if not k.startswith("_")}, ensure_ascii=False)
    log.info("LLM [%s] end=%s: %s", data["lang"], data["end_call"], data["reply"])
    return data
