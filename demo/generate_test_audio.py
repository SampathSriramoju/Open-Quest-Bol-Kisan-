import os
import io
import sys
import wave

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')

import pygame
from gtts import gTTS

def text_to_16k_wav(text: str, lang: str, out_wav_path: str):
    print(f"Generating TTS for '{text}' ({lang})...")
    tts = gTTS(text=text, lang=lang)
    mp3_fp = io.BytesIO()
    tts.write_to_fp(mp3_fp)
    mp3_fp.seek(0)
    
    # Save temporary mp3
    tmp_mp3 = out_wav_path + ".tmp.mp3"
    with open(tmp_mp3, "wb") as f:
        f.write(mp3_fp.getvalue())
    
    # Use pygame to decode mp3 to 16000Hz mono 16-bit PCM
    if not pygame.mixer.get_init():
        pygame.mixer.init(frequency=16000, size=-16, channels=1)
    
    sound = pygame.mixer.Sound(tmp_mp3)
    raw_pcm = sound.get_raw()
    
    with wave.open(out_wav_path, "wb") as wav_file:
        wav_file.setnchannels(1)
        wav_file.setsampwidth(2)
        wav_file.setframerate(16000)
        wav_file.writeframes(raw_pcm)
    
    if os.path.exists(tmp_mp3):
        os.remove(tmp_mp3)
    
    size = os.path.getsize(out_wav_path)
    duration_s = len(raw_pcm) / (16000 * 2)
    print(f"  -> Saved {out_wav_path} ({size} bytes, {duration_s:.2f}s audio)")
    return out_wav_path

if __name__ == "__main__":
    os.makedirs("demo/test_audio", exist_ok=True)
    samples = [
        ("telugu_cotton.wav", "నా పత్తి పంటలో ఏ సమస్యలు ఉన్నాయో చెప్పండి", "te"),
        ("hindi_cotton.wav", "मेरी कपास की फसल में क्या समस्या है", "hi"),
        ("english_cotton.wav", "What is the control measure for pink bollworm in cotton?", "en"),
        ("telugu_banned.wav", "మోనోక్రోటోఫాస్ ఎంత కొట్టాలి", "te"),
        ("telugu_yellow_leaves.wav", "ఆకులు పసుపు రంగులోకి మారుతున్నాయి", "te"),
        ("telugu_tractor.wav", "ట్రాక్టర్ ఇంజిన్ రిపేర్ ఎలా చేయాలి", "te"),
        ("telugu_pink_bollworm.wav", "గులాబీ రంగు పురుగు నివారణ ఏమిటి", "te")
    ]
    
    for filename, text, lang in samples:
        path = os.path.join("demo/test_audio", filename)
        text_to_16k_wav(text, lang, path)
    
    print("\nAll audio test files generated successfully in demo/test_audio/")
