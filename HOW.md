# HOW IT WORKS

Dokumen ini menjelaskan bagaimana tiga fitur utama berjalan secara teknis, step by step: **Upload**, **Download**, dan **AI Summary PDF**.

---

## 1. UPLOAD FILE

### Gambaran Umum

Upload menggunakan strategi **chunked upload** — file dipecah menjadi potongan-potongan kecil (chunk) oleh client, lalu dikirim satu per satu ke server. Server mengumpulkan chunk, lalu secara bertahap mengirimkan ke storage node via gRPC.

### Step by Step

#### Step 1 — Client Inisialisasi Upload
```
POST /api/files/init
Body: { fileName, totalSize }
```
- Server menerima nama file dan total ukuran.
- Server cek apakah user punya cukup storage quota.
  - Quota default: **5 GB** jika tidak dikonfigurasi.
  - Diambil dari `fileRepository.calculateUsedStorageByUserId()`.
- Jika quota cukup, server membuat dua record di database:
  - **`File`** — metadata file (id, nama, ukuran, userId).
  - **`UploadSession`** — status sesi upload (totalChunks, uploadedChunks, status = `UPLOADING`).
- Server menghitung jumlah total chunk dari ukuran file.
- Server mengembalikan `fileId` kepada client.

#### Step 2 — Client Mengirim Chunk Satu per Satu
```
POST /api/files/{fileId}/chunks/{index}
Body: multipart/form-data (file = potongan file ke-N)
```
- Setiap chunk diterima oleh `UploadController`, lalu diserahkan ke `UploadCoordinator`.

**Di dalam `UploadCoordinator.handleChunkUpload()`:**

1. **Tulis chunk ke disk sementara (temp)**
   - `UploadUnitWriter` menyimpan chunk ke direktori temp: `temp/{userId}/{fileId}/chunk-{index}`
   - Setelah berhasil ditulis, chunk ditandai sebagai "diterima" di **in-memory tracker** (`InMemoryImp` / `ConcurrentHashMap`).

2. **Cek apakah semua chunk sudah diterima (upload selesai)**
   - Jika ya → masuk ke **flow completion** (lihat Step 3b).
   - Jika belum → cek apakah **satu batch** sudah siap.

3. **Cek batch siap dikirim ke storage**
   - Chunk dikelompokkan dalam batch berukuran **5 chunk**.
   - Contoh: chunk 0-4 = batch 0, chunk 5-9 = batch 1, dst.
   - Jika semua chunk dalam satu batch sudah ada → masuk ke **flow batch trigger** (lihat Step 3a).

#### Step 3a — Batch Dikirim ke Storage Node (gRPC)
- `UploadStorageService.sendBatch()` dipanggil.
- Setiap chunk file dibaca dari disk dan dipecah lagi menjadi slice **256 KB** (batas aman gRPC payload).
- Slice dikirim via gRPC client-streaming ke storage node.
- Storage node mengonfirmasi batch diterima.
- Chunk temp di disk **dihapus** (`IOCleaningService.cleanupBatch()`).
- Progress upload di database diperbarui.
- Retry otomatis hingga **3 kali** jika gRPC gagal (exponential backoff).

#### Step 3b — Semua Chunk Selesai (Completion)
- Batch-batch yang belum sempat terkirim di-flush terlebih dahulu.
- Sinyal finalisasi dikirim ke storage node: `sendFinalSignal()` via gRPC.
- Status `UploadSession` di database diubah menjadi `COMPLETED`.
- Semua file temp dihapus dari disk.
- Data in-memory tracker untuk file ini dibersihkan.

### Diagram Alur
```
Client                   Backend                    Storage Node
  |                         |                            |
  |-- POST /init ---------->|                            |
  |<-- fileId --------------|                            |
  |                         |                            |
  |-- POST chunk-0 -------->|-- tulis ke disk temp       |
  |-- POST chunk-1 -------->|-- tulis ke disk temp       |
  |-- POST chunk-2 -------->|-- tulis ke disk temp       |
  |-- POST chunk-3 -------->|-- tulis ke disk temp       |
  |-- POST chunk-4 -------->|-- batch-0 siap!            |
  |                         |--- gRPC sendBatch(0-4) --->|
  |                         |<-- OK ---------------------|
  |                         |-- hapus temp chunk 0-4     |
  |-- POST chunk-N -------->|-- semua chunk selesai!     |
  |                         |--- gRPC finalSignal ------>|
  |                         |<-- OK ---------------------|
  |                         |-- status = COMPLETED       |
```

---

## 2. DOWNLOAD FILE

### Gambaran Umum

Download menggunakan **HTTP streaming** — byte file dialirkan langsung dari storage node ke client melalui server, tanpa load seluruh file ke memory.

### Step by Step

#### Step 1 — Client Inisialisasi Download
```
POST /api/files/download/init
Body: { fileId }
```
- Server verifikasi apakah user punya akses ke file:
  - Jika file milik user sendiri → akses diizinkan.
  - Jika bukan → cek tabel `FileShared` apakah file di-share ke user ini.
  - Jika tidak ada akses → `403 Access Denied`.
- Server membuat record **`DownloadSession`** di database:
  - Status = `INIT`
  - `totalBytes` = ukuran file
  - `bytesSent` = 0
- Server mengembalikan `sessionId` dan info file.

#### Step 2 — Client Memulai Streaming
```
GET /api/files/download/{fileId}/stream
```
- Server validasi akses file sekali lagi.
- Status `DownloadSession` diubah menjadi `STREAMING`.
- Server memanggil `downloadStorageService.downloadFile(userId, fileId)`.

**Di dalam `DownloadStorageImp.downloadFile()`:**
- Permintaan download dikirim ke storage node via **gRPC server-streaming**.
- Storage node mengirimkan data sebagai stream chunk (`DownloadResponse`).
- Setiap chunk yang datang dari gRPC dikonversi menjadi `FileChunk`.

**Kembali di `DownloadServiceImp.streamFile()`:**
- Setiap chunk yang diterima:
  1. Cek apakah download di-cancel oleh user (via status `DownloadSession` di DB).
  2. Update `bytesSent` di `DownloadSession`.
  3. Simpan update ke database.
  4. Kirim data `byte[]` ke client.
- Saat selesai: status `DownloadSession` diubah ke `COMPLETED`.
- Jika error: status diubah ke `FAILED`.

#### Step 3 — Client Menerima File
- Client menerima header:
  - `Content-Disposition: attachment; filename="namafile.ext"`
  - `Content-Length: <ukuran file>`
  - `Content-Type: application/octet-stream`
- Data mengalir sebagai byte stream hingga selesai.

#### Fitur Tambahan: Cancel Download
```
POST /api/files/download/{fileId}/cancel
```
- Mengubah status `DownloadSession` menjadi `CANCELED`.
- Streaming yang sedang berjalan akan berhenti secara otomatis di chunk berikutnya.

#### Fitur Tambahan: Cek Status Download
```
GET /api/files/download/{fileId}/status
```
- Mengembalikan progress: `bytesSent / totalBytes` dalam bentuk persentase (0.0 - 1.0).

### Diagram Alur
```
Client                   Backend                    Storage Node
  |                         |                            |
  |-- POST /init ---------->|                            |
  |<-- sessionId -----------|                            |
  |                         |                            |
  |-- GET /stream --------->|                            |
  |                         |--- gRPC downloadFile ----->|
  |                         |<-- chunk stream -----------|
  |<-- bytes chunk-0 -------|                            |
  |<-- bytes chunk-1 -------|                            |
  |<-- bytes chunk-N -------|                            |
  |   (file selesai)        |-- status = COMPLETED       |
```

---

## 3. AI SUMMARY PDF

### Gambaran Umum

Fitur ini memungkinkan user meminta **ringkasan otomatis** dari file PDF yang sudah tersimpan di storage, menggunakan **Google Gemini AI** via Spring AI.

### Step by Step

#### Step 1 — Client Meminta Summary
```
POST /api/ai/summary/pdf/{fileId}
```
- `fileId` adalah UUID file PDF yang sudah diupload sebelumnya.
- User harus sudah login (JWT required).

#### Step 2 — Download PDF dari Storage (Internal)
- `PdfService.extractFile(fileId)` dipanggil.
- File PDF **didownload dari storage node** via gRPC (sama seperti proses download biasa).
- Data byte dikumpulkan dan ditulis ke **file temp lokal** (`/tmp/ocr-XXXX.pdf`).
- Proses ini dijalankan di `pdfScheduler` (thread pool terpisah) karena bersifat blocking I/O.

#### Step 3 — Ekstraksi Teks dari PDF
- File temp dibuka menggunakan **Apache PDFBox**.
- `PDFTextStripper.getText()` mengekstrak seluruh teks dari PDF.
- File temp **langsung dihapus** setelah ekstraksi selesai (via `doFinally`).

#### Step 4 — Teks Dikirim ke Gemini AI
- Teks hasil ekstraksi dibungkus dalam prompt:
  ```
  "Tolong rangkum teks berikut secara singkat dan jelas dalam Bahasa Indonesia:\n\n{teks_pdf}"
  ```
- Prompt dikirim ke **Google Gemini** via `ChatClient` (Spring AI).
- Request dijalankan di `aiScheduler` (thread pool terpisah) karena memanggil API eksternal.
- Gemini mengembalikan hasil ringkasan sebagai string.

#### Step 5 — Hasil Dikembalikan ke Client
```json
{
  "result": "Ringkasan isi dokumen: ..."
}
```
- Jika Gemini gagal → mengembalikan pesan error: `"Gagal menghasilkan ringkasan karena masalah teknis."`

### Diagram Alur
```
Client                   Backend                Storage Node        Gemini AI
  |                         |                       |                   |
  |-- POST /summary/pdf --->|                       |                   |
  |      /{fileId}          |                       |                   |
  |                         |--- gRPC download ---->|                   |
  |                         |<-- byte stream -------|                   |
  |                         |-- tulis ke temp file  |                   |
  |                         |-- ekstrak teks (PDFBox)                   |
  |                         |-- hapus temp file     |                   |
  |                         |--- kirim prompt ------------------>|       |
  |                         |<-- ringkasan ----------------------|       |
  |<-- { result: "..." } ---|                       |                   |
```

### Catatan
- Fitur ini hanya bekerja untuk file **PDF** yang sudah diupload ke sistem.
- Kualitas ringkasan tergantung pada kualitas teks di dalam PDF (PDF scan/gambar tidak bisa diekstrak teksnya).
- Untuk summary teks biasa (bukan PDF):
  ```
  POST /api/ai/summary
  Body: { "teks": "teks yang ingin dirangkum" }
  ```

---

## Ringkasan Komponen Utama

| Komponen | Peran |
|---|---|
| `UploadController` | Menerima request upload dari client |
| `UploadCoordinator` | Mengatur alur chunk → batch → storage |
| `InMemoryImp` | Tracker chunk yang sudah diterima (ConcurrentHashMap) |
| `UploadUnitWriter` | Menulis chunk ke disk temp |
| `UploadStorageService` | Mengirim batch ke storage node via gRPC |
| `DownloadController` | Menerima request download, streaming ke client |
| `DownloadService` | Koordinasi sesi download dan akses kontrol |
| `DownloadStorageService` | Menerima stream dari storage node via gRPC |
| `PdfService` | Download + ekstrak teks dari PDF |
| `GeminiService` | Mengirim prompt ke Gemini dan mengembalikan ringkasan |
