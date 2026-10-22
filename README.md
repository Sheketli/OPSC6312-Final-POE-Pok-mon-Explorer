# Pokémon Explorer

An Android Pokédex companion — browse, search, compare and collect Pokémon from
[PokéAPI](https://pokeapi.co), wrapped in a game-UI look inspired by the in-game
Pokédex and Pokémon trading cards.

Built with **Kotlin**, **Android Views (ViewBinding)**, and **Room Database**.

[![Android CI](https://github.com/Sheketli/OPSC6312-Final-POE-Pok-mon-Explorer/actions/workflows/android-ci.yml/badge.svg)](https://github.com/Sheketli/OPSC6312-Final-POE-Pok-mon-Explorer/actions/workflows/android-ci.yml)

The full design brief lives in [`design/pokemon-explorer-design.md`](design/pokemon-explorer-design.md).

---

## Screens

| Screen | What it does |
| --- | --- |
| **Splash** | Cinematic Poké Ball open, logo and tagline |
| **Login / Register** | User authentication with secure local password hashing (Room & jBCrypt) |
| **Explore** | Featured hero Pokémon, Random Discovery, type filter chips, 2-column card grid |
| **Search** | Debounced lookup by name or Pokédex number, with popular searches and an empty state |
| **Detail** | Hero artwork on a type gradient, Normal/Shiny toggle, height/weight, abilities, animated base stats, moves, favourite / catch / compare actions |
| **Compare** | Head-to-head VS arena, inline pickers, per-stat winner highlighting and base-stat totals |
| **Collection** | Pokédex completion ring over Favourites / Caught grids |
| **Type Guide** | All 18 types with strong / weak / resistant / immune breakdowns |
| **Settings** | Appearance, data, preferences and about sections |

## Building

Requires **JDK 17** and the Android SDK (compileSdk 34).

```bash
# Open the project root in Android Studio and press Run, or:
./gradlew assembleDebug
```

CI builds the debug APK and runs the unit tests on every push
(`.github/workflows/android-ci.yml`), so the project is known to compile.

> **Note:** `gradle/wrapper/gradle-wrapper.jar` is not committed in this checkout.
> If `./gradlew` reports a missing wrapper, either open the project in Android
> Studio (it will restore the wrapper) or generate it once with a local Gradle:
> `gradle wrapper --gradle-version 8.9`. The intended Gradle version is pinned in
> `gradle/wrapper/gradle-wrapper.properties`. CI installs Gradle 8.9 directly for
> the same reason.

### Toolchain

Versions are centralised in `gradle/libs.versions.toml`.

| | |
| --- | --- |
| Android Gradle Plugin | 8.5.2 |
| Kotlin | 2.0.20 |
| Gradle | 8.9 |
| minSdk / targetSdk / compileSdk | 24 / 34 / 34 |

## Project layout

```
app/src/main/java/com/pokemon/explorer/
├── MainActivity.kt          Single activity: splash → app shell, back handling
├── data/
│   ├── db/                  Room entities and database for User authentication
│   ├── repository/          AuthRepository for managing logins
│   ├── Models.kt            PokemonSummary, PokemonDetail, Stat, Ability, Sprites
│   ├── TypeData.kt          Type colours, contrast helper, full 18-type matchup chart
│   └── PokeApi.kt           Dependency-free PokéAPI client + in-memory response cache
├── state/
│   └── AppState.kt          ViewModel: per-tab back stacks, user sessions, favourites, caught, theme
└── ui/
    ├── adapters/            RecyclerView adapters for lists and grids
    └── fragments/           One Fragment per screen, listed above
```

Unit tests live in `app/src/test/java/com/pokemon/explorer/` and cover the
hand-transcribed type chart. Run them with `./gradlew testDebugUnitTest`.

### Architecture notes

- **Navigation** is a stack per bottom-nav tab held in `AppState`, so each tab
  remembers where you were and Back walks that tab's history. Fragments are manually swapped via `FragmentManager`.
- **Networking** uses `HttpURLConnection` and `org.json` (both part of the
  platform) rather than Retrofit, so there is no networking dependency to
  configure. Responses are memoised for the life of the process.
- **Images** are loaded with [Coil](https://coil-kt.github.io/coil/) into `ImageView` from the
  official-artwork sprite set. Normal and shiny artwork both come from one
  helper, `PokeApi.artworkUrl(id, shiny)`.
- **Persistence** User credentials are saved securely in a local `Room` database. Favourites, caught Pokémon, and application preferences are saved via `SharedPreferences`.
- **Authentication** A full login/register flow ensures data is kept behind a user profile.

## Differences from the original web prototype

This app replaces an earlier React/Vite prototype. Deliberate changes:

- **No phone frame.** The prototype simulated a 390×844 device inside the
  browser. On Android the app *is* the device, so the mock frame, fake status bar
  and dynamic island are gone; the real system bars are used via edge-to-edge.
- **State survives rotation.** Collection and navigation state live in a
  `ViewModel`; the browser version lost them on reload.
- **Compare pickers accept names.** The prototype's placeholder read "Name or #"
  but only parsed integers. Here a name is resolved through the API.
- **Reset Collection and Clear Cache are wired up.** They were inert buttons in
  the prototype.

## Known gaps

- **Searches are exact, by design.** PokéAPI's `/pokemon/{name}` endpoint returns
  a single match, so typing "pika" finds nothing until "pikachu" is complete —
  same behaviour as the prototype. Fuzzy or prefix search would need the full
  name index from `/pokemon?limit=1025`.
- **Bundled fonts were not carried over.** The design uses Nunito, Outfit and
  JetBrains Mono; the app currently falls back to platform families. Drop the TTFs
  into `app/src/main/res/font/` and configure them in XML to match the design
  exactly.

## Attribution

Pokémon data from [PokéAPI](https://pokeapi.co). Sprites from the
[PokeAPI sprites](https://github.com/PokeAPI/sprites) repository.

Pokémon and all related names are trademarks of Nintendo / Game Freak / Creatures
Inc. This is a non-commercial fan project, not affiliated with The Pokémon
Company.
