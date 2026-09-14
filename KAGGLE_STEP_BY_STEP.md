# MicUp RVC Kaggle - COMPLETE SETUP GUIDE

## 🎯 Tujuan
Setup RVC inference API di Kaggle yang bisa diakses dari MicUp app via HTTP.

---

## 📋 STEP 1: DOWNLOAD MODEL DARI voice-models.com

### ✅ Yang lu lakukan di local PC:

1. Buka: https://voice-models.com/model/1qd42AajsNe
2. Download 2 file:
   - `okada.pth` (model weights, ~500MB)
   - `okada.index` (voice search index, ~300MB)
3. Save ke folder: `~/MicUp-RVC-Models/`

**Expected output:**
```
~/MicUp-RVC-Models/
├── okada.pth
└── okada.index
```

---

## 🎯 STEP 2: UPLOAD MODEL KE KAGGLE DATASET

### ✅ Setup Kaggle CLI (first time only):

```bash
# Install kaggle CLI
pip install kaggle

# Download API key dari Kaggle settings
# 1. Go to: https://www.kaggle.com/settings/account
# 2. Click "Create New API Token"
# 3. Save kaggle.json ke ~/.kaggle/kaggle.json

# Set permissions
chmod 600 ~/.kaggle/kaggle.json
```

### ✅ Create & Upload Dataset:

```bash
# Create dataset folder
mkdir -p ~/kaggle-datasets/rvc-models-okada
cd ~/kaggle-datasets/rvc-models-okada

# Copy model files
cp ~/MicUp-RVC-Models/okada.pth .
cp ~/MicUp-RVC-Models/okada.index .

# Create dataset.json
cat > dataset-metadata.json << 'EOF'
{
  "id": "rvc-models-okada",
  "licenses": [{"name": "CC0-1.0"}],
  "resources": [
    {"path": "okada.pth"},
    {"path": "okada.index"}
  ],
  "title": "RVC Model - Okada Kazuchika",
  "subtitle": "Voice model for MicUp RVC",
  "description": "RVC voice model (Okada Kazuchika) for real-time voice conversion",
  "isPrivate": false,
  "keywords": ["rvc", "voice-conversion", "okada"]
}
EOF

# Upload to Kaggle
kaggle datasets create -p .

# Output akan seperti:
# Dataset slug: username/rvc-models-okada
```

**⚠️ Important:** Catat dataset slug mu: `username/rvc-models-okada`

---

## 🎯 STEP 3: CREATE KAGGLE NOTEBOOK

### ✅ Buka Kaggle & buat notebook baru:

1. Go to: https://www.kaggle.com/code
2. Click "New Notebook"
3. Name: `rvc-inference` (or any name)
4. Language: Python
5. **PENTING:** Click "⚙️ Notebook Settings" → Accelerator: **GPU** ✅

---

## 🎯 STEP 4: COPY CODE KE KAGGLE NOTEBOOK

### ✅ Cell 1: Install Dependencies

```python
!pip install -q flask flask-cors librosa soundfile torch torchaudio
!pip install -q numpy scipy
```

### ✅ Cell 2: Mount Dataset & Setup

```python
import os
import sys
import torch
import librosa
import numpy as np
from flask import Flask, request, jsonify
from flask_cors import CORS
import base64
import io
import logging
import time
from typing import Tuple

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Configuration
DEVICE = "cuda" if torch.cuda.is_available() else "cpu"
SAMPLE_RATE = 44100

logger.info(f"✅ Device: {DEVICE}")
logger.info(f"✅ GPU Available: {torch.cuda.is_available()}")
if torch.cuda.is_available():
    logger.info(f"✅ GPU Name: {torch.cuda.get_device_name(0)}")
    logger.info(f"✅ GPU Memory: {torch.cuda.get_device_properties(0).total_memory / 1024**3:.1f} GB")

# Check model files
model_path = "/kaggle/input/rvc-models-okada/okada.pth"
index_path = "/kaggle/input/rvc-models-okada/okada.index"

logger.info(f"Model exists: {os.path.exists(model_path)}")
logger.info(f"Index exists: {os.path.exists(index_path)}")
```

### ✅ Cell 3: Audio Utilities

```python
def base64_to_audio(audio_base64: str) -> Tuple[np.ndarray, int]:
    """Decode base64 WAV to audio array."""
    try:
        audio_bytes = base64.b64decode(audio_base64)
        y, sr = librosa.load(io.BytesIO(audio_bytes), sr=SAMPLE_RATE, mono=True)
        return y, sr
    except Exception as e:
        logger.error(f"Failed to decode audio: {e}")
        return None, None

def audio_to_base64(y: np.ndarray, sr: int = SAMPLE_RATE) -> str:
    """Encode audio array to base64 WAV."""
    try:
        import soundfile as sf
        buffer = io.BytesIO()
        sf.write(buffer, y, sr, format='WAV')
        buffer.seek(0)
        audio_bytes = buffer.read()
        audio_base64 = base64.b64encode(audio_bytes).decode('utf-8')
        return audio_base64
    except Exception as e:
        logger.error(f"Failed to encode audio: {e}")
        return None

logger.info("✅ Audio utilities loaded")
```

### ✅ Cell 4: Load RVC Model (PLACEHOLDER)

```python
# TODO: Load actual RVC model here
# For now, we'll echo audio back (placeholder)

rvc_model = {"loaded": True, "device": DEVICE}
rvc_index = None

logger.info("⚠️  RVC model loading is a placeholder")
logger.info("⚠️  In production, you need to:")
logger.info("   1. Clone RVC repo: git clone https://github.com/RVC-Project/Retrieval-based-Voice-Conversion-WebUI.git")
logger.info("   2. Load model.pth with: model = load_checkpoint(model_path, device)")
logger.info("   3. Process with: output_audio = model.infer(input_audio, speaker_id, pitch_shift)")

logger.info("✅ Model holder initialized")
```

### ✅ Cell 5: Flask API

```python
app = Flask(__name__)
CORS(app)

@app.route("/health", methods=["GET"])
def health():
    """Health check endpoint."""
    return jsonify({
        "status": "healthy",
        "device": DEVICE,
        "model_loaded": rvc_model is not None,
        "gpu_available": torch.cuda.is_available(),
        "gpu_name": torch.cuda.get_device_name(0) if torch.cuda.is_available() else "N/A",
    }), 200

@app.route("/process", methods=["POST"])
def process_audio():
    """Process audio with RVC."""
    start_time = time.time()
    
    try:
        data = request.json
        logger.info(f"Processing: pitch={data.get('pitch')}, f0_method={data.get('f0Method')}")
        
        # Validate
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
        
        # TODO: RVC inference here
        # For now, echo audio back
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
        logger.error(f"Error: {e}", exc_info=True)
        return jsonify({
            "status": "error",
            "error": str(e)
        }), 500

@app.route("/info", methods=["GET"])
def info():
    """Model information."""
    return jsonify({
        "model_name": "Okada Kazuchika",
        "sample_rate": SAMPLE_RATE,
        "device": DEVICE,
    }), 200

logger.info("✅ Flask routes defined")
```

### ✅ Cell 6: Start Server

```python
logger.info("🚀 Starting Flask server...")
logger.info("📍 Endpoints: /health, /process, /info")

try:
    app.run(host="0.0.0.0", port=7860, debug=False, threaded=True, use_reloader=False)
except KeyboardInterrupt:
    logger.info("Server stopped")
```

---

## 🎯 STEP 5: TEST DI KAGGLE

### ✅ Run notebook:

1. Click "Run All" (atau Ctrl+Enter tiap cell)
2. Wait sampai Cell 6 finished
3. Output akan show:
   ```
   WARNING: This is a development server. Do not use it in production.
   Running on http://0.0.0.0:7860
   ```
4. Click "Share" button di notebook → "Public" ✅
5. Copy public notebook URL

### ✅ Test /health endpoint:

Buka terminal baru:
```bash
curl https://www.kaggle.com/api/v1/notebooks/[USERNAME]/rvc-inference/http/health
```

Expected output:
```json
{
  "status": "healthy",
  "device": "cuda",
  "model_loaded": true,
  "gpu_available": true,
  "gpu_name": "NVIDIA Tesla P100-PCIE-16GB"
}
```

---

## 🎯 STEP 6: PASTE URL KE MICUP APP

### ✅ Format:

Buka MicUp app → RVC tab → Kaggle Endpoint field

Paste:
```
https://www.kaggle.com/api/v1/notebooks/[USERNAME]/[NOTEBOOK-SLUG]/http
```

Contoh:
```
https://www.kaggle.com/api/v1/notebooks/asrolakimprowindows/rvc-inference/http
```

Click "Test Connection" → Should show 🟢 Connected

---

## ⚠️ TROUBLESHOOTING

### ❌ "Notebook not found"
- Make sure notebook is **Public** (Share → Public)
- Double-check URL spelling

### ❌ "Connection timeout"
- Notebook might be stopped
- Open notebook → Click "Run All" again
- Wait for server to start (Cell 6)

### ❌ "403 Forbidden"
- Notebook is Private
- Share → Change to Public

### ❌ High latency
- Kaggle GPU might be throttled
- Reduce chunk size in app
- Optimize model (quantization)

---

## 📝 NEXT: Actual RVC Implementation

Code di atas adalah **placeholder**. Untuk real RVC inference, lu perlu:

```python
# Install RVC library
!pip install git+https://github.com/RVC-Project/Retrieval-based-Voice-Conversion-WebUI.git

# Load & use model
from rvc.inference_main import VC

vc = VC(MODEL_PATH, "cuda", is_half=True)
audio_out = vc.pipeline(
    hubert_model,
    net_g,
    audio_input,
    speaker_id,
    f0_up_key=pitch_shift,
    f0_method=f0_method,
    file_index=INDEX_PATH,
    index_rate=index_rate,
)
```

Tapi untuk testing, placeholder sudah cukup! 🚀

---

**Ready? Let me know kalau ada error!** 💪
