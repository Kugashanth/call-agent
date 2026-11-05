"""Owner dashboard API (used by the Play Store app). Same key as the phone API: header X-Agent-Key.

GET    /api/status              -> {"owner", "twilio_number"}
GET    /api/calls?limit=50      -> [{sid, from, started, ended, channel, lang, caller_name, messages, turns}]
GET    /api/calls/{sid}         -> full call record (with transcript)
DELETE /api/calls/{sid}
GET    /api/knowledge           -> {"text"}
PUT    /api/knowledge  {"text"} -> {"ok": true}
"""
import json
from pathlib import Path

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel, Field

import brain
import config
import conversation
from phone import check_key

router = APIRouter(prefix="/api", dependencies=[Depends(check_key)])
MAX_KNOWLEDGE = 20_000


def _path(sid: str) -> Path:
    if not conversation.valid_id(sid):
        raise HTTPException(status_code=400, detail="Invalid id")
    p = (conversation.CALL_DIR / f"{sid}.json").resolve()
    if p.parent != conversation.CALL_DIR.resolve() or not p.exists():
        raise HTTPException(status_code=404, detail="Not found")
    return p


@router.get("/status")
def status():
    return {"owner": config.OWNER_NAME, "twilio_number": config.TWILIO_NUMBER}


@router.get("/calls")
def calls(limit: int = Query(50, ge=1, le=200)):
    files = sorted(conversation.CALL_DIR.glob("*.json"), key=lambda f: f.stat().st_mtime, reverse=True)[:limit]
    out = []
    for f in files:
        try:
            c = json.loads(f.read_text(encoding="utf-8"))
        except Exception:
            continue
        out.append({k: c.get(k) for k in ("sid", "from", "started", "ended", "channel", "lang", "caller_name", "messages")}
                   | {"turns": len(c.get("turns") or [])})
    return out


@router.get("/calls/{sid}")
def call(sid: str):
    return json.loads(_path(sid).read_text(encoding="utf-8"))


@router.delete("/calls/{sid}")
def delete_call(sid: str):
    _path(sid).unlink()
    return {"ok": True}


class Knowledge(BaseModel):
    text: str = Field(..., max_length=MAX_KNOWLEDGE)


@router.get("/knowledge")
def get_knowledge():
    return {"text": brain.KNOWLEDGE_FILE.read_text(encoding="utf-8")}


@router.put("/knowledge")
def put_knowledge(k: Knowledge):
    brain.KNOWLEDGE_FILE.write_text(k.text, encoding="utf-8")
    return {"ok": True}
