#!/usr/bin/env bash
set -e

ASSETS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/app/src/main/assets/models"
mkdir -p "$ASSETS_DIR"

echo "=========================================================="
echo "   Downloading Pre-trained ML Models for DocWarp AI      "
echo "=========================================================="

# 1. Google MediaPipe Hand Landmarker (Fingertip detection)
MP_URL="https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task"
MP_TARGET="$ASSETS_DIR/hand_landmarker.task"
if [ ! -f "$MP_TARGET" ]; then
    echo "[1/2] Downloading MediaPipe Hand Landmarker (float16)..."
    curl -L -o "$MP_TARGET" "$MP_URL"
    echo "      Downloaded hand_landmarker.task successfully."
else
    echo "[1/2] hand_landmarker.task already exists. Skipping."
fi

# 2. LaMa Mobile ONNX Inpainter
LAMA_URL="https://huggingface.co/sapienkit/LaMa-ONNX/resolve/main/lama_fp32.onnx"
LAMA_TARGET="$ASSETS_DIR/lama_fp32.onnx"
if [ ! -f "$LAMA_TARGET" ] && [ ! -f "$ASSETS_DIR/lama_fp16.onnx" ]; then
    echo "[2/2] Downloading LaMa Mobile Inpainting Model (~200MB)..."
    curl -L -o "$LAMA_TARGET" "$LAMA_URL"
    echo "      Downloaded lama_fp32.onnx successfully."
else
    echo "[2/2] LaMa ONNX model already exists. Skipping."
fi

echo -e "\nAll models verified and placed in $ASSETS_DIR"
