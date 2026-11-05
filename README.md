# DompetKu Pro v2 — Keuangan Pribadi & Bisnis

Aplikasi Android pencatat keuangan dengan **4 tab**, **ruang Pribadi & Bisnis terpisah**,
**auto-capture transaksi dari SMS bank/e-wallet**, **sinkronisasi online ke server milik sendiri**,
dan **ekspor laporan CSV untuk pembukuan**.

## Fitur

### 💰 Pencatatan
- Pemasukan / Pengeluaran / **Transfer antar akun** (kategori & emoji per ruang)
- Multi-akun: Kas, Bank, E-Wallet — dengan **saldo per akun** yang dihitung otomatis
- Akun bank/e-wallet **dibuat otomatis** saat SMS pertamanya tertangkap
- Transfer tidak mengubah total kekayaan, hanya memindahkan saldo antar akun

### 🤖 Koneksi Platform Bank / E-Wallet (3 metode)
1. **SMS Auto-Capture** (aktif secara offline) — parser memindai SMS dari BCA, Mandiri, BRI, BNI,
   CIMB Niaga, SeaBank, Jenius, BSI, GoPay, OVO, DANA, ShopeePay, LinkAja.
   Nominal + arah (keluar/masuk) dikenali; duplikat dilewati; bisa pindai balik inbox lama.
2. **Import mutasi CSV / rekening koran** — parser toleran pemisah `;`/`,` dan format tanggal;
   pratinjau sebelum import; opsi interpretasi angka minus.
3. **API resmi bank** — bank tidak membuka mutasi rekening via API publik; jalur resminya lewat
   agregator (Brick, Ayoconnect, dll.) dengan kredensial bisnis. Kerangka konektornya
   (modul sync + DTO) siap disambungkan.

### 🌐 Sinkronisasi Online (server sendiri)
- Server Ktor (Kotlin) + SQLite, satu "ruang data" per token; bagikan token yang sama ke banyak
  perangkat (satu orang atau seluruh tim perusahaan).
- Last-write-wins berdasarkan `updatedAt`; sinkron berkala (6 jam) via WorkManager saat online,
  plus tombol "Sinkron Sekarang".
- Bisa jalan di localhost, VPS, atau Docker.

### 📊 Perusahaan / Pembukuan
- Ruang **Bisnis** dengan kategori usaha (Gaji Karyawan, Sewa, Operasional, Marketing, Pajak,
  Penjualan, Jasa, Investasi)
- Laporan **Laba/Rugi** bulanan + rincian pemasukan & pengeluaran per kategori
- **Ekspor CSV** sebulan penuh (tanggal, jenis, kategori, keterangan, akun, jumlah) via berbagi

## Struktur

```
DompetKu/
├── app/src/main/java/com/dompetku/
│   ├── MainActivity.kt
│   ├── data/        Transaction · Account · Category · Dao · Room v2 · Seed
│   ├── sms/         BankSmsParser · SmsReceiver (capture realtime) · SmsInbox (pindai balik)
│   ├── sync/        DTO · SyncManager (push/pull LWW) · SyncWorker (periodik)
│   ├── util/        Csv (ekspor & parse toleran)
│   └── ui/          HomeScreen (4 tab) · DompetViewModel · TransactionForm
│                    ReportTab · AccountsTab · SettingsTab · LinkScreen · ImportScreen
├── server/          Ktor + SQLite — /api/v1/ledger · /api/v1/sync · Dockerfile
└── README.md
```

## Build

```bash
gradle :app:assembleDebug            # app → app/build/outputs/apk/debug/app-debug.apk
gradle :server:buildFatJar           # server → server/build/libs/server-all.jar
java -jar server-all.jar             # server jalan di port 8080 (PORT=... untuk ganti)
docker build -t dompetku-server .    # atau deploy via Docker
```

> Di mesin RAM ≤ 6 GB pakai: `gradle --no-daemon -Dkotlin.compiler.execution.strategy=in-process`

## Pakai sinkronisasi

1. Jalankan server (di PC: `java -jar server-all.jar`; emulator Android melihat PC di
   `http://10.0.2.2:8080`; antar perangkat: IP LAN/VPS).
2. App → tab **Pengaturan** → masukkan URL → **Buat Ruang Baru** (dapat token) atau
   **Simpan Token** (gabung ke ruang yang ada — caranya multi-perangkat/tim).
3. **Sinkron Sekarang**, atau biarkan jalan otomatis tiap 6 jam saat online.

## Catatan penting (jujur soal batasan)

- **API langsung bank tidak tersedia publik.** Auto-capture SMS adalah metode offline paling
  umum yang dipakai aplikasi serupa; import CSV menutup kebutuhan akuntansi penuh.
  Untuk koneksi mutasi real-time resmi, mendaftar ke agregator (Brick/Ayoconnect) —
  modul sync sudah siap sebagai landasan.
- HTTP polos (`http://…`) hanya disarankan untuk server lokal/LAN sendiri; untuk VPS publik,
  letakkan di belakang HTTPS (mis. Caddy/nginx) agar token & data tidak melintas plaintext.
- Parser SMS berbasis pola; SMS di luar 13 platform yang dikenal atau tanpa pola jelas diabaikan.
- Izin SMS/READ_SMS hanya dipakai untuk fitur pencatatan ini; tanpa sinkronisasi, data tidak
  pernah keluar dari perangkat.
