# MicUp RVC Kaggle Setup Guide

## Prerequisites
- Kaggle account with GPU access
- RVC model files (`.pth` + `.index`) from voice-models.com
- Python 3.9+

## Step 1: Download RVC Model

1. Go to: https://voice-models.com/model/1qd42AajsNe
2. Download model files:
   - `okada.pth` (model weights)
   - `okada.index` (voice search index)
3. Upload both files to Kaggle dataset

## Step 2: Create Kaggle Dataset

```bash
# Create dataset folder
mkdir -p ~/kaggle/rvc-models/okada
cp okada.pth ~/kaggle/rvc-models/okada/
cp okada.index ~/kaggle/rvc-models/okada/

# Upload to Kaggle
kaggle datasets create -p ~/kaggle/rvc-models
```

## Step 3: Setup Kaggle Notebook

1. Go to https://www.kaggle.com/code
2. Create new notebook
3. Enable GPU (Code → Notebook Settings → Accelerator: GPU)
4. Copy the code from `kaggle_rvc_notebook.py` into cells
5. Run all cells to test

## Step 4: Deploy as Public API

The notebook will output a public URL like:
```
https://www.kaggle.com/api/v1/notebooks/username/rvc-inference/http
```

## Step 5: Configure in MicUp App

1. Open MicUp app → RVC tab
2. Paste Kaggle endpoint URL in "Kaggle Endpoint" field
3. Click "Test Connection"
4. Should show 🟢 Connected

## Troubleshooting

### Connection fails
- Check Kaggle endpoint URL is correct
- Verify notebook is running (show URL)
- Check internet connection

### RVC inference fails
- Check model files are correctly loaded
- Verify audio format (WAV, 44.1kHz, mono)
- Check GPU memory usage in Kaggle logs

### High latency
- Reduce chunk size in MicUp app
- Optimize model (quantization, pruning)
- Check Kaggle GPU availability

## API Endpoints

### `/health`
Check if API is running
```bash
curl https://your-kaggle-endpoint/health
```

Response:
```json
{
  "status": "healthy",
  "device": "cuda",
  "model_loaded": true
}
```

### `/process`
Process audio with RVC
```bash
curl -X POST https://your-kaggle-endpoint/process \
  -H "Content-Type: application/json" \
  -d @request.json
```

Request (request.json):
```json
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
```

Response:
```json
{
  "status": "success",
  "audioBase64": "...",
  "latencyMs": 2500
}
```
