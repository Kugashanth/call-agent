"""All settings in one place. Secrets come from .env (see .env.example)."""
import os
from dotenv import load_dotenv

load_dotenv()

# --- Server ---
PUBLIC_BASE_URL = os.getenv("PUBLIC_BASE_URL", "").rstrip("/")  # e.g. https://abc.ngrok-free.app
VALIDATE_TWILIO = os.getenv("VALIDATE_TWILIO", "1") == "1"       # verify X-Twilio-Signature
DELETE_RECORDINGS = os.getenv("DELETE_RECORDINGS", "1") == "1"   # delete caller audio from Twilio after use

# --- Android app (phone answers the call itself) ---
PHONE_AGENT_KEY = os.getenv("PHONE_AGENT_KEY", "")  # shared secret; the app sends it as X-Agent-Key

# --- Twilio ---
TWILIO_ACCOUNT_SID = os.getenv("TWILIO_ACCOUNT_SID", "")
TWILIO_AUTH_TOKEN = os.getenv("TWILIO_AUTH_TOKEN", "")

# --- Azure Speech (STT + TTS) ---
AZURE_SPEECH_KEY = os.getenv("AZURE_SPEECH_KEY", "")
AZURE_SPEECH_REGION = os.getenv("AZURE_SPEECH_REGION", "southeastasia")

# --- LLM (Gemini) ---
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", "")
GEMINI_MODEL = os.getenv("GEMINI_MODEL", "gemini-2.5-flash")

# --- Owner / notifications ---
OWNER_NAME = os.getenv("OWNER_NAME", "Kathir")
TELEGRAM_BOT_TOKEN = os.getenv("TELEGRAM_BOT_TOKEN", "")
TELEGRAM_CHAT_ID = os.getenv("TELEGRAM_CHAT_ID", "")

# --- Conversation limits ---
MAX_TURNS = int(os.getenv("MAX_TURNS", "12"))
MAX_NO_INPUT = 2

# Speech-to-text candidate locales (Azure at-start language ID allows up to 4).
# en-IN copes with South-Asian accents and romanised Tanglish/Singlish better than en-US.
STT_LOCALES = ["en-IN", "ta-IN", "si-LK"]

# Text-to-speech voices per reply language
VOICES = {
    "en": os.getenv("VOICE_EN", "en-IN-NeerjaNeural"),
    "ta": os.getenv("VOICE_TA", "ta-LK-SaranyaNeural"),
    "si": os.getenv("VOICE_SI", "si-LK-ThiliniNeural"),
}

# Fixed phrases (edit freely). {owner} is filled in automatically.
TEXTS = {
    "ta": {
        "greeting": "வணக்கம்! இது {owner} அவர்களின் AI உதவியாளர்.",
        "reprompt": "மன்னிக்கவும், சரியாக கேட்கவில்லை. மீண்டும் சொல்லுங்கள்.",
        "goodbye": "நன்றி! வணக்கம்.",
        "error": "மன்னிக்கவும், ஒரு தொழில்நுட்ப பிரச்சனை. {owner} உங்களை பிறகு அழைப்பார்.",
    },
    "si": {
        "greeting": "ආයුබෝවන්! මේ {owner}ගේ AI සහායකයා.",
        "reprompt": "සමාවන්න, හරියට ඇහුණේ නැහැ. කරුණාකර නැවත කියන්න.",
        "goodbye": "ස්තූතියි! ආයුබෝවන්.",
        "error": "සමාවන්න, තාක්ෂණික ගැටලුවක්. {owner} පසුව ඔබට කතා කරයි.",
    },
    "en": {
        "greeting": "Hello! You've reached {owner}'s AI assistant. You can speak in Tamil, Sinhala or English. How can I help you?",
        "reprompt": "Sorry, I didn't catch that. Could you say it again?",
        "goodbye": "Thank you for calling. Goodbye!",
        "error": "Sorry, there's a technical problem. {owner} will call you back.",
    },
}
GREETING_ORDER = ["ta", "si", "en"]  # greeting is played in each language, in this order


def text(lang: str, key: str) -> str:
    return TEXTS.get(lang, TEXTS["en"])[key].format(owner=OWNER_NAME)
