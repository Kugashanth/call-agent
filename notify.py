"""Send the owner a call summary on Telegram (optional)."""
import logging

import requests

import config

log = logging.getLogger("notify")


def call_summary(call: dict) -> str:
    lines = [
        f"📞 Call handled by AI ({call.get('channel', 'twilio')})",
        f"From: {call.get('from')}",
        f"Caller name: {call.get('caller_name') or '-'}",
        f"Language: {call.get('lang')}",
    ]
    if call.get("messages"):
        lines.append("Message: " + " | ".join(call["messages"]))
    lines.append("\nTranscript:")
    for t in call.get("turns", [])[-8:]:
        lines.append(f"👤 {t['caller']}\n🤖 {t['agent']}")
    return "\n".join(lines)[:4000]


def send(call: dict) -> None:
    if not (config.TELEGRAM_BOT_TOKEN and config.TELEGRAM_CHAT_ID):
        return
    try:
        requests.post(
            f"https://api.telegram.org/bot{config.TELEGRAM_BOT_TOKEN}/sendMessage",
            json={"chat_id": config.TELEGRAM_CHAT_ID, "text": call_summary(call)},
            timeout=10,
        ).raise_for_status()
    except Exception:
        log.exception("Telegram notify failed")
