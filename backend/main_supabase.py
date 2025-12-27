"""
FastAPI Backend для Discord Nameplates с Supabase
Хранит соответствие Minecraft UUID -> Discord userId -> display_name
"""

import os
from dotenv import load_dotenv

# Загружаем переменные окружения из .env файла
load_dotenv()

import secrets
import time
from datetime import datetime, timedelta
from typing import Optional, Dict
from urllib.parse import urlencode

import httpx
from fastapi import FastAPI, HTTPException, Query, Request
from fastapi.responses import RedirectResponse, JSONResponse
from pydantic import BaseModel
from supabase import create_client, Client

app = FastAPI(title="Discord Nameplates Backend (Supabase)")

# Discord OAuth2 settings
DISCORD_CLIENT_ID = os.getenv("DISCORD_CLIENT_ID", "")
DISCORD_CLIENT_SECRET = os.getenv("DISCORD_CLIENT_SECRET", "")
DISCORD_REDIRECT_URI = os.getenv("DISCORD_REDIRECT_URI", "")
DISCORD_API_BASE = "https://discord.com/api/v10"

# Supabase settings
SUPABASE_URL = os.getenv("SUPABASE_URL", "")
SUPABASE_KEY = os.getenv("SUPABASE_KEY", "")

# In-memory state store для OAuth2 (в продакшене использовать Redis)
oauth_states: Dict[str, Dict] = {}

# Supabase client
supabase: Optional[Client] = None

if SUPABASE_URL and SUPABASE_KEY:
    try:
        supabase = create_client(SUPABASE_URL, SUPABASE_KEY)
        print("✅ Supabase client initialized")
    except Exception as e:
        print(f"❌ Failed to initialize Supabase: {e}")


def init_db():
    """Инициализация базы данных в Supabase"""
    if not supabase:
        print("⚠️  Supabase not configured, skipping database init")
        return
    
    try:
        # Проверяем, существует ли таблица (через запрос)
        result = supabase.table("discord_links").select("mc_uuid").limit(1).execute()
        print("✅ Supabase table 'discord_links' exists")
    except Exception as e:
        print(f"⚠️  Table check failed: {e}")
        print("Please create the table in Supabase Dashboard:")
        print("""
CREATE TABLE discord_links (
    mc_uuid TEXT PRIMARY KEY,
    discord_user_id TEXT NOT NULL,
    display_name TEXT NOT NULL,
    updated_at BIGINT NOT NULL,
    consent INTEGER NOT NULL DEFAULT 1
);
        """)


# Инициализация при старте
init_db()


@app.get("/health")
async def health():
    """Health check endpoint"""
    return {"status": "ok", "database": "supabase" if supabase else "not configured"}


@app.get("/link/start")
async def link_start(mc_uuid: str = Query(..., description="Minecraft UUID")):
    """Начало OAuth2 flow"""
    if not DISCORD_CLIENT_ID or not DISCORD_REDIRECT_URI:
        raise HTTPException(status_code=500, detail="Discord OAuth2 not configured")
    
    # Генерируем state для безопасности
    state = secrets.token_urlsafe(32)
    oauth_states[state] = {
        "mc_uuid": mc_uuid,
        "timestamp": time.time()
    }
    
    # Параметры для Discord OAuth2
    params = {
        "client_id": DISCORD_CLIENT_ID,
        "redirect_uri": DISCORD_REDIRECT_URI,
        "response_type": "code",
        "scope": "identify",
        "state": state
    }
    
    auth_url = f"{DISCORD_API_BASE}/oauth2/authorize?{urlencode(params)}"
    return RedirectResponse(url=auth_url)


@app.get("/link/callback")
async def link_callback(
    code: str = Query(..., description="Authorization code"),
    state: str = Query(..., description="State parameter")
):
    """Обработка OAuth2 callback"""
    if state not in oauth_states:
        raise HTTPException(status_code=400, detail="Invalid state")
    
    state_data = oauth_states.pop(state)
    mc_uuid = state_data["mc_uuid"]
    
    # Проверяем, не устарел ли state (10 минут)
    if time.time() - state_data["timestamp"] > 600:
        raise HTTPException(status_code=400, detail="State expired")
    
    if not DISCORD_CLIENT_ID or not DISCORD_CLIENT_SECRET or not DISCORD_REDIRECT_URI:
        raise HTTPException(status_code=500, detail="Discord OAuth2 not configured")
    
    # Обмениваем code на access_token
    async with httpx.AsyncClient() as client:
        token_response = await client.post(
            f"{DISCORD_API_BASE}/oauth2/token",
            data={
                "client_id": DISCORD_CLIENT_ID,
                "client_secret": DISCORD_CLIENT_SECRET,
                "grant_type": "authorization_code",
                "code": code,
                "redirect_uri": DISCORD_REDIRECT_URI
            },
            headers={"Content-Type": "application/x-www-form-urlencoded"}
        )
        
        if token_response.status_code != 200:
            raise HTTPException(status_code=400, detail="Failed to exchange code for token")
        
        token_data = token_response.json()
        access_token = token_data["access_token"]
        
        # Получаем информацию о пользователе
        user_response = await client.get(
            f"{DISCORD_API_BASE}/users/@me",
            headers={"Authorization": f"Bearer {access_token}"}
        )
        
        if user_response.status_code != 200:
            raise HTTPException(status_code=400, detail="Failed to get user info")
        
        user_data = user_response.json()
        discord_user_id = user_data["id"]
        display_name = user_data.get("global_name") or user_data.get("username", "")
    
    # Сохраняем в Supabase
    if not supabase:
        raise HTTPException(status_code=500, detail="Supabase not configured")
    
    try:
        # Используем upsert для обновления или создания записи
        supabase.table("discord_links").upsert({
            "mc_uuid": mc_uuid,
            "discord_user_id": discord_user_id,
            "display_name": display_name,
            "updated_at": int(time.time()),
            "consent": 1
        }).execute()
        
        return JSONResponse({
            "status": "success",
            "message": "Discord account linked successfully",
            "display_name": display_name
        })
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Database error: {str(e)}")


@app.get("/lookup")
async def lookup(mc_uuid: str = Query(..., description="Minecraft UUID")):
    """Получение display_name для Minecraft UUID"""
    if not supabase:
        raise HTTPException(status_code=500, detail="Supabase not configured")
    
    try:
        result = supabase.table("discord_links").select("display_name, updated_at").eq("mc_uuid", mc_uuid).execute()
        
        if not result.data:
            return JSONResponse(
                {"error": "Not found"},
                status_code=404
            )
        
        link_data = result.data[0]
        return {
            "display_name": link_data["display_name"],
            "updated_at": link_data["updated_at"]
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Database error: {str(e)}")


@app.post("/unlink")
async def unlink(mc_uuid: str = Query(..., description="Minecraft UUID")):
    """Удаление привязки"""
    if not supabase:
        raise HTTPException(status_code=500, detail="Supabase not configured")
    
    try:
        supabase.table("discord_links").delete().eq("mc_uuid", mc_uuid).execute()
        return {"status": "success", "message": "Link removed"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Database error: {str(e)}")


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)

