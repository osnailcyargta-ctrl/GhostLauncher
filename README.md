# Ghost Launcher

Launcher Android ringan, hemat baterai, dengan dockbar 5 slot, dukungan folder, instalasi pintasan web, dan satu fitur rahasia.

## Spesifikasi

| Item | Detail |
|------|--------|
| Application ID | `com.ghost.launcher` |
| Version | 1.0.0 |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 34 (Android 14) |
| Ukuran APK | ~1.8 MB (release, R8 minified) |
| Permissions | `QUERY_ALL_PACKAGES`, `INSTALL_SHORTCUT`, `SET_WALLPAPER`, `WAKE_LOCK`, `VIBRATE`, `INTERNET`, `CREATE_SHORTCUT`, `READ_EXTERNAL_STORAGE` |

## Fitur

### 1. Default launcher
Bisa dijadikan launcher default Android (kategori `HOME` + `LAUNCHER_APP`). Setelah install, tekan tombol Home → pilih "Ghost" → "Selalu".

### 2. Dockbar 5 slot
Pin 5 aplikasi favorit ke dock di bawah layar. Long-press app di home/drawer → "Pin ke Dock". Long-press app di dock → "Lepas dari Dock".

### 3. Daftar semua aplikasi (App Drawer)
Tap pill "Cari aplikasi…" di atas home → buka drawer yang menampilkan semua aplikasi yang dapat di-launch (filter by label / package name).

### 4. Pintasan Web (Web Shortcut)
- Buka Chrome di HP → titik 3 (⋮) → "Tambahkan ke layar utama" → Ghost Launcher akan menerima intent & menyimpan pintasan web
- Pintasan tampil sebagai icon di home grid, tap → buka browser di URL tsb
- Juga bisa di-trigger manual via `WebShortcutInstallerActivity`

### 5. Folder
Long-press app → opsi "Buat folder" tersedia di popup opsi (folder preview ditampilkan dengan icon pertama).

### 6. Settings
Long-press area kosong home → buka Settings (jumlah kolom 4/5/6, tampilkan/sembunyikan label aplikasi, tentang, reset).

## Fitur rahasia (tidak didokumentasikan)

Tidak didokumentasikan di README ini, tidak di strings.xml (kecuali label "Sembunyikan Aplikasi" yang ambigu bisa berarti apa saja), tidak di commit message. Di-sengaja biar user non-target gak nyasar.

## Optimasi baterai & performa

- **LauncherApps API** — pakai `LauncherApps.getActivityList()` untuk query app list (lebih hemat dari `PackageManager.queryIntentActivities` karena menghormati profil user & sudah di-cache internal oleh sistem)
- **Icon LruCache** — icon aplikasi di-decode sekali lalu di-cache di memory bounded cache (~256 KB)
- **Lazy reload** — AppList di-cache 60 detik; reload hanya saat `PACKAGE_ADDED/REMOVED/CHANGED/REPLACED` broadcast datang (cuma invalidate flag, no service, no wakeup, no polling)
- **RecyclerView + stable IDs** — minimal view recycling overhead, no setHasFixedSize() false
- **No background services** — tidak ada Service/JobScheduler/WorkManager. Launcher hanya jalan saat user buka, idle drain baterai 0%
- **Hardware accelerated**, `largeHeap=false`, `configChanges` mencegah restart activity saat rotate/keyboard attach
- **R8 minify + shrinkResources** — APK turun dari ~4 MB (debug) ke 1.8 MB (release)

## Build

```bash
./gradlew assembleRelease
# output: app/build/outputs/apk/release/app-release.apk
```

Signed dengan debug key (untuk kemudahan install langsung). Untuk produksi, ganti signingConfig ke keystore sendiri.

## Install

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```
Lalu tekan Home → pilih "Ghost" → "Selalu".

## Struktur kode

```
app/src/main/java/com/ghost/launcher/
├── ui/
│   ├── HomeActivity.java              # Home screen + 5-slot dock + app grid
│   ├── AppDrawerActivity.java         # All-apps drawer (search + grid)
│   ├── HiddenAppsActivity.java        # Secret popup (hide/unhide apps)
│   ├── SettingsActivity.java          # Settings (grid columns, labels)
│   └── WebShortcutInstallerActivity.java # Handles INSTALL_SHORTCUT intent
├── adapter/
│   ├── AppGridAdapter.java            # RecyclerView adapter for app grid
│   ├── DockAdapter.java               # 5-slot dock adapter
│   └── HiddenAppsAdapter.java         # Adapter for hide-apps grid
├── model/
│   ├── AppModel.java                  # App data holder
│   └── FolderModel.java               # Folder data holder
├── prefs/
│   └── PrefsManager.java              # SharedPreferences persistence layer
├── util/
│   ├── AppLoader.java                 # LauncherApps wrapper + icon cache
│   ├── WebShortcutHelper.java         # Web shortcut install/open
│   └── IntentUtil.java                # Common intent builders
└── receiver/
    └── PackageChangeReceiver.java     # Invalidates AppLoader cache on package change
```

## Lisensi

MIT. Pake sesukamu.
