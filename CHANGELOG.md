# Changelog

Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), versi mengikuti [SemVer](https://semver.org/).

## [1.0.0] - 2026-10-05

### Added
- Pemindaian kamera (CameraX) dengan deteksi tepi dokumen real-time (OpenCV) dan potret otomatis saat dokumen stabil ±1 detik.
- Mode batch: "Pindai lagi" menambah halaman ke dokumen yang sama.
- Impor dari galeri lewat Photo Picker (tanpa izin penyimpanan), dengan batas 40 MB dan validasi gambar.
- Editor potong 4 sudut dengan lensa pembesar, deteksi ulang otomatis, dan opsi halaman penuh.
- Koreksi perspektif dan filter: Ajaib (hapus bayangan), Asli, Abu-abu (CLAHE), Hitam putih (adaptive threshold), Cerahkan; putar 90°.
- Manajemen dokumen (Room): daftar, cari judul **dan isi teks OCR**, ganti nama, hapus, urutkan/hapus halaman.
- Ekspor PDF (A4 / Letter / sesuai gambar), bagikan PDF atau JPG, simpan PDF ke lokasi pilihan (SAF).
- OCR offline (ML Kit, model bundled) per halaman atau seluruh dokumen; salin/bagikan teks.
- Pengaturan: tema Sistem/Terang/Gelap, potret otomatis, filter bawaan, ukuran halaman PDF.
- Privasi: tanpa izin INTERNET, data dikecualikan dari backup cloud/transfer perangkat, FileProvider terbatas ke folder ekspor.
- Unit test untuk geometri, pelacak stabilitas, sanitasi nama file, ukuran gambar, dan tata letak PDF; CI GitHub Actions.
