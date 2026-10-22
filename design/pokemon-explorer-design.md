# Pokémon Explorer — Android UI/UX Design (Anime / Pokémon-Style)

Design a complete, polished **Android mobile UI/UX** for an app called **Pokémon Explorer**.

**Tagline:** Discover. Compare. Explore.

The app is a Pokédex-style discovery and collection companion that consumes data from PokéAPI. Users browse, search, filter, view detailed Pokémon info, favourite Pokémon, mark them as caught, compare two Pokémon, discover random Pokémon, check type matchups, and manage settings.

The design should feel like it was pulled from the Pokémon games and anime themselves — energetic, illustrated, and alive — while still being a clean, usable, modern mobile app. Think **Pokémon HOME meets a trading-card app meets the in-game Pokédex UI**, not a generic Material template with Pokémon colours slapped on.

---

## 1. DESIGN DIRECTION

Create a vibrant, illustrated, motion-ready mobile interface inspired by:

* The in-game Pokédex / summary screens (Scarlet & Violet, Legends: Arceus)
* Pokémon trading cards — foil edges, holographic sheen, bold frames
* Anime key-art energy: dynamic diagonals, speed lines, soft glows
* Poké Ball motifs used as structural/graphic elements, not just icons
* Card-based information architecture with a game-UI feel (chunky borders, notches, badge shapes)
* Strong, saturated colour with confident contrast — not muted or "safe"

This should look **fun, kinetic, and premium** at once — the kind of UI that feels like it could animate on tap (cards flipping like trading cards, stat bars filling like HP bars, Poké Balls wobbling on favourite). Describe motion intent even where the deliverable is static Figma frames, so it's ready to hand to a motion/dev team.

Avoid: flat corporate Material Design, muted pastel-only palettes, generic rounded-card fintech look.

Target audience: Pokémon fans who want a fast, good-looking Pokédex — this can feel playful, but every screen still needs to be scannable and fast to use.

---

## 2. DESIGN SYSTEM

Build a reusable Figma design system before any screens.

### Colours

Primary:
* Poké Ball red (#EE1515) + white, used the way the games use it — bold, not decorative
* Deep navy/charcoal for text and dark surfaces (dark mode should feel like a "Pokédex at night," not just inverted grey)
* Electric yellow (#FFCB05) as the energy/accent colour (buttons, highlights, Random Discovery)

Type badge colours (used consistently everywhere a type appears):

Normal #A8A77A · Fire #EE8130 · Water #6390F0 · Electric #F7D02C · Grass #7AC74C · Ice #96D9D6 · Fighting #C22E28 · Poison #A33EA1 · Ground #E2BF65 · Flying #A98FF3 · Psychic #F95587 · Bug #A6B91A · Rock #B6A136 · Ghost #735797 · Dragon #6F35FC · Dark #705746 · Steel #B7B7CE · Fairy #D685AD

Let type colour drive **background gradients on hero/detail cards** (a soft gradient wash of the Pokémon's primary type colour behind the artwork), not just small badges — this is what makes the game UI feel alive.

### Typography

A bold, slightly geometric sans-serif for headings (something with game-UI presence), paired with a clean readable sans for body text. Numbers/stats get a chunky, tabular, high-contrast treatment — they should feel like HP counters.

Hierarchy: Display heading → Screen title → Section heading → Card title → Body → Caption → Metadata.

### Components

Reusable components, each with a "game UI" treatment (chunky border or notch, subtle inner shadow or foil sheen where appropriate):

* Top app bar · Bottom navigation (icon style: Poké Ball for Explore/home)
* Search bar with Poké Ball-shaped submit/search affordance
* Filter chips styled like in-game type filters
* Pokémon cards (trading-card proportions, holographic-style highlight on favourites/shinies)
* Type badges · HP-style stat bars with fill animation intent
* Buttons (primary = red/yellow game-UI button with slight bevel; secondary = outline)
* Favourite button (Poké Ball or heart with a "catch" micro-interaction)
* Caught toggle (Poké Ball fills/closes when marked caught)
* Segmented controls · Tabs · Progress rings (Pokédex completion) · Skeleton loaders · Empty/error states · Dialogs · Bottom sheets · Toggle switches · Settings rows

Spacing/system: 8px grid, 12–20px corner radius on soft UI elements but sharper/faceted edges allowed on "game card" elements, 48dp+ touch targets, consistent shadow + glow system (glows used for shiny/rare states).

---

## 3. APP NAVIGATION

Bottom navigation, four destinations, icons with a game-UI feel (not generic outline icons):

**Explore** (Poké Ball icon) · **Collection** (Pokédex icon) · **Compare** (VS icon) · **Guide** (type-wheel icon)

Settings via top-right icon. Navigation stays simple — the personality lives in the visuals, not in extra taps.

---

## 4. SCREEN 1 — SPLASH

A premium, cinematic splash: Pokémon Explorer logo animates in with a Poké Ball opening/glow effect, tagline "Discover. Compare. Explore." fades in, subtle animated background texture (silhouette Poké Balls or particle sparkle), then transitions into Explore. Describe the intended motion beat even though the deliverable is static frames (e.g. "Poké Ball opens, light burst, logo settles").

---

## 5. SCREEN 2 — HOME / EXPLORE

The hero screen — most visually ambitious in the app.

**Top:** Pokémon Explorer title, settings icon, search bar ("Search Pokémon...").

**Hero section:** A large featured-Pokémon card with big artwork on a type-coloured gradient background, name, Pokédex number, type badges, short blurb, "View Details" button — plus a visually distinct **Random Discovery** button (Poké Ball icon, feels like a "spin/catch" action, maybe a subtle glow or pulse to invite tapping).

**Filters:** Horizontal chips — All / Type / Generation — styled like in-game filter tabs.

**Pokémon grid:** Two-column trading-card-style grid. Each card: artwork on a soft type-gradient backing, Pokédex number, name, type badges, favourite icon, caught indicator (small Poké Ball, filled when caught). Cards for shiny/favourited Pokémon get a subtle holographic sheen treatment. Include skeleton loading state as cards stream in.

---

## 6. SCREEN 3 — SEARCH RESULTS

Back button, search field, filter button, result count ("24 Pokémon found"). Same trading-card result rows as Explore. Empty state: friendly illustration, "No Pokémon found" / "Try searching by name or Pokédex number." / "Clear Search" button.

---

## 7. SCREEN 4 — POKÉMON DETAILS

The richest screen — should feel like flipping to a Pokémon's page in the Pokédex.

**Top bar:** Back, favourite, more/options.

**Hero:** Large artwork centered on a full-bleed type-colour gradient panel, #025 Pikachu, type badge(s), and a **Normal | Shiny** toggle — switching should feel like a card-flip/shimmer, not a plain swap.

**Physical info:** Height / Weight in two compact stat cards.

**Abilities:** Compact chip/card row (Static, Lightning Rod).

**Base Stats:** HP-bar-style horizontal bars (colour intensity or fill animation cue for high stats), numeric values, and a **Total Base Stats** figure with strong visual weight.

**Moves:** Compact list, manageable subset.

**Actions:** Large, game-styled buttons — ❤️ Favourite · ✓ Mark as Caught (Poké Ball closes) · ⚔ Compare.

---

## 8. SCREEN 5 — COMPARE POKÉMON

Header "Compare Pokémon." Pokémon A **VS** Pokémon B, each with artwork on their own type-gradient half, name, number, type badges — the VS should feel like a battle-intro moment (bold diagonal split, "VS" treated as a graphic element, not just text).

**Stat table:** Side-by-side HP/Attack/Defense/Sp. Atk/Sp. Def/Speed with the higher value visually emphasised (colour, glow, or bar length) per row. Total stat comparison at the bottom (e.g. Pikachu — 320 vs Charizard — 534) with a bar-style visual.

**Controls:** "Change Pokémon A" · "Change Pokémon B" · "Back to Details."

---

## 9. SCREEN 6 — MY COLLECTION

A gamified dashboard. Header "My Collection." Large progress card up top — **Pokédex Progress: 152 / 1025 (15%)** — as a circular progress ring styled like a Poké Ball filling in, or a horizontal HP-style bar.

Tabs: **Favourites | Caught**. Grid of trading-card-style entries (artwork, name, number, caught checkmark). Empty favourites state: small illustration, "No favourites yet" / "Tap the heart icon on any Pokémon to add it here."

---

## 10. SCREEN 7 — TYPE GUIDE

Header "Type Guide," "Select a type" field. All 18 types shown as bold type-coloured cards (like in-game type icons, not flat chips). On selection: **Strong Against / Weak Against / Resistant To / Weak To**, each with a clear symbol (✓ Strong · × Weak · ◆ Resistant) — easy to scan at a glance. Tapping a type opens a filtered Pokémon list of that type.

---

## 11. SCREEN 8 — SETTINGS

Clean but on-brand settings screen (game-UI row style, not default OS list):

**Appearance** — Theme (System/Light/Dark) as a segmented control.
**Data** — Search History, Clear Cache, Clear Search History, Reset Collection.
**Preferences** — Last Viewed Pokémon, Notifications (if applicable).
**About** — App name, Version 1.0.0, PokéAPI attribution, subtle footer.

---

## 12. LOADING STATES

Skeleton shimmer placeholders (no generic spinners) for: Pokémon cards, details, stats, collection, search results. Consider a Poké Ball "spin" loader as a signature moment for full-screen loads only.

---

## 13. ERROR STATES

**No Internet:** Friendly illustration, "We couldn't connect to PokéAPI." / "Check your connection and try again." / "Try Again" button, plus "View Cached Data" if available.
**Pokémon Not Found:** "No Pokémon was found." / "Try Another Search."
**API Error:** "Pokémon information is temporarily unavailable." / "Try Again."

---

## 14. EMPTY STATES

Polished empty states (no favourites, no caught Pokémon, no search results, no cached Pokémon) — each with a small on-brand illustration, short message, explanation, and one clear primary action.

---

## 15. INTERACTION DESIGN

Map and annotate motion intent for each flow, since the anime/game-UI direction depends on it:

* Explore → tap card → Details (card "lifts" into detail view)
* Search → Search Results → Details
* Random Discovery → Poké Ball spin/catch beat → random Details
* Favourite → heart/Poké Ball micro-animation
* Caught → Poké Ball closes, Collection progress ring increments
* Details → Compare → select Pokémon → VS reveal
* Collection item → Details
* Type Guide → select type → type detail → filtered Pokémon list
* Settings → theme selection

---

## 16. RESPONSIVE AND ACCESSIBLE DESIGN

Design for small/standard/large Android phones. Minimum 48dp touch targets, strong contrast even against gradient/type-colour backgrounds, never rely on colour alone (pair type colour with text/icon), support dynamic text sizes where practical, clear focus states.

---

## 17. FIGMA FILE ORGANISATION

**01 — Design System** (colours, typography, icons, buttons, cards, inputs, badges, stat components)
**02 — Components** (variants)
**03 — User Flows** (navigation diagrams, interaction/motion notes)
**04 — Screens** (final mobile screens)
**05 — Prototype** (interactive connections, with motion notes attached to key transitions)

---

## 18. FINAL SCREEN SET

1. Splash 2. Explore/Home 3. Search Results 4. Pokémon Details 5. Details — Shiny State 6. Compare Pokémon 7. Pokémon Selection for Compare 8. Collection — Favourites 9. Collection — Caught 10. Type Guide 11. Type Guide — Selected Type 12. Settings 13. Empty Search 14. Empty Collection 15. Offline/Error State 16. Loading/Skeleton State

---

## 19. OVERALL UX GOAL

The app should feel like a real Pokémon companion product — one built by people who love the games and anime — not an academic exercise or a generic template with red-and-yellow branding. Prioritise: **fast discovery → striking Pokémon presentation → useful information → personal collection → comparison → battle reference**, all wrapped in a game-UI energy that makes routine actions (favouriting, catching, comparing) feel satisfying.

Don't overcrowd screens — use progressive disclosure. Pokémon artwork stays the visual hero; data stays highly readable even against bold colour and gradient treatments.

End-to-end journey to prototype: **Open App → Explore → Search → View Pokémon → Favourite/Catch → Compare → View Collection → Use Type Guide → Settings.**