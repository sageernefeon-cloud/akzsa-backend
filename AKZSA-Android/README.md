# AKZSA Android client

Native Kotlin Android IME with voice typing, AI commands, AI writing, Snippets, Backtrack and an inline GIF panel. Text/image embeddings use Google's MediaPipe Universal Embedder and the public LiteRT Text+Vision EmbeddingGemma 2 model. The model downloads automatically after the first app launch over unmetered Wi-Fi, is verified, and is mapped from app-private storage. WorkManager shows background progress for model download, base file scanning and semantic-vector indexing. OCR, index rows, embeddings and ranking are kept local; the FastAPI voice/command backend is separate.

See the root `README_FIRST.md` and `AUTOMATIC-AI-MODEL-AND-PROGRESS.md` before building. Google Play broad-storage access approval is a separate policy step; the user-authorized-folder mode is the fallback.
