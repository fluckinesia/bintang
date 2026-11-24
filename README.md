# STAR ASN Browser — Android saja

Proyek Android WebView minimal untuk pengujian yang diizinkan.

- Membuka: https://star-asn.kemenimipas.go.id/authentication/login
- Lokasi geolocation browser terpasang permanen: -6.2212607, 106.8321831
- Nilai 153m di Google Maps adalah tinggi tampilan peta; **bukan** GPS altitude.
- Koordinat ini hanya untuk simulasi API lokasi dalam WebView. Tidak mengubah GPS Android maupun menjamin penerimaan lokasi oleh situs.
- Versi: 1.2.0 (debug APK untuk uji coba, bukan rilis aplikasi resmi).

## Upload & build di GitHub

1. Buat repository baru, misalnya `star-asn-browser`, pilih **Private** dan jangan centang README/.gitignore/license.
2. Download ZIP proyek ini dan pilih Extract All / Ekstrak Semua.
3. Masuk ke repository baru > **uploading an existing file**.
4. **Seret isi folder hasil ekstrak**: `android/`, `.github/`, dan `README.md` — jangan seret file ZIP.
5. Commit changes.
6. Buka tab Actions, pilih Build Android APK, klik Run workflow (atau workflow mulai otomatis karena perubahan pada `android/`).
7. Setelah sukses buka run > Artifacts > unduh `FixedLocationBrowser-debug-apk`. Ekstrak untuk memperoleh `.apk`.

## Catatan

Jika upload melalui web tidak memasukkan `.github/workflows/build-android-apk.yml`, buat file tersebut di web UI sesuai path yang sama dan salin isi file YAML dari folder ekstrak. Source `android` tetap harus berada di root repo.
