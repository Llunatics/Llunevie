# Llunevie

**Llunevie** is a native Android eReader, Light Novel reader, and personal book collection manager built with **Kotlin** and **[Jetpack Compose](https://developer.android.com/compose)**. It pairs tactile editorial typography with a dual surface system (**Classic Design** by default and an optional translucent **Glass Design** mode).

## Key Features

### 1. eReader & Light Novel Engine
- **Local File Import**: Parses and renders local `.epub`, `.pdf`, and `.txt` files directly on-device via **[Jsoup](https://jsoup.org/)** and Android's native `PdfRenderer`. Imported books normalize raw filenames, extract cover artwork immediately, and display a minimal inline progress bar on the book card while chapters and illustrations index in the background.
- **4 Reading Flow Modes**: Switch between **Page Turn**, **Vertical Scroll**, **Webtoon**, and **Two Pages** (auto-landscape `1.414 : 1` book spread with strict line-height pagination so bottom lines never clip, plus an auto-docking edge fullscreen toggle).
- **Planar Page-Turn Physics**: Hardware-accelerated single-page and two-page spread flipbook engine bounded strictly to the page frame, featuring flat planar diagonal fold geometry, margin-accurate reverse page reflection, opaque paper backing, Smooth Slide and Gentle Fade transitions, interactive bottom page scrubber, and hardware volume-key navigation.
- **Interactive Artwork Viewer**: Detects both full-color and monochrome Light Novel insert illustrations, providing a 2-column chapter drawer gallery and a full-screen viewer with multi-touch pinch-to-zoom (`1.0x–4.0x`), 2D pan, and quick zoom presets.
- **Typography, Pastel Highlights & Manual Notes**: 15 distinct downloadable/bundled editorial and sans-serif typefaces, adjustable font size (`12sp–28sp`), alignment, weight, line/paragraph/letter/word spacing, margins, paragraph indentation, dynamic drag-range text selection with 8 pastel highlighter swatches, icon-based highlight actions, in-book quote search, and manual note creation for both EPUB and PDF documents.

### 2. Multi-Shelf Library & Parallel Catalog Search
- **4 Collection View Modes**: Browse shelves in a **3D Spine Bookshelf** (cover-extracted spine colors, proportional book heights, and isolated single-book touch lift), **Cover Grid** (`2:3` ratio with progress bar), **Full Cover Grid**, or **Editorial List** view.
- **Shelf & Status Filtering**: Filter across **All Shelves (`Semua`)**, **eReader**, **Physical Collection**, or custom shelves, combined with reading status (`All`, `Unread`, `Reading`, `Finished`), category (`All`, `Light Novel`, `Novel`), one-tap sort cycling (`Newest`, `Title A–Z`, `Author`, `Progress`), and an inline **Manage Shelves** panel.
- **Parallel Multi-API Book Search**: Queries **Gramedia**, **Goodreads**, **[Google Books API](https://developers.google.com/books)**, **[Open Library API](https://openlibrary.org/developers/api)**, and **[Jikan (MyAnimeList) API](https://jikan.moe/)** concurrently with first-response streaming results.
- **CameraX ISBN & QR Scanner**: Powered by **[CameraX](https://developer.android.com/media/camera/camerax)** and **[ML Kit Barcode Scanning](https://developers.google.com/ml-kit/vision/barcode-scanning)**, featuring a dimmed viewfinder cutout, strict ISBN-10/ISBN-13 validation, center-reticle autofocus, first-completed parallel lookup (`<300ms`), hardware torch control, and an instant 0ms batch scan queue.

### 3. On-Device Reading Analytics
- **Local Privacy-First Tracking**: All reading sessions, streaks, highlights, and notes are stored locally on the device in Room. Books are marked finished strictly upon reaching the final page.
- **6-Month Activity Matrix**: `24 × 7` contribution heatmap with monthly labels, daily reading duration, pace metrics, and per-book time allocation bars.

### 4. Adaptive Themes & Dual Design System
- **4 Reading Palettes**: **Paper**, **White**, **Sepia**, and **Black**, plus a pure `#000000` **AMOLED** toggle.
- **Classic & Glass Design Modes**: Uses crisp **Classic Design** surfaces with solid dark active selectors by default (`Glass Design: Off`) and switches navigation docks, sheets, and segmented capsules to high-blur translucent **Glass Design** when enabled.
- **Bilingual UI**: Instant localization across **Indonesian (`id`)** and **English (`en`)**.

## Tech Stack

| Layer | Technology |
| :--- | :--- |
| **Language** | Kotlin 2.0 (`JVM Target 17`) |
| **UI Framework** | [Jetpack Compose](https://developer.android.com/compose) (Material 3, Custom `Canvas` Vector Icons & Planar Page-Fold Engine) |
| **Architecture** | MVVM (`StateFlow`, Coroutines, Unidirectional Data Flow) |
| **Local Storage** | [Room Database](https://developer.android.com/training/data-storage/room) (`SQLite`) & Jetpack DataStore Preferences |
| **Networking & Catalog** | Parallel Coroutine Aggregator (Gramedia, Goodreads, [Google Books](https://developers.google.com/books), [Open Library](https://openlibrary.org/developers/api), [Jikan MAL](https://jikan.moe/)) |
| **eBook Parsing & Media** | [Jsoup](https://jsoup.org/) (EPUB OPF/NCX/XHTML), Android `PdfRenderer` (`LruCache`), [Coil](https://coil-kt.github.io/coil/), AndroidX `Palette`, [CameraX](https://developer.android.com/media/camera/camerax) + [ML Kit](https://developers.google.com/ml-kit/vision/barcode-scanning) |

## Project Structure

```text
Llunevie/
├── app/
│   ├── build.gradle.kts
│   ├── keystore/
│   │   └── llunevie-release.jks
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/
│       │   └── llunevie-icon.svg
│       ├── java/com/llunaticsys/llunevie/
│       │   ├── LlunevieApplication.kt
│       │   ├── LlunevieViewModel.kt
│       │   ├── MainActivity.kt
│       │   ├── data/
│       │   │   ├── local/        # Entities, LlunevieDao, LlunevieDatabase, UserPreferencesRepository
│       │   │   ├── parser/       # EpubNativeParser, PdfNativeRenderer, TxtNativeParser, SpineColorExtractor
│       │   │   └── remote/       # MultiBookApiRepository
│       │   ├── model/            # Models.kt
│       │   └── ui/
│       │       ├── components/   # CommonComponents.kt
│       │       ├── reader/       # CylindricalFlipbookCanvas.kt, ReaderScreen.kt
│       │       ├── screens/      # Home, Library, AddBook, IsbnCameraScanner, Stats, Settings
│       │       └── theme/        # LlunevieTheme.kt, LlunevieStrings.kt
│       └── res/
├── llunevie_prototype.html       # Standalone UI/UX design reference
├── build.gradle.kts
├── gradle.properties
└── settings.gradle.kts
```

## Native Compliance

Zero `WebView` components or HTML assets are bundled inside `app/`:

```bash
$ grep -r "WebView\|loadUrl\|JavascriptInterface\|android_asset.*html" app/src/main/java app/src/main
# 0 matches
```

## Getting Started

### Prerequisites
- **Android Studio Ladybug (2024.2.1)** or newer
- **JDK 17**
- **Android SDK 35** (`minSdk 26`, `targetSdk 35`)

### Build & Run
1. Clone the repository:
   ```bash
   git clone https://github.com/Llunatics/Llunevie.git
   cd Llunevie
   ```
2. Open the project in **Android Studio** and let Gradle sync complete.
3. Assemble signed debug or release APKs via CLI:
   ```bash
   ./gradlew assembleDebug assembleRelease
   ```

## Roadmap
- **v0.1 (Initial Release)**: Native Compose eReader (`.epub`, `.pdf`, `.txt`), planar single/spread flipbook engine, dynamic EPUB text selection & pastel highlighter, manual notes, multi-shelf 3D spine library, parallel multi-API catalog search, CameraX ISBN/QR scanner, and local reading analytics.
- **v1.0 (Planned)**: **Reading Journal** (long-form notes, quote cards, and reading reflections) and **Press** modules.

## Inspiration

Inspired by **[Flyleaf](https://www.google.com/search?q=https://www.behance.net/gallery/255117785/Flyleaf-a-journal-an-eReader-and-Press-for-reading)**.