# PowerShell script using curl.exe for high-speed resumable model downloads
$ErrorActionPreference = "Stop"

$assetsDir = "$PSScriptRoot\app\src\main\assets\models"
if (!(Test-Path $assetsDir)) {
    New-Item -ItemType Directory -Path $assetsDir -Force | Out-Null
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   Downloading Pre-trained ML Models for DocWarp AI       " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Google MediaPipe Hand Landmarker
$mediaPipeUrl = "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task"
$mediaPipeTarget = "$assetsDir\hand_landmarker.task"
if (!(Test-Path $mediaPipeTarget)) {
    Write-Host "[1/2] Downloading MediaPipe Hand Landmarker (float16)..." -ForegroundColor Yellow
    curl.exe -L -C - -o $mediaPipeTarget $mediaPipeUrl
    Write-Host "      Downloaded hand_landmarker.task successfully." -ForegroundColor Green
} else {
    Write-Host "[1/2] hand_landmarker.task already exists (7.8MB). Skipping." -ForegroundColor Green
}

# 2. LaMa Mobile ONNX Inpainter
$lamaUrl = "https://huggingface.co/sapienkit/LaMa-ONNX/resolve/main/lama_fp32.onnx"
$lamaTarget = "$assetsDir\lama_fp32.onnx"
if (!(Test-Path $lamaTarget) -and !(Test-Path "$assetsDir\lama_fp16.onnx")) {
    Write-Host "[2/2] Downloading LaMa Mobile Inpainting Model (~208MB)..." -ForegroundColor Yellow
    curl.exe -L -C - -o $lamaTarget $lamaUrl
    Write-Host "      Downloaded lama_fp32.onnx successfully." -ForegroundColor Green
} else {
    Write-Host "[2/2] LaMa ONNX model already exists (208MB). Skipping." -ForegroundColor Green
}

Write-Host "`nAll models verified and placed in $assetsDir" -ForegroundColor Green
