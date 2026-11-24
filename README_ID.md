# KlipHP Android — panduan Bahasa Indonesia

Proyek Android Kotlin/Jetpack Compose untuk target Infinix Smart 9 (Android 14).

## Isi versi awal (MVP)
- Pilih video dari galeri menggunakan Android Photo Picker / document picker.
- Tema gelap hitam-merah.
- Durasi klip 15, 30, 60 detik.
- Analisis visual lokal sederhana untuk memilih rentang dengan perubahan gambar paling tinggi.
- Export MP4 H.264/AAC dengan Media3 Transformer, tinggi 720p atau 1080p.
- Transkripsi bahasa Indonesia melalui endpoint OpenAI Audio Transcriptions.
- GitHub Actions membangun APK debug dan menyediakannya sebagai Artifact.

## Penting: batas versi awal
1. Tombol Auto Clip saat ini memakai heuristik perubahan visual (scene-change score), bukan model AI yang memahami isi video atau mendeteksi momen viral. Anggap sebagai Auto Highlight beta.
2. Subtitle saat ini menghasilkan teks transkripsi, belum membuat subtitle bertimestamp/SRT dan belum membakar teks ke MP4.
3. API key dimasukkan pengguna di aplikasi dan hanya berada di memori selama aplikasi terbuka. Jangan membagikan APK dengan API key tertanam.
4. Integrasi saat ini memakai `whisper-1`. Dokumentasi OpenAI menyatakan model ini deprecated dan dijadwalkan dihentikan 26 Februari 2027. Sebelum tanggal itu, ganti model ke model transkripsi yang direkomendasikan saat itu (`gpt-transcribe`) dan sesuaikan format respons.
5. API transkripsi membatasi unggahan file hingga 25 MB. Video dikirim ke layanan OpenAI. Jangan gunakan untuk video pribadi/sensitif tanpa memahami implikasi privasi dan biaya API.
6. Ekspor menyimpan file di folder app-specific `Android/data/com.kliphp.app/files/`. Versi berikutnya perlu menambahkan penyimpanan MediaStore agar hasil langsung tampil di Galeri.

## Persyaratan build
- JDK 17
- Gradle 8.11.1
- Android Gradle Plugin 8.9.2
- Android SDK Platform 35
- Android device Android 8.0+ (minSdk 26)

## Cara membuat repository GitHub dari HP
1. Buka https://github.com lalu masuk/daftar.
2. Tekan tombol `+` > `New repository`.
3. Isi nama: `KlipHP`.
4. Pilih `Public` atau `Private`, centang `Add a README file` boleh tidak dicentang.
5. Buat repository.
6. Cara paling mudah mengunggah proyek dari HP: unduh ZIP proyek ini, ekstrak dengan aplikasi pengelola file, buka repository di browser, pilih `Add file` > `Upload files`.
7. Unggah seluruh ISI folder `KlipHP` (bukan folder pembungkusnya) ke root repository.
8. Tekan `Commit changes` ke branch `main`.

## Build APK lewat GitHub Actions
1. Buka repository `KlipHP`.
2. Pilih tab `Actions`.
3. Jika diminta, tekan `I understand my workflows, go ahead and enable them`.
4. Pilih workflow `Build KlipHP APK`.
5. Tekan `Run workflow` > `Run workflow`.
6. Tunggu sampai job selesai dengan tanda centang hijau.
7. Buka hasil run, gulir ke bagian `Artifacts`.
8. Unduh `KlipHP-debug-apk` lalu ekstrak ZIP-nya.
9. Di Android, buka `app-debug.apk`, izinkan pemasangan dari sumber tersebut jika diminta, lalu instal.

## Build otomatis setiap commit
Setiap perubahan yang di-commit ke branch `main` akan menjalankan workflow dan membuat artifact APK baru.

## Catatan untuk pengujian
- Mulai dengan video pendek 20–60 detik.
- Uji export 720p lebih dahulu karena Infinix Smart 9 memiliki sumber daya terbatas.
- 1080p membutuhkan waktu dan ruang lebih besar; hasil tergantung resolusi video sumber dan codec perangkat.
- Jangan menutup aplikasi saat proses export berjalan pada MVP ini.
- Jika API mengembalikan 401, periksa API key. Jika 429, periksa kuota/billing API. Jika 413/ukuran besar, gunakan video di bawah 25 MB.

## Struktur
- `app/src/main/java/com/kliphp/app/MainActivity.kt` — UI, pemilihan video, analisis visual, ekspor, transkripsi.
- `.github/workflows/android.yml` — workflow build APK.
