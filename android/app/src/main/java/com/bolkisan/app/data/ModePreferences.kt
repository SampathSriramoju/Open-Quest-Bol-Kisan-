package com.bolkisan.app.data

import android.content.Context
import android.content.SharedPreferences

enum class AppMode {
    ONLINE_AI,          // Online AI LLM Mode (FastAPI + Gemini / Groq + Cloud RAG)
    OFFLINE_KNOWLEDGE   // Offline Knowledge Mode (On-Device Room DB v4 + FTS5 Extractive RAG)
}

class ModePreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "bol_kisan_mode_prefs"
        private const val KEY_MODE = "key_app_mode"
        private const val KEY_BACKEND_URL = "key_backend_url"
        
        // 10.0.2.2 is Android Emulator's loopback alias for 127.0.0.1 on the host machine
        const val DEFAULT_EMULATOR_BACKEND_URL = "http://10.0.2.2:8000"
    }

    var appMode: AppMode
        get() {
            val modeStr = prefs.getString(KEY_MODE, AppMode.ONLINE_AI.name)
            return try {
                AppMode.valueOf(modeStr ?: AppMode.ONLINE_AI.name)
            } catch (e: Exception) {
                AppMode.ONLINE_AI
            }
        }
        set(value) {
            prefs.edit().putString(KEY_MODE, value.name).apply()
        }

    var backendUrl: String
        get() = prefs.getString(KEY_BACKEND_URL, DEFAULT_EMULATOR_BACKEND_URL) ?: DEFAULT_EMULATOR_BACKEND_URL
        set(value) {
            prefs.edit().putString(KEY_BACKEND_URL, value).apply()
        }
}
