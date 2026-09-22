# Lightweight PDF Reader for Android

A privacy-first Android PDF reader built around `PdfRenderer`. It opens PDFs from device storage without uploading them, renders pages lazily, and keeps only a small number of rendered bitmaps in memory.

## Included

- Android Storage Access Framework file picker
- Lazy page rendering in a scrolling list
- Zoom controls and page navigation
- Rotation support
- Small LRU bitmap cache to reduce memory use
- Works with large PDFs without loading the whole document

## Build

Open this repository in Android Studio Ladybug or newer and run the `app` configuration. The project uses Kotlin, Jetpack Compose, and the Android framework `PdfRenderer`, so it does not require a PDF SDK or native library.

> Note: Android's framework renderer does not expose text extraction, OCR, PDF compression, or password decryption. Those can be added later with optional libraries; keeping the core reader dependency-free is what makes it small and low-memory.
