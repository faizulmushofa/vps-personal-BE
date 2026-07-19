# Panduan Bootstrap & Setup Awal VPS Horizon (Debian)

Panduan ini berisi langkah-langkah untuk menyiapkan VPS baru Anda (`horizon` / `your-vps-ip-or-domain`) dari kondisi kosong (fresh install Debian) hingga siap menerima deployment otomatis dari GitHub Actions CD.

---

## Langkah 1: Update Sistem & Install Dependensi Awal
Hubungi VPS Anda via SSH dari Mac, lalu jalankan:
```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y curl git gnupg rsync
```

---

## Langkah 2: Install Docker Engine & Docker Compose
Debian membutuhkan repositori resmi Docker agar mendapatkan versi Docker terbaru. Jalankan perintah ini satu per satu:

1.  **Tambahkan GPG Key resmi Docker:**
    ```bash
    sudo install -m 0755 -d /etc/apt/keyrings
    sudo curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc
    sudo chmod a+r /etc/apt/keyrings/docker.asc
    ```
2.  **Tambahkan repositori ke sumber APT:**
    ```bash
    echo \
      "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian \
      $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
      sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
    ```
3.  **Install Docker Package:**
    ```bash
    sudo apt update
    sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
    ```

---

## Langkah 3: Berikan Akses Docker ke User `horizon`
Secara default, Docker memerlukan akses `sudo`. Agar GitHub Actions (yang masuk lewat user `horizon`) bisa menjalankan `docker compose` tanpa error *permission denied*, tambahkan user `horizon` ke group `docker`:

```bash
sudo usermod -aG docker horizon
```
> [!IMPORTANT]
> Setelah menjalankan perintah di atas, Anda **wajib log out** dari SSH VPS dan masuk kembali agar perubahan group user dapat diterapkan oleh sistem.
> Untuk menguji apakah berhasil tanpa `sudo`, jalankan: `docker ps`.

---

## Langkah 4: Hubungkan & Konfigurasi SSH Key untuk GitHub
Kita perlu mengizinkan GitHub Actions masuk ke VPS tanpa password menggunakan SSH Key.

1.  **Di terminal Mac Anda, buat key pair baru:**
    ```bash
    ssh-keygen -t ed25519 -f ~/.ssh/vps_horizon_deploy -C "github-actions-vps"
    # Tekan enter-enter saja (kosongkan passphrase)
    ```
2.  **Salin Public Key baru tersebut ke VPS:**
    ```bash
    ssh-copy-id -i ~/.ssh/vps_horizon_deploy.pub horizon@your-vps-ip-or-domain
    ```
    *(Masukkan password user `horizon` sekali untuk menyalin).*
3.  **Dapatkan Kunci Privat untuk GitHub Secrets:**
    Tampilkan isi file kunci privat di Mac Anda:
    ```bash
    cat ~/.ssh/vps_horizon_deploy
    ```
    *Salin seluruh teks outputnya (termasuk baris BEGIN dan END) dan masukkan ke GitHub Repository Secrets dengan nama `SSH_PRIVATE_KEY`.*

---

## Langkah 5: Kloning Repositori & Buat Network di VPS
Masuk kembali ke VPS via SSH, lalu siapkan direktori proyek:

1.  **Buat Docker Network Bersama:**
    ```bash
    docker network create horizon-network
    ```
2.  **Kloning Proyek Backend ke `/home/horizon`:**
    ```bash
    cd /home/horizon
    git clone -b dev https://github.com/faizulmushofa/vps-personal-BE.git vps-personal-backend
    ```
3.  **Siapkan file `.env` Produksi:**
    ```bash
    cd /home/horizon/vps-personal-backend
    cp .env.example .env
    nano .env
    ```
    *Masukkan semua detail konfigurasi riil Anda (Database URL, API key, gRPC port, dll).*

---

## Langkah 6: Kloning Proyek Storage Node & Konfigurasinya
Sama seperti backend, mari siapkan storage-node:

1.  **Kloning Proyek Storage Node:**
    ```bash
    cd /home/horizon
    git clone https://github.com/faizulmushofa/storage-node-grpc.git storage-node
    ```
2.  **Siapkan file `.env` Storage Node:**
    ```bash
    cd /home/horizon/storage-node
    cp .env.example .env
    nano .env
    ```
    *Sesuaikan path folder data storage Anda (misal ke folder `/home/horizon/storage-data/data` dan `/home/horizon/storage-data/certs` yang sudah Anda transfer).*
3.  **Jalankan Storage Node pertama kali:**
    ```bash
    docker compose up -d
    ```

---

Setelah semua langkah di atas selesai, VPS Anda sudah siap 100% untuk menerima deployment otomatis dari GitHub Actions. Anda bisa langsung mencoba fitur merge Pull Request Anda!
