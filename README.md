# Llunevie

**Llunevie** is a native Android eReader, Light Novel reader, and personal book collection manager built with **Kotlin** and **[Jetpack Compose](https://developer.android.com/compose)**. It combines book-inspired editorial typography with local-first collection management and reading analytics.

## Key Features

### 1. eReader & Light Novel Engine
- **Local File Import**: Parses and renders local `.epub`, `.pdf`, and `.txt` files on-device using **[Jsoup](https://jsoup.org/)**, **[PDFBox-Android](https://github.com/TomRoush/PdfBox-Android)**, and Android's native `PdfRenderer`. Imported files extract cover artwork immediately and index chapters and insert illustrations in the background.
- **Smart TOC & Illustration Indexing**: Extracts chapter structures from EPUB navigation documents (`toc.ncx` / `nav.xhtml`), PDF outlines, numbered or hyperlinked `Contents` pages, and running header/footer transitions, while isolating color and monochrome insert illustrations into a 2-column gallery.
- **4 Reading Flow Modes**: Switch between **Page Turn**, **Vertical Scroll**, **Webtoon**, and **Two Pages** (full-screen landscape book spread with a left vertical control bar and inline vertical page scrubber).
- **Page Turn Physics**: Hardware-accelerated **Flipbook** (single-page and two-page spread planar fold geometry), **Slide**, and **Fade** transitions with an inline page scrubber and optional volume-key navigation.
- **High-Resolution Illustration Viewer**: Dedicated full-screen viewer for Light Novel and book artwork with multi-touch pinch-to-zoom (`1.0x–4.0x`), 2D pan, and double-tap zoom.
- **Typography, Highlights, Notes & Bookmarks**: 15 bundled offline typefaces (*Literata*, *EB Garamond*, *Source Serif 4*, *Newsreader*, *Lora*, *Merriweather*, *Crimson Pro*, *Playfair Display*, *PT Serif*, *Bitter*, *Plus Jakarta Sans*, *Inter*, *DM Sans*, *Nunito*, *JetBrains Mono*), adjustable font size, weight (`Normal`, `Medium`, `Bold`), alignment, line/paragraph/letter/word spacing, page margins, first-line paragraph indent, unobstructed live slider preview, draggable text selection handles, pastel highlights, manual notes, and one-tap page bookmarks.

### 2. Multi-Shelf Library & Parallel Catalog Search
- **4 Collection View Modes**: Browse shelves in a **3D Spine Bookshelf** (cover-extracted spine colors, proportional physical book heights, and per-row touch lift), **Cover Grid** (adjustable `2–5` books per row), **Full Cover Grid**, or **Detailed List** view.
- **Shelf & Status Filtering**: Filter across **All**, **eReader**, **Physical Collection**, or custom shelves by reading status (`All`, `Unread`, `Reading`, `Finished`), category (`All`, `Light Novel`, `Novel`), and sort order (`Newest`, `Title A–Z`, `Author`, `Progress`).
- **Parallel Multi-API Book Search**: Queries **[Open Library API](https://openlibrary.org/developers/api)**, **[Google Books API](https://developers.google.com/books)**, and **[Jikan (MyAnimeList) API](https://jikan.moe/)** concurrently and deduplicates results by title and publication year.
- **CameraX ISBN & Batch Scanner**: Powered by **[CameraX](https://developer.android.com/media/camera/camerax)** and **[ML Kit Barcode Scanning](https://developers.google.com/ml-kit/vision/barcode-scanning)**, featuring single ISBN lookup with target shelf selection, parallel batch scanning with a vertically scrollable queue, torch control, and a manual entry fallback for unfound ISBNs.

### 3. On-Device Reading Analytics
- **Local Privacy-First Tracking**: Reading sessions, streaks, page bookmarks, highlights, and notes are stored locally on the device in Room.
- **6-Month Contribution Matrix**: `24 × 7` reading activity heatmap with monthly labels, daily duration metrics, reading pace, and an expandable per-book time breakdown.

### 4. Adaptive Themes & Localization
- **4 Reading Palettes & System Sync**: **Paper**, **White**, **Sepia**, and **Black**, paired with a pure `#000000` **AMOLED** toggle and **Follow Device Theme** for automatic light/dark switching.
- **Profile & Cover Crop Tool**: Built-in interactive circular and cover image cropper with drag-pan, pinch-to-zoom, and `90°` rotation.
- **Bilingual UI**: Complete localization across **Indonesian (`id`)** and **English (`en`)**.

## Tech Stack

| Layer | Technology |
| :--- | :--- |
| **Language** | Kotlin 2.0 (`JVM Target 17`) |
| **UI Framework** | [Jetpack Compose](https://developer.android.com/compose) (Material 3, Custom `Canvas` Page-Fold Engine, `HorizontalPager`) |
| **Architecture** | MVVM (`StateFlow`, Coroutines, Unidirectional Data Flow) |
| **Local Storage** | [Room Database](https://developer.android.com/training/data-storage/room) (`SQLite`) & Jetpack DataStore Preferences |
| **Networking & Catalog** | Parallel Coroutine Aggregator ([Open Library](https://openlibrary.org/developers/api), [Google Books](https://developers.google.com/books), [Jikan MAL](https://jikan.moe/)) |
| **eBook Parsing & Media** | [Jsoup](https://jsoup.org/) (EPUB OPF/NCX/XHTML), [PDFBox-Android](https://github.com/TomRoush/PdfBox-Android), Android `PdfRenderer` (`LruCache`), [Coil](https://coil-kt.github.io/coil/), [CameraX](https://developer.android.com/media/camera/camerax) + [ML Kit Barcode Scanning](https://developers.google.com/ml-kit/vision/barcode-scanning) |

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
2. Open the project in **Android Studio** and wait for Gradle sync to complete.
3. Run on a physical device or emulator (`API 26+`), or assemble debug and release APKs via CLI:
   ```bash
   ./gradlew assembleDebug assembleRelease
   ```

## Inspiration

Inspired by **[Flyleaf](https://www.google.com/search?q=https://www.behance.net/gallery/255117785/Flyleaf-a-journal-an-eReader-and-Press-for-reading)**.