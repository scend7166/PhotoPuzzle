# Photo Puzzle — Android App

A jigsaw puzzle game that turns your photos into puzzles. Built with Kotlin + Jetpack Compose.

---

## Features

- **Photo picker** — Choose any photo from your gallery, or tap "Random" to get a surprise
- **6 puzzle sizes** — 25, 50, 100, 150, 200, or 250 pieces
- **Tap-to-swap** gameplay — Tap a piece to select it, tap another to swap
- **Live timer** — Tracks your solve time in MM:SS
- **Stats tracking** (persisted with Room):
  - Total puzzles solved
  - Average completion time
  - Average time per piece (seconds)
  - Breakdown by puzzle size

---

## Tech Stack

| Layer | Technology |
|---|---|
| UI | Jetpack Compose + Material3 |
| Navigation | Navigation Compose |
| DI | Hilt |
| Database | Room |
| Image loading | Coil |
| Architecture | MVVM + Repository |
| Min SDK | 26 (Android 8.0) |

---

## Project Structure

```
app/src/main/java/com/photopuzzle/app/
├── MainActivity.kt
├── PhotoPuzzleApp.kt
├── ui/
│   ├── NavGraph.kt
│   ├── theme/Theme.kt
│   └── screens/
│       ├── HomeScreen.kt        ← Photo picker + piece count selector
│       ├── GameScreen.kt        ← Puzzle board + timer
│       ├── StatsScreen.kt       ← Stats display
│       └── StatsViewModel.kt
├── game/
│   ├── PuzzleEngine.kt          ← Bitmap slicing + swap logic
│   └── GameViewModel.kt         ← Game state + timer
└── data/
    ├── AppModule.kt             ← Hilt DI bindings
    ├── models/Models.kt         ← PuzzlePiece, PuzzleResult, StatsOverview
    ├── db/
    │   ├── AppDatabase.kt
    │   └── PuzzleResultDao.kt
    └── repository/
        └── StatsRepository.kt
```

---

## Setup Instructions

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 35

### Steps

1. **Open the project** in Android Studio:  
   `File → Open → select the PhotoPuzzle folder`

2. **Sync Gradle** — Android Studio will prompt you; click "Sync Now"

3. **Run on device or emulator** (API 26+):  
   Click ▶ or press `Shift+F10`

4. On first launch, grant the **READ_MEDIA_IMAGES** permission when prompted

---

## How the Puzzle Works

### Piece Slicing (`PuzzleEngine.kt`)
- A bitmap is divided into a grid based on piece count (e.g. 25 → 5×5 grid)
- Each cell is extracted as a sub-bitmap using `Bitmap.createBitmap()`
- Pieces are assigned their correct `(col, row)` position, then shuffled

### Gameplay
- Pieces are displayed in their **current** grid position
- Tap a piece → it's **highlighted** in blue
- Tap another piece → they **swap** positions
- Green border = piece is in its correct position
- Puzzle is solved when all pieces are in their correct positions

### Stats Storage
- On puzzle completion, a `PuzzleResult` is saved to Room DB
- Stats screen queries aggregated data (AVG, COUNT, GROUP BY) live via Flow

---

## Extending the App

### Add drag-and-drop
Replace the tap-to-swap mechanic in `GameScreen.kt` with Compose's `pointerInput` / `detectDragGestures` for a more tactile feel.

### Add a preview image
Show the original photo as a small reference image in the corner during gameplay.

### Add difficulty levels
Introduce rotation of pieces as a harder mode — store `currentRotation` in `PuzzlePiece` and rotate the Canvas when drawing.

### Leaderboard
Add a `playerName` field to `PuzzleResult` and a best-time query to the DAO for a per-size leaderboard.
