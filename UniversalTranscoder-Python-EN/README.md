# Universal Transcoder (English edition)

A **fully offline** universal format converter: video, audio, image and document, with batch
processing, fault tolerance with automatic repair, and two front ends (desktop and web).

The conversion core is **FFmpeg**. The user interface ships in two independent forms: a
**PyQt6 desktop application** and a **Vue 3 + JSX web application**, both sharing the same
core logic.

- Version: v1.0.0
- Author: Xu
- Folder: `UniversalTranscoder-Python-EN` (this is the English project; it is independent of, and can
  be run side by side with, the Chinese edition)

---

## 1. Feature Overview

### 1.1 Video conversion

Target formats: `mp4 / mkv / mov / avi / flv / webm / ts / mpg / wmv / 3gp / m4v / gif`

Adjustable parameters:

- Target format and 14 built-in video presets (selecting one fills the parameters automatically)
- Quality tier (lossless / high / standard / small) and constant-quality CRF
- Resolution (width, height, scale mode: fit / fill / stretch) and frame rate
- Video codec, video bitrate and pixel format
- Rotation (0/90/180/270) and mirroring (horizontal / vertical / both)
- Cropping and clip range (start time, duration or end time)
- Speed change (audio and video stay in sync)
- Watermarks: an image or text, with corner or centre placement and an opacity control
- Burned-in subtitles (srt / ass / ssa / vtt)
- Metadata (title, artist, album, comment)
- Stream selection (automatic, first stream only, or explicit stream indices) and dropping the
  audio or video track
- "Change the container only" (stream copy, no re-encoding, completes in seconds)

### 1.2 Audio conversion

Target formats: `mp3 / m4a / wav / flac / ogg / opus / ac3 / aiff / amr / wma`

Adjustable parameters: audio codec, audio bitrate, channels (1/2/6/8), sample rate, volume gain
(a multiplier or decibels), speed change, clip range and metadata.

`m4a` also offers a "direct repackage" preset that only changes the container without
re-encoding, so it is extremely fast and completely lossless.

### 1.3 Image conversion

Target formats: `jpg / png / webp / bmp / tiff / gif / avif / ico`

Adjustable parameters: quality, resolution and scaling, rotation and mirroring, cropping and
watermarks (image or text).

It also includes **video snapshots**: extracting several frames evenly across a video while
automatically avoiding the opening black frame and the closing credits (the effective range is
taken from 5% to 95%).

### 1.4 Document conversion

- **Images to PDF**: merge several images into one PDF, reorder pages, and composite any
  transparency onto a white background automatically.
- **PDF to images**: export every page as PNG or JPG, with selectable DPI and a page range such
  as `1-3,5`.
- **Text to PDF**: a built-in minimal PDF writer that typesets output with no external office
  software at all.

### 1.5 Job queue

- Batch submission with automatic de-duplication (same source plus same target format)
- Live progress, instantaneous speed, time remaining and size change
- Start, pause, resume, stop, clear and remove selected
- Double-click a failed job for details: error code, title, advice and the raw FFmpeg error
- Optionally open the output folder once everything finishes

### 1.6 Fault tolerance and automatic repair

- **Error triage**: the raw FFmpeg error text is classified into 15 categories (damaged source,
  insufficient permission, disk full, odd dimensions, missing decoder, hardware failure and
  more), each with readable advice.
- **Automatic retry**: two recoverable categories are repaired and retried once
    - Odd dimensions: padded automatically to even sizes
    - Hardware encoding failure: the **whole command is rebuilt with a software encoder** (adding
      `-hwaccel none` alone would not work, because that does not replace the encoder)
- **Automatic downgrade on stream-copy conflicts**: if "container only" is combined with filters
  or watermarks, the job switches to full re-encoding instead of failing by definition.
- **Runaway protection**: a cap on concurrent jobs (default 1, the most stable setting on
  mechanical disks)

---

## 2. Directory Layout

```
UniversalTranscoder-Python-EN/
├── app/                        Application source package
│   ├── main.py                 Entry point (run_gui / run_web)
│   ├── core/                   Pure business core, independent of any UI
│   │   ├── ffmpeg_locator.py   Locating and verifying FFmpeg/ffprobe
│   │   ├── media_info.py       ffprobe probing and structured parsing
│   │   ├── capabilities.py     Capability matrix (encoders, muxers, hardware)
│   │   ├── command_builder.py  Compiling intent into FFmpeg arguments (the algorithm core)
│   │   ├── runner.py           Subprocess execution, live progress parsing, cancellation
│   │   ├── tasks.py            Job model and queue state machine
│   │   ├── errors.py           FFmpeg error classification and advice
│   │   ├── presets.py          35 built-in presets
│   │   ├── hwaccel.py          Hardware encoder detection and selection
│   │   ├── thumbnail.py        Video snapshots
│   │   └── doc_convert.py      Document conversion (image/PDF, text to PDF)
│   ├── gui/                    PyQt6 desktop application
│   │   ├── main_window.py      Main window
│   │   └── style.py            Dark theme QSS
│   ├── web/                    FastAPI web backend (REST + WebSocket)
│   └── util/                   Paths, configuration, logging, text and formatting
├── frontend/                   Vue 3 + JSX web application (built with Vite)
│   └── dist/                   Build output, served by the backend
├── resources/ffmpeg/           The bundled FFmpeg core
├── assets/                     Application icon (icon.ico / icon.png)
├── build/                      PyInstaller configuration and one-click build script
├── tools/                      Self-check and build helper scripts
├── run_gui.py                  Desktop launcher
├── run_web.py                  Web launcher
└── requirements.txt
```

---

## 3. Quick Start

### 3.1 Requirements

- Windows 10 or 11
- Python 3.10 or newer (development was verified on Python 3.14)
- FFmpeg does not need to be installed separately; the core ships in `resources/ffmpeg/`

### 3.2 Create the virtual environment and install dependencies

```bat
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
```

### 3.3 Start the desktop application

```bat
.venv\Scripts\python.exe run_gui.py
```

### 3.4 Start the web application

```bat
.venv\Scripts\python.exe run_web.py
```

It listens on `http://127.0.0.1:8765/` by default and opens the browser automatically. The
listen address and port can be changed under Settings, Web app.

> You can also run `python -m app.main --web --port 9000` to pick a port directly.
> The packaged executable accepts the same flag: `UniversalTranscoder.exe --web`.

### 3.5 Build the web front end (optional, for the full interface)

```bat
cd frontend
npm install
npm run build
```

The build output goes to `frontend/dist` and is served by the backend automatically. If it has
not been built, the web app still runs, but the interface falls back to a built-in notice page
while all APIs remain available.

---

## 4. Packaging as a Standalone Executable

One-click build (recommended):

```bat
build\build.bat
```

The script performs, in order: create the environment, install dependencies, generate the icon,
build the front end, run the core self-check and package with PyInstaller.

Or manually:

```bat
.venv\Scripts\python.exe -m PyInstaller build\UniversalTranscoder.spec --noconfirm --clean
```

The output lands in `dist\UniversalTranscoder\`. The **whole folder** is the portable edition:

- No external dependencies; copy it to any **offline** Windows machine and run it
- FFmpeg and the web static assets are bundled inside
- The application icon is applied
- One-folder mode (fast start-up, less prone to security software false positives)

---

## 5. Self-Check and Verification Scripts

The project provides four reproducible self-check scripts under `tools/`:

| Script           | Purpose                                                                                                | How to run                    |
|------------------|--------------------------------------------------------------------------------------------------------|-------------------------------|
| `smoke_core.py`  | Core chain: locate, probe, build, execute, verify output, fault case                                   | `python tools\smoke_core.py`  |
| `test_matrix.py` | **Conversion matrix**: exhaustively convert all 30 target formats and verify every output              | `python tools\test_matrix.py` |
| `smoke_gui.py`   | Desktop: build the real window, enqueue, cross-thread signals, table refresh (works without a display) | `python tools\smoke_gui.py`   |
| `smoke_web.py`   | Web API: system info, presets, conversion, snapshots, document conversion                              | see the header of the file    |
| `make_icon.py`   | Generate the application icon (multi-size ico plus png)                                                | `python tools\make_icon.py`   |

`test_matrix.py` gives the strongest coverage: it really converts every format and then uses
ffprobe to verify each output again, for example confirming that the produced `.jpg` really is an
image rather than a video stream renamed to jpg.

---

## 6. Configuration and Log Locations

The program never writes into its own installation folder; configuration and logs live in the
user data directory:

```
%APPDATA%\UniversalTranscoder-Python-EN\
├── settings.json     Configuration (plain JSON, hand-editable)
└── logs\app.log      Run log (rotated, 2 MB per file, five files kept)
```

If `%APPDATA%` is unavailable, it falls back to `.universal_transcoder_en` in the user's home
directory.

---

## 7. How It Works

1. **Layered architecture**: `core` depends on no UI framework, so the desktop and web front ends
   share one conversion logic. Behaviour is identical by construction and the core can be tested
   on its own.
2. **Argument lists, not command strings**: every FFmpeg call is produced as an argument list,
   which removes quoting and spacing differences between Windows and Unix and eliminates command
   injection.
3. **Progress parsing**: `-progress pipe:1` makes FFmpeg emit machine-readable `key=value`
   progress blocks, far more stable than parsing human-readable stderr and unaffected by the
   interface language. Each block ends with `progress=continue` or `progress=end`.
4. **Fixed filter chain order**: crop, scale, rotate/mirror, frame rate, watermark, subtitle,
   speed change. The order cannot be swapped without causing subtle defects such as misaligned
   coordinates after rotation or a watermark that deforms with scaling.
5. **Watermarks and subtitles use filter_complex**: they are multi-input filters, so the video
   stream is mapped from the filter output label and the usual `-map 0:v:0` must be skipped to
   avoid a conflict.
6. **Thread safety**: core callbacks fire on worker threads. The desktop front end marshals them
   back with Qt queued signals, and the web front end posts them with `call_soon_threadsafe`
   before broadcasting, so no UI object is ever touched from a foreign thread.

---

## 8. Notice

This software never modifies a source file; every output is a newly written file. Results depend
on the quality of the source and the parameters chosen, so keep your original material before
converting.
