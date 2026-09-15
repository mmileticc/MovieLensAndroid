# Movienee (Android)

Native Android client for Movienee — a movie discovery and social-tracking app. It combines
catalog browsing, AI-assisted search (photo recognition and natural-language "mood" semantic search),
location-aware cinema showtimes, and a social layer (friends, watch history, taste-based
recommendations, comments) on top of a custom Ktor backend.

Repository: `mmileticc/MovieLensAndroid`
Backend counterpart: `mmileticc/MovieLensBackendKtor`

---

## 1. Overview & Core Purpose

Movienee lets a user find something to watch through whichever entry point fits the moment —
searching a title, photographing a poster or a frame from a film, or describing what they're in
the mood for — and then track what they've watched, keep a watchlist, see what their friends are
watching, and get genre-based recommendations, including joint "Movie Night" suggestions for a
specific friend. Cinema showtimes are scraped server-side and surfaced sorted by distance from the
user.

Core engineering goals:

- **Usable without an account** — browsing, search, and movie detail all work signed-out; a
  Google sign-in is only required for actions that need identity (scanning, watchlist, ratings,
  friends).
- **Offline-first for owned data** — watchlist, history, profile, friends, activity feed, taste
  profile, and friend-profile views are backed by a Room cache and exposed as `Flow`s, so the UI
  renders instantly from the last known state while a network refresh happens in the background.
- **Resilient networking** — every repository call is funneled through a single `safeApiCall`
  wrapper and a centralized `friendlyMessage()` mapper, so no raw exception text (timeouts, stack
  traces, HTTP codes) is ever shown to the user; a dedicated OkHttp `Authenticator` transparently
  refreshes the backend session token on expiry without interrupting the user.
- **Low-friction distribution** — the backend base URL is runtime-configurable (with a hosted
  default), so a release APK works out of the box for testers without a local backend, while still
  allowing developers to point at `localhost`/a LAN IP.
- **Modern single-activity architecture** — 100% Jetpack Compose UI, one `Activity`, navigation
  and screen composition driven entirely by Navigation-Compose.

---

## 2. Architecture & Data Flow

**Pattern:** MVVM + Repository, with Hilt providing dependency injection across all layers.

```
Composable Screens (ui/*)
       │  observes StateFlow / collects Flow
       ▼
ViewModels (one per screen, @HiltViewModel)
       │  calls suspend functions / observes Flows
       ▼
Repositories (data/repository) ── interface + *Impl, bound via Hilt @Binds
       │                               │
       │ (network)                     │ (local, offline-first reads)
       ▼                               ▼
Retrofit API services              Room DAOs (WatchlistDao, HistoryDao, GenericCacheDao)
(MovieApiService, UserApiService,       │
 AuthApi, VisionApi)                    ▼
       │                          SQLite (movielens.db)
       ▼
OkHttpClient
  ├─ DynamicBaseUrlInterceptor  (rewrites scheme/host/port to the user-configured backend URL)
  ├─ AuthInterceptor            (attaches "Authorization: Bearer <jwt>")
  ├─ HttpLoggingInterceptor     (BASIC in debug, NONE in release)
  └─ TokenAuthenticator         (on 401: silently refreshes the Firebase ID token, re-exchanges
                                 it for a new backend JWT, retries the original request once)
       │
       ▼
Ktor backend (see MovieLensBackendKtor)
```

### Offline-first read path

For data the user "owns" (watchlist, history, own profile, friends list, pending requests, friend
activity feed, taste profile, friend profiles, Movie Night suggestions), each repository exposes
an `observeCached*`/`observeWatchlist`/`observeHistory` `Flow` that reads **only** from Room/the
generic JSON cache (`GenericCacheEntity`, a single-table key→JSON-payload store keyed like
`"friends:<userId>"` or `"movienight:<userId>:<friendId>:<page>"`). A parallel `getXxx()` suspend
function performs the actual network call and writes the fresh result back into the same cache —
the `Flow` then emits automatically. ViewModels typically collect the cached `Flow` for instant
render and trigger the network refresh in `init {}`/on pull-to-refresh.

### Authentication data flow

1. User signs in with Google (Firebase Authentication) → `AuthRepository.signInWithGoogleIdToken`.
2. The Firebase ID token is exchanged with the backend (`POST /api/auth/firebase`) for a
   24-hour, HMAC256-signed backend JWT, persisted in `SessionManager` (`SharedPreferences`).
3. `AuthInterceptor` attaches this JWT to every subsequent request.
4. On a `401`, `TokenAuthenticator` (an OkHttp `Authenticator`, not a regular interceptor) force-
   refreshes the underlying Firebase ID token (Firebase's own refresh token lives far longer than
   the 24h backend JWT), re-exchanges it, saves the new session, and retries — using a **separate**
   `@AuthRefresh`-qualified `OkHttpClient`/`Retrofit`/`AuthApi` that has neither `AuthInterceptor`
   nor `TokenAuthenticator` installed, to avoid a Dagger provider cycle and an infinite 401 loop.

### Visual search data flow

`SearchScreen` → camera/gallery `Uri` → `VisionRepositoryImpl.detect()` downsamples the bitmap
(two-pass `BitmapFactory` decode to avoid OOM on large camera images), corrects EXIF rotation,
JPEG-compresses under a size budget, uploads as multipart to `POST /chat/vision` → backend returns
ranked movie candidates with TMDB matches and cinema availability → results render inline,
clickable through to `MovieDetailScreen`.

---

## 3. Key Features & Technical Implementation

| Feature | Mechanism | Key files |
|---|---|---|
| Google sign-in + backend session | Firebase Auth Google credential → Firebase ID token → exchanged for backend JWT; JWT persisted in `SharedPreferences`; auto-refreshed transparently on expiry. | `data/auth/AuthRepository.kt`, `data/pref/SessionManager.kt`, `data/remote/interceptor/TokenAuthenticator.kt`, `ui/auth/AuthViewModel.kt` |
| Three-mode search | Text search (debounced), photo/vision search (Gemini via backend), and semantic natural-language search over the current cinema repertoire, switchable in one screen. | `ui/search/SearchScreen.kt`, `ui/search/SearchViewModel.kt`, `data/repository/VisionRepository.kt`, `data/repository/MovieRepository.kt` |
| Movie detail | Aggregates TMDB metadata, cast, genres, location-sorted showtimes, supplementary IMDb/Rotten Tomatoes ratings, a "friends who watched this" badge, and a public comment thread — all from a single backend call plus an on-demand comments call. | `ui/detail/MovieDetailScreen.kt`, `ui/detail/MovieDetailViewModel.kt` |
| Actor detail | Biography, photo, and sorted filmography, reached from cast entries on Movie Detail. | `ui/actor/ActorDetailScreen.kt`, `ui/actor/ActorDetailViewModel.kt` |
| Watchlist & history (Library) | Offline-first via Room (`WatchlistDao`/`HistoryDao`); optimistic add/remove with server sync; rating + short-review editable inline; "undo" for accidental "mark as watched". | `ui/library/LibraryScreen.kt`, `ui/library/LibraryViewModel.kt`, `data/local/WatchlistDao.kt`, `data/local/HistoryDao.kt` |
| Social graph | User search, send/accept/reject/cancel friend requests, friend list with match score, friend-only activity feed, friend profile (full or limited preview depending on relationship status). | `ui/profile/ProfileScreen.kt`, `ui/friendprofile/FriendProfileScreen.kt`, `data/repository/UserRepository.kt` |
| Taste profile & recommendations | Displays a per-genre affinity breakdown ("Tvoj ukus") computed server-side; personalized Home feed blends TMDB discover results across the user's top genres. | `ui/profile/ProfileScreen.kt`, `ui/home/HomeViewModel.kt` |
| Movie Night suggestions | Joint recommendation screen for a specific friend, based on shared genre affinity; supports paging via a "refresh suggestions" action, prefetched when a friend profile opens. | `ui/movienight/MovieNightScreen.kt`, `ui/movienight/MovieNightViewModel.kt` |
| Location-aware showtimes | Reads the device's last-known location via the plain `android.location.LocationManager` (no Play Services location dependency), passed to the backend for server-side haversine sorting. | `data/location/LocationHelper.kt` |
| Offline generic cache | A single Room entity (`GenericCacheEntity`, `cacheKey` → JSON payload) backs profile, friends, pending/sent requests, activity feed, taste profile, friend profiles, and Movie Night suggestions — avoiding a bespoke table per data shape. | `data/local/GenericCacheEntity.kt`, `data/local/GenericCacheDao.kt`, `data/repository/UserRepository.kt` |
| Dynamic backend URL | Base URL is rewritten per-request by an interceptor reading from `SharedPreferences`; defaults to the hosted Render deployment so a release build works without configuration, while a settings dialog lets developers/testers point at another host and keeps a short history of recent URLs. | `data/remote/interceptor/DynamicBaseUrlInterceptor.kt`, `data/pref/UrlStorage.kt`, `ui/components/IpSettingsDialog.kt` |
| Centralized error handling | Every repository call is wrapped by `safeApiCall`; a single `friendlyMessage()` function maps `IOException`/`HttpException` status codes to Serbian-language, non-technical messages — no raw exception text ever reaches the UI. | `util/SafeApiCall.kt`, `util/FriendlyError.kt`, `util/TransientServiceException.kt` |
| Theming | Local (non-synced) dark/light mode preference, read at `Activity` startup before first composition. | `data/pref/ThemePreferences.kt`, `ui/theme/Theme.kt`, `MainActivity.kt` |
| Avatar & profile editing | Client-side image compression/downsampling before sending an avatar as a base64 data URI; username/bio editing with server-side validation surfaced back to the UI. | `util/AvatarImage.kt`, `ui/profile/ProfileViewModel.kt` |

---

## 4. Tech Stack Reference

| Category | Technology | Exact Usage in This Project |
|---|---|---|
| Language | Kotlin 2.0.21 | Entire codebase |
| UI toolkit | Jetpack Compose (BOM `2024.09.00`), Material 3 | 100% Compose UI, single `ComponentActivity`, no XML layouts |
| Architecture | MVVM + Repository, Hilt DI | `@HiltViewModel` per screen, `@Binds`-bound repository interfaces |
| Dependency injection | Dagger Hilt 2.51.1 (+ KSP) | `di/NetworkModule.kt`, `di/DatabaseModule.kt`, `di/RepositoryModule.kt`, `di/FirebaseModule.kt` |
| Navigation | Navigation-Compose 2.8.6 | `navigation/NavGraph.kt` — single `NavHost`, sealed `Screen` route definitions |
| Networking | Retrofit 2.11.0 + OkHttp 4.12.0 | `data/remote/*ApiService.kt`, custom interceptors/authenticator |
| Serialization (network) | Gson (Retrofit converter) + Scalars converter | JSON DTOs in `data/remote/*/*Models.kt`; scalars for plain-text responses |
| Local persistence (structured) | Room 2.6.1 (+ KSP compiler) | `data/local/AppDatabase.kt` — `WatchlistEntity`, `HistoryEntity`, `GenericCacheEntity` |
| Local persistence (key-value) | Android `SharedPreferences` | `SessionManager`, `ThemePreferences`, `UrlStorage`, `RecentSearchesStore` |
| Authentication | Firebase Auth (BOM `33.1.0`) + Google Sign-In (`play-services-auth` 21.2.0) | `data/auth/AuthRepository.kt`, `ui/auth/SignInScreen.kt` |
| Image loading | Coil 2.6.0 (Compose integration) | Poster/avatar/backdrop rendering throughout `ui/` |
| Image EXIF handling | AndroidX `exifinterface` 1.3.7 | Rotation correction before uploading vision-search photos |
| Lifecycle / state | `lifecycle-viewmodel-compose` 2.8.6, Kotlin Coroutines + `StateFlow`/`Flow` | All ViewModels |
| Location | Android `LocationManager` (framework API, no extra dependency) | `data/location/LocationHelper.kt` |
| Build tooling | Gradle (Kotlin DSL) + version catalog (`libs.versions.toml`), AGP 8.13.2, KSP | `build.gradle.kts` (project + `app`) |
| Firebase project integration | `google-services` Gradle plugin 4.3.15 | `app/google-services.json` |
| Declared but not actively wired | `androidx.datastore:datastore-preferences` 1.1.1 | Present as a dependency; current preference storage uses `SharedPreferences` directly (see above) |
| Min / Target / Compile SDK | 24 / 36 / 36 | `app/build.gradle.kts` |

---

## 5. Project & Directory Structure

```
MovieLens/
└── app/src/main/java/dev/milinko/movielens/
    ├── MainActivity.kt              # Single Activity; sets Compose content + NavHost
    ├── MovieLensApp.kt              # @HiltAndroidApp Application class
    ├── di/                          # Hilt modules
    │   ├── NetworkModule.kt         # OkHttp/Retrofit/API service providers (incl. @AuthRefresh client)
    │   ├── DatabaseModule.kt        # Room database + DAO providers
    │   ├── RepositoryModule.kt      # Binds repository interfaces to implementations
    │   ├── FirebaseModule.kt        # FirebaseAuth provider
    │   └── Qualifiers.kt            # @AuthRefresh qualifier annotation
    ├── navigation/
    │   └── NavGraph.kt              # Sealed Screen routes + MovieLensNavHost composable graph
    ├── data/
    │   ├── auth/AuthRepository.kt   # Firebase sign-in/token retrieval
    │   ├── local/                   # Room: AppDatabase, WatchlistDao/Entity, HistoryDao/Entity,
    │   │                            #       GenericCacheDao/Entity (generic JSON cache)
    │   ├── location/LocationHelper.kt
    │   ├── pref/                    # SharedPreferences-backed stores
    │   │   ├── SessionManager.kt    # Backend JWT + user identity
    │   │   ├── ThemePreferences.kt  # Dark/light mode
    │   │   ├── UrlStorage.kt        # Configurable backend base URL + recent-URL history
    │   │   └── RecentSearchesStore.kt
    │   ├── remote/
    │   │   ├── AuthApi.kt
    │   │   ├── movie/               # MovieApiService + MovieModels (DTOs)
    │   │   ├── user/                # UserApiService + UserModels (DTOs)
    │   │   ├── vision/               # VisionApi + VisionModels
    │   │   ├── image/DataUriFetcher.kt
    │   │   └── interceptor/         # AuthInterceptor, DynamicBaseUrlInterceptor, TokenAuthenticator
    │   └── repository/              # MovieRepository, UserRepository, VisionRepository (+ Impl)
    ├── ui/
    │   ├── auth/                    # SignInScreen, AuthViewModel
    │   ├── home/                    # HomeScreen, HomeViewModel (infinite-scroll feed, "now in cinemas")
    │   ├── search/                  # SearchScreen, SearchViewModel (text/vision/semantic modes)
    │   ├── detail/                  # MovieDetailScreen, MovieDetailViewModel
    │   ├── actor/                   # ActorDetailScreen, ActorDetailViewModel
    │   ├── library/                 # LibraryScreen, LibraryViewModel (watchlist/history tabs)
    │   ├── profile/                 # ProfileScreen, ProfileViewModel (own profile, friends, settings)
    │   ├── friendprofile/           # FriendProfileScreen, FriendProfileViewModel
    │   ├── movienight/              # MovieNightScreen, MovieNightViewModel
    │   ├── components/              # Shared composables (bottom nav, cards, dialogs, state placeholders)
    │   └── theme/                   # Color.kt, Shape.kt, Theme.kt, Type.kt
    └── util/                        # SafeApiCall, FriendlyError, UsernameSanitizer, AvatarImage,
                                      # DevSettings, TaskExtensions, TransientServiceException
```

---

## 6. Configuration, API & Execution

### Prerequisites

- Android Studio (Koala/2024.x or newer recommended for AGP 8.13.x / Compose BOM `2024.09.00`).
- A Firebase project with Google Sign-In enabled, and its `google-services.json` placed at
  `app/google-services.json` (already present in this repo for the project's own Firebase app).
- A reachable instance of the [Movienee backend](https://github.com/mmileticc/MovieLensBackendKtor)
  — either the hosted default or a local instance.

### Backend URL configuration

The app does **not** require a build-time backend URL. It defaults to the hosted deployment
(`https://movielensbackendktor.onrender.com/`) and rewrites every request's scheme/host/port at
the OkHttp layer via `DynamicBaseUrlInterceptor`. To point at a local backend during development,
open the in-app backend settings dialog (accessible from the Profile screen) and enter the new
base URL (e.g. `http://10.0.2.2:8080/` for the Android emulator talking to a backend on the host
machine) — up to 6 recently-used URLs are remembered.

### Key client-side routes (Navigation-Compose)

| Route | Screen | Notes |
|---|---|---|
| `home` | `HomeScreen` | Default start destination; usable signed-out |
| `search` | `SearchScreen` | Text / photo / semantic search |
| `detail/{tmdbId}` | `MovieDetailScreen` | |
| `actor/{actorId}` | `ActorDetailScreen` | |
| `library` | `LibraryScreen` | Watchlist + history; requires sign-in for content |
| `profile` | `ProfileScreen` | Own profile, friends, settings |
| `friend_profile/{friendId}` | `FriendProfileScreen` | |
| `movie_night/{friendId}` | `MovieNightScreen` | |
| `sign_in?next={next}` | `SignInScreen` | Google Sign-In; redirects back to `next` on success |

### Build & run

```bash
# Clone
git clone https://github.com/mmileticc/MovieLensAndroid.git
cd MovieLensAndroid

# Debug build via Gradle wrapper
./gradlew.bat assembleDebug

# Or open in Android Studio and run the "app" configuration on an emulator/device
```

No environment variables are required for a debug build against the hosted backend — the API key
for TMDB/Gemini/etc. all live server-side; the client only needs its Firebase configuration
(bundled `google-services.json`) and, optionally, a custom backend URL set from within the app.
