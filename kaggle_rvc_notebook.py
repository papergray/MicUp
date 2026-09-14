#!/usr/bin/env python3
"""
Kaggle RVC Inference Notebook

Run this in Kaggle notebook with GPU enabled.
Will expose a public API endpoint for MicUp app to use.

Requirements:
- GPU Accelerator enabled
- RVC model dataset mounted
- Flask installed
"""

import os
import sys
import torch
import librosa
import numpy as np
from flask import Flask, request, jsonify
from flask_cors import CORS
import base64
import io
from typing import Tuple
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# ==================== CONFIGURATION ====================

DEVICE = "cuda" if torch.cuda.is_available() else "cpu"
MODEL_PATH = "/kaggle/input/rvc-models-okada/okada.pth"
INDEX_PATH = "/kaggle/input/rvc-models-okada/okada.index"
SAMPLE_RATE = 44100

logger.info(f"🚀 Using device: {DEVICE}")
logger.info(f"📦 Model: {MODEL_PATH}")
logger.info(f"📊 Index: {INDEX_PATH}")

# ==================== INITIALIZE FLASK ====================

app = Flask(__name__)
CORS(app)  # Enable CORS for app requests

# Global model holder
rvc_model = None
rvc_index = None

# ==================== LOAD RVC MODEL ====================

def load_rvc_model():
    """
    Load RVC model and index.
    TODO: Implement actual RVC model loading
    For now, placeholder that echoes audio back.
    """
    global rvc_model, rvc_index
    
    try:
        logger.info("Loading RVC model...")
        
        # TODO: Import RVC library
        # from rvc.inference import get_vc
        # rvc_model, net_g, _ = get_vc(MODEL_PATH, 0, DEVICE, False)
        
        # Placeholder
        rvc_model = {"loaded": True, "device": DEVICE}
        rvc_index = None
        
        logger.info("✅ Model loaded successfully")
        return True
    except Exception as e:
        logger.error(f"❌ Failed to load model: {e}")
        return False

# ==================== AUDIO UTILITIES ====================

def base64_to_audio(audio_base64: str) -> Tuple[np.ndarray, int]:
    """
    Decode base64 WAV to audio array.
    """
    try:
        audio_bytes = base64.b64decode(audio_base64)
        # Use librosa to load WAV from bytes
        y, sr = librosa.load(io.BytesIO(audio_bytes), sr=SAMPLE_RATE, mono=True)
        return y, sr
    except Exception as e:
        logger.error(f"Failed to decode audio: {e}")
        return None, None

def audio_to_base64(y: np.ndarray, sr: int = SAMPLE_RATE) -> str:
    """
    Encode audio array to base64 WAV.
    """
    try:
        import soundfile as sf
        
        # Write to bytes buffer
        buffer = io.BytesIO()
        sf.write(buffer, y, sr, format='WAV')
        buffer.seek(0)
        
        # Encode to base64
        audio_bytes = buffer.read()
        audio_base64 = base64.b64encode(audio_bytes).decode('utf-8')
        return audio_base64
    except Exception as e:
        logger.error(f"Failed to encode audio: {e}")
        return None

# ==================== API ROUTES ====================

@app.route("/health", methods=["GET"])
def health():
    """
    Health check endpoint.
    MicUp uses this to test connection.
    """
    return jsonify({
        "status": "healthy",
        "device": DEVICE,
        "model_loaded": rvc_model is not None,
        "gpu_available": torch.cuda.is_available(),
        "gpu_name": torch.cuda.get_device_name(0) if torch.cuda.is_available() else "N/A",
    }), 200

@app.route("/process", methods=["POST"])
def process_audio():
    """
    Process audio with RVC.
    
    Expected JSON:
    {
        "audioBase64": "...",
        "pitch": 0,
        "formant": 0.0,
        "indexRate": 0.5,
        "protectConsonants": true,
        "speakerId": 0,
        "chunkSize": 1024,
        "hopLength": 256,
        "f0Method": "RMVPE"
    }
    """
    import time
    start_time = time.time()
    
    try:
        data = request.json
        logger.info(f"Processing audio: pitch={data.get('pitch')}, f0_method={data.get('f0Method')}")
        
        # Validate model
        if rvc_model is None:
            return jsonify({
                "status": "error",
                "error": "Model not loaded"
            }), 503
        
        # Decode audio
        audio_data, sr = base64_to_audio(data.get("audioBase64", ""))
        if audio_data is None:
            return jsonify({
                "status": "error",
                "error": "Failed to decode audio"
            }), 400
        
        logger.info(f"Audio shape: {audio_data.shape}, SR: {sr}")
        
        # TODO: RVC inference
        # audio_out = rvc_model.infer(
        #     audio=audio_data,
        #     speaker_id=data.get("speakerId", 0),
        #     pitch_shift=data.get("pitch", 0),
        #     f0_method=data.get("f0Method", "rmvpe").lower(),
        # )
        
        # For now, echo the audio back (placeholder)
        audio_out = audio_data
        
        # Encode output
        audio_base64 = audio_to_base64(audio_out, sr)
        if audio_base64 is None:
            return jsonify({
                "status": "error",
                "error": "Failed to encode audio"
            }), 500
        
        latency_ms = int((time.time() - start_time) * 1000)
        
        return jsonify({
            "status": "success",
            "audioBase64": audio_base64,
            "latencyMs": latency_ms
        }), 200
    
    except Exception as e:
        logger.error(f"Error processing audio: {e}", exc_info=True)
        return jsonify({
            "status": "error",
            "error": str(e)
        }), 500

@app.route("/info", methods=["GET"])
def info():
    """
    Get RVC model information.
    """
    return jsonify({
        "model_name": "Okada Kazuchika",
        "sample_rate": SAMPLE_RATE,
        "device": DEVICE,
        "gpu_memory": f"{torch.cuda.get_device_properties(0).total_memory / 1024**3:.1f} GB" if torch.cuda.is_available() else "N/A",
    }), 200

# ==================== MAIN ====================

if __name__ == "__main__":
    logger.info("🎙️ MicUp RVC Kaggle Inference API")
    logger.info(f"Device: {DEVICE}")
    
    # Load model on startup
    if not load_rvc_model():
        logger.warning("⚠️ Model loading failed, but continuing...")
    
    # Run Flask app
    logger.info("🚀 Starting Flask server...")
    logger.info("📍 Endpoints: /health, /process, /info")
    
    # Kaggle will expose this as public URL
    app.run(host="0.0.0.0", port=7860, debug=False, threaded=True)
