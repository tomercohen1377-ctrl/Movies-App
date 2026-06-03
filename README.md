# 🎬 Movies App

A production-quality Android application that displays the latest movies using [The Movie Database (TMDB) API](https://www.themoviedb.org/documentation/api). Built as part of a development skills assignment with a focus on clean architecture, code quality, and user experience.

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Features](#features)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Technical Decisions](#technical-decisions)
- [Limitations](#limitations)
- [Future Improvements](#future-improvements)

---

## Overview

This app allows users to browse the latest movies, filter by category, view movie details, and save favorites — all built on top of the TMDB API.

---

## Architecture

The app follows **Clean Architecture** with **MVI (Model-View-Intent)** as the UI pattern.

```
UI Layer (Compose + MVI)
    ↕
Domain Layer (UseCases + Repository Interfaces)
    ↕
Data Layer (Repository Impl + Remote/Local sources)
```

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the full architecture breakdown.

---

## Features

| Feature | Status |
|---|---|
| Movie list with poster images | ✅ |
| Category filter (Upcoming / Top Rated / Now Playing) | ✅ |
| Movie details screen (poster, description, release year, rating) | ✅ |
| Smooth screen transition animations | ✅ |
| Favorites tab | ✅ |
| Image caching (1-day expiration) | ✅ |
| Infinite scrolling / pagination | ✅ |
| Unit tests | ✅ (Bonus) |
| Trailer playback | ✅ (Bonus) |
| Offline handling | ✅ (Bonus) |

---

## Tech Stack

| Layer | Technology |
|---|---|
| UI | Jetpack Compose + Material 3 |
| State management | MVI (ViewModel + StateFlow) |
| DI | Hilt |
| Navigation | Compose Navigation |
| Networking | Retrofit + OkHttp |
| Image loading | Coil (with disk cache) |
| Local storage | Room |
| Async | Kotlin Coroutines + Flow |
| Paging | Paging 3 |
| Video | YouTube Player / ExoPlayer |
| Testing | JUnit4, MockK, Turbine |

---

## Project Structure

```
app/
├── data/
│   ├── local/          # Room DB, DAOs, entities
│   ├── remote/         # Retrofit API, DTOs
│   ├── repository/     # Repository implementations
│   └── mapper/         # DTO → Domain model mappers
├── domain/
│   ├── model/          # Domain models
│   ├── repository/     # Repository interfaces
│   └── usecase/        # Use cases (business logic)
├── presentation/
│   ├── movielist/      # Movie list screen (MVI)
│   ├── moviedetail/    # Movie detail screen (MVI)
│   ├── favorites/      # Favorites screen (MVI)
│   ├── navigation/     # NavGraph + routes
│   └── common/         # Shared UI components
└── di/                 # Hilt modules
```

---

## Getting Started

### Prerequisites
- Android Studio Hedgehog or later
- Android SDK 34+
- A TMDB API key ([register here](https://www.themoviedb.org/settings/api))

### Setup

1. Clone the repository:
   ```bash
   git clone <your-repo-url>
   ```

2. Add your TMDB API key to `local.properties`:
   ```
   TMDB_API_KEY=your_api_key_here
   ```

3. Open the project in Android Studio and run on a device/emulator.

---

## Technical Decisions

See [`docs/TECHNICAL_DECISIONS.md`](docs/TECHNICAL_DECISIONS.md) for the full reasoning behind major choices.

---

## Limitations

- Trailer playback requires a YouTube account/app on device (uses Intent or embedded player).
- Offline mode shows only previously cached data; no background sync.
- Favorites are stored locally (no cloud sync).

---

## Future Improvements

- Search functionality across all movies.
- User authentication via TMDB account.
- Cloud-synced favorites.
- Home screen widget for trending movies.
- Dark/light theme toggle.
- Accessibility improvements (TalkBack, dynamic font scaling).
