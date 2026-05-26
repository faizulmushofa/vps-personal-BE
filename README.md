# VPS Personal Backend

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![gRPC](https://img.shields.io/badge/gRPC-1.77.1-blue.svg)](https://grpc.io/)
[![Database](https://img.shields.io/badge/R2DBC-PostgreSQL-blue.svg)](https://r2dbc.io/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

---

## 📋 [PROJECT INFO]
* **Project Name**: `vps-personal-backend`
* **One Line Description**: Sebuah backend storage pribadi terdistribusi yang sepenuhnya asinkron dan reaktif (Reactive Stack), menggunakan Spring Boot WebFlux dan gRPC untuk transfer file chunk berkinerja tinggi.
* **Problem Statement**: Cloud storage tradisional seringkali menggunakan arsitektur monolitik yang boros RAM/resource saat mengunggah atau mengunduh file berukuran raksasa. Selain itu, menyatukan logika bisnis API dengan penyimpanan fisik membatasi skalabilitas sistem penyimpanan.
* **Target Users**: Pengguna mandiri (Self-hosters) yang menginginkan sistem penyimpanan pribadi yang aman, ringan, dan cepat, serta developer yang membutuhkan fondasi arsitektur microservices/distributed storage berbasis gRPC.

---

## ⚡ [FEATURES]
* 🔄 **Reactive WebFlux Stack**: Seluruh aliran data non-blocking menggunakan Project Reactor (`Flux`/`Mono`) untuk menangani konkurensi tinggi.
* 📦 **Chunked File Streaming**: Proses unggah dan unduh file besar dilakukan dengan membagi file menjadi potongan kecil (chunks) sehingga konsumsi RAM di server tetap stabil dan minimal.
* 🔌 **Decoupled gRPC Storage**: Pemisahan layer logika (API) dengan penyimpanan aktual. Core API bertindak sebagai orchestrator dan berkomunikasi dengan gRPC storage node.
* 🔐 **Robust Security (JWT & RBAC)**: Autentikasi aman menggunakan access token di header dan refresh token dalam HTTP-Only Cookie. Mendukung Role-Based Access Control (Admin & User).
* 📂 **File Sharing System**: Kemudahan membagikan file tertentu ke pengguna lain secara aman melalui pencocokan email.
* 🧠 **Smart AI Summarizer (Gemini)**: Integrasi dengan Google Gemini AI untuk merangkum teks umum maupun mengekstrak teks langsung dari file PDF (menggunakan Apache PDFBox) lalu meringkasnya secara otomatis.
* 🖥️ **Built-in SPA Client**: Halaman web single-page application sederhana (HTML, CSS, JS) bawaan di dalam folder static resources untuk pengujian instan.

---

## 💻 [TECH STACK]
* **Backend**: Java 21, Spring Boot 4.0.6, Spring Security, Spring WebFlux, Spring gRPC Client
* **Frontend**: Simple SPA (Vanilla HTML5, Tailwind CSS, JavaScript) di `/static`
* **Database**: PostgreSQL (akses reaktif)
* **ORM / Database Driver**: Spring Data R2DBC (Reactive Relational Database Connectivity) & `r2dbc-postgresql`
* **Auth Method**: JWT (JSON Web Tokens) dengan Access Token & Refresh Token (HTTP-Only Cookie)
* **External Services**: Google Gemini AI (via Spring AI Google GenAI), Apache PDFBox 3.0.5 (PDF text extractor)

---

## 🏗️ [ARCHITECTURE]

### System Overview
Aplikasi ini berjalan secara asinkron dengan pola **Reactive API Gateway / Orchestrator**. 

```
                                    ┌────────────────────────┐
                                    │      Client App        │
                                    │  (Browser / SPA / Mobile)│
                                    └───────────┬────────────┘
                                                │ REST API
                                                ▼
                                    ┌────────────────────────┐
                                    │ vps-personal-backend   │
                                    │  (Spring Boot WebFlux) │
                                    └─────┬────────────┬─────┘
                                          │            │
                    gRPC (Secure/TLS)     │            │ R2DBC (Reactive)
                                          ▼            ▼
                               ┌──────────────┐   ┌──────────────┐
                               │ Storage Node │   │  PostgreSQL  │
                               │ (gRPC Server)│   │   Database   │
                               └──────────────┘   └──────────────┘
```

### Main Modules
1. **`Ai`**: Menampung `AiController` dan `GeminiService` untuk pemrosesan AI generatif.
2. **`File`**: Modul utama manajemen file, dibagi menjadi sub-layanan:
   * `core`: Menyimpan data dasar metadata file.
   * `upload`: Sesi unggah chunks file dan asinkron manajemen buffer.
   * `download`: Pengunduhan chunk-by-chunk dan manajemen session.
   * `pdf`: Layanan pengekstrak teks dari PDF menggunakan PDFBox.
   * `share`: Logika pembagian file antar akun user.
3. **`Storage`**: Komponen client gRPC yang berkomunikasi langsung dengan Storage Node biner.
4. **`UploadUnit`**: `UploadCoordinator` untuk sinkronisasi antrean potongan file (chunks) agar tersusun rapi saat ditransfer ke storage node.
5. **`User`**: Manajemen data user, kuota penyimpanan, registrasi, dan modul admin.
6. **`security` / `infra`**: Konfigurasi keamanan Spring Security, filter JWT, dan pengisi data awal database (database seeder).

### Data Flow Summary
1. **Pendaftaran & Login**: User mendaftar/login via `/api/auth`. Server memvalidasi dan mengirimkan Access Token (JSON body) serta Refresh Token (HTTP-Only Cookie).
2. **Unggah File (Upload)**:
   * Client memulai sesi upload lewat `/api/files/init` mendapatkan `uploadSessionId`.
   * Client mengirimkan chunks lewat `/api/files/{id}/chunks/{index}` secara paralel/berurutan.
   * `UploadCoordinator` menerima chunk secara reaktif, lalu membukakan gRPC channel stream ke Storage Node untuk menyimpan potongan file fisik tersebut.
   * Setelah seluruh chunk masuk, status sesi diubah menjadi `COMPLETED` dan metadata tersimpan di PostgreSQL.
3. **Unduh File (Download)**:
   * Client meminta link unduh. Server memverifikasi kepemilikan lalu membuka koneksi gRPC Server-Streaming ke Storage Node.
   * Chunks dikirimkan dari Storage Node ke Backend, lalu dilanjutkan langsung (piped) ke response output HTTP client secara reaktif.

---

## ⚙️ [INSTALLATION]

### 1. Clone Repo
```bash
git clone https://github.com/faizulmushofa/vps-personal-BE.git
cd vps-personal-backend
```

### 2. Install Dependencies
Pastikan Java 21 sudah terinstall, lalu compile project untuk mendownload library dan men-generate class dari Protocol Buffer (gRPC):
```bash
./mvnw clean compile
```

### 3. Env Setup
Buat file environment variable atau pasang di sistem operasi Anda sebelum menjalankan aplikasi (lihat bagian [ENV VARIABLES]).

### 4. Database Setup
Pastikan PostgreSQL Anda sudah aktif dan buat database kosong (misalnya bernama `vps_db`).
> ⚠️ **Catatan Penting**: Anda **TIDAK PERLU** mengimport file `.sql` apa pun secara manual! `DatabaseSeeder.java` di dalam aplikasi akan mendeteksi database kosong, membuat seluruh tabel yang dibutuhkan secara otomatis, dan melakukan seeding data awal saat pertama kali aplikasi dijalankan.

### 5. Run Dev Command
Jalankan aplikasi dengan Maven:
```bash
./mvnw spring-boot:run
```

---

## 🔑 [ENV VARIABLES]

Aplikasi membutuhkan environment variables berikut untuk dapat berjalan dengan baik:

| Nama Variabel | Penjelasan Singkat | Contoh Nilai |
| :--- | :--- | :--- |
| `DATABASE_URL` | Koneksi reaktif URL ke PostgreSQL database | `r2dbc:postgresql://localhost:5432/vps_db` |
| `DATABASE_USERNAME` | Username database PostgreSQL | `postgres` |
| `DATABASE_PASSWORD` | Password database PostgreSQL | `secret_password` |
| `SECRET_KEY` | Key rahasia minimum 256-bit untuk enkripsi JWT | `3c90a3f7892bcf4011ef9bc... (min 32 karakter)` |
| `STORAGE_NODE_URL` | Alamat host gRPC Storage Node fisik | `localhost:9090` |
| `SERVICE_TOKEN` | Token otorisasi gRPC antar backend-service | `my-secure-grpc-token-123` |
| `SPRING_AI_GOOGLE_GENAI_API_KEY` | API Key untuk mengakses Google Gemini AI | `AIzaSyD_ExampleKey12345` |

---

## 🏃 [RUN COMMANDS]

* **Menjalankan Mode Development**:
  ```bash
  ./mvnw spring-boot:run
  ```
* **Membangun Artifact / Build**:
  ```bash
  ./mvnw clean package -DskipTests
  ```
* **Menjalankan Artifact Hasil Build**:
  ```bash
  java -jar target/faizul-0.0.1-SNAPSHOT.jar
  ```
* **Menjalankan Unit/Integration Test**:
  ```bash
  ./mvnw test
  ```

---

## 🌐 [API]

* **Base URL**: `http://localhost:8080/api`
* **Auth Method**: `Authorization: Bearer <JWT_ACCESS_TOKEN>` & Cookie `refreshToken` (HTTP-Only)

### Main Endpoints Summary

#### 🔑 Authentications (`/api/auth`)
* `POST /api/auth/register` : Mendaftarkan user baru.
* `POST /api/auth/login` : Login user, mendapatkan JWT access token & meletakkan refresh token di cookie.
* `POST /api/auth/refresh` : Memperbarui access token menggunakan refresh token di cookie.

#### 📂 File & Quota Management (`/api/files`)
* `GET /api/files` : Mengambil daftar seluruh file milik user saat ini.
* `GET /api/files/{id}` : Melihat detail spesifik metadata file tertentu.
* `DELETE /api/files/{id}` : Menghapus metadata file dan menginstruksikan penghapusan di Storage Node.
* `GET /api/files/me/storage` : Mendapatkan status penyimpanan & sisa kuota user saat ini.
* `PUT /api/files/users/{id}/quota` `[ADMIN]` : Memperbarui batas kuota storage untuk user tertentu.

#### 📤 Chunks Upload Service (`/api/files`)
* `POST /api/files/init` : Memulai sesi upload baru (mengirim nama file & total ukuran).
* `POST /api/files/{id}/chunks/{index}` : Mengunggah potongan file (chunk) ke-`index` menggunakan Multipart FilePart.

#### 📥 Chunks Download Service (`/api/files/download`)
* `POST /api/files/download/init` : Menginisialisasi sesi unduh file.
* `GET /api/files/download/{fileId}/stream` : Mengambil data biner biner via stream (Reactive Octet-Stream).
* `GET /api/files/download/{fileId}/status` : Cek status pengunduhan.
* `POST /api/files/download/{fileId}/cancel` : Membatalkan proses unduhan berjalan.

#### 👥 File Sharing (`/api/files/share`)
* `POST /api/files/share/{fileId}` : Membagikan akses file kepada pengguna lain via alamat email.
* `DELETE /api/files/share/{fileId}/{userId}` : Mencabut akses berbagi file dari pengguna tertentu.
* `GET /api/files/share/shared-with-me` : Melihat file-file orang lain yang dibagikan kepada saya.

#### 👥 User Admin Management (`/api/users`)
* `GET /api/users` `[ADMIN]` : Melihat semua user yang terdaftar di sistem.
* `POST /api/users` `[ADMIN]` : Membuat akun user baru.
* `DELETE /api/users/{id}` `[ADMIN]` : Menghapus akun user tertentu.

#### 🧠 Gemini AI Services (`/api/ai`)
* `POST /api/ai/summary` : Mengirim prompt teks umum untuk diringkas/dijawab oleh Gemini AI.
* `POST /api/ai/summary/pdf/{fileId}` : Mengekstrak isi file PDF milik user, lalu meringkas isinya secara otomatis lewat Gemini AI.

---

## 📁 [PROJECT STRUCTURE]

Berikut adalah struktur direktori utama pada proyek `vps-personal-backend`:

```
vps-personal-backend/
├── pom.xml                                   # File Maven dependencies dan konfigurasi build
├── src/
│   └── main/
│       ├── java/
│       │   └── io/
│       │       └── github/
│       │           └── faizul/
│       │               ├── Ai/               # Modul integrasi AI Gemini (Controller, Service)
│       │               ├── Exception/        # Global Exception Handler reaktif
│       │               ├── File/             # Inti modul Manajemen File & Sharing
│       │               │   ├── core/         # Metadata files
│       │               │   ├── download/     # Sesi pengunduhan
│       │               │   ├── mapper/       # DTO mapping
│       │               │   ├── pdf/          # PDF text extractor (PDFBox)
│       │               │   ├── share/        # Berbagi file ke pengguna lain
│       │               │   └── upload/       # Sesi pengunggahan
│       │               ├── Storage/          # Layanan gRPC Client ke Storage Node
│       │               ├── UploadUnit/       # UploadCoordinator sinkronisasi chunk
│       │               ├── User/             # Entitas User & Kontrol Quota Storage
│       │               ├── infra/            # Konfigurasi Security & Database Seeder
│       │               └── security/         # JWT filter, autentikasi, & Role entities
│       └── resources/
│           ├── application.yaml              # Konfigurasi runtime Spring Boot
│           └── static/                       # Client web bawaan (SPA UI)
│               ├── app.js
│               ├── index.html
│               └── styles.css
```

---

## 📝 [NOTES / DECISIONS]

* **Dynamic Database Seeding**: Schema database PostgreSQL didesain auto-create di runtime lewat `DatabaseSeeder.java`. Hal ini meminimalisir setup manual bagi developer baru.
* **Fully Reactive Stack**: Penggunaan WebFlux dan R2DBC memastikan performa throughput data yang sangat besar dan efisiensi memori yang tangguh karena tidak memblock thread I/O.
* **gRPC Plaintext vs TLS**: Konfigurasi gRPC client di `application.yaml` mendukung opsi TLS. Dalam local development, parameter `secure: false` diset untuk mempermudah testing tanpa ribet setup certificate local.

---

## 🗺️ [ROADMAP]
- [ ] Dukungan penyimpanan multi-cloud (S3 / Object Storage integration).
- [ ] Enkripsi data file di sisi server (Encryption at Rest) agar data fisik di Storage Node aman.
- [ ] Push Notification unggahan/unduhan selesai menggunakan Server-Sent Events (SSE) reaktif.

---

## 📄 [LICENSE]
Didistribusikan di bawah lisensi **MIT**. Silakan gunakan, modifikasi, dan bagikan secara bebas sesuai kebutuhan Anda!
