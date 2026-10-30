# M3 Launcher

Android launcher: Kotlin + Jetpack Compose + Material 3 Expressive (spring motion, MaterialShapes, dynamic color).

## Run
1. Open this folder in Android Studio (Ladybug or newer), let Gradle sync.
2. Run on a device/emulator (API 26+, dynamic color needs 31+).
3. Press Home, choose "M3 Launcher" and "Always" (or Settings > Set as default home app).

If a dependency version is rejected, bump `compose-bom`, AGP, Kotlin in `build.gradle.kts` / `app/build.gradle.kts`.
Material 3 Expressive APIs need `material3` 1.4.0 or newer.

## Fonts
- Settings > Font > Import: pick any .ttf/.otf (variable fonts like Google Sans Flex work, weights follow the font's wght axis).
- Or drop files in `app/src/main/assets/fonts/` before building; they show up in the list.
- "Google Sans (if on device)" uses the system `google-sans` family (Pixels); falls back to default elsewhere.
- Google Sans itself is proprietary: only use files you're licensed to ship.

## Features
Home pager + dock, long-press popup (Home Settings / Rearrange / Wallpaper & Style), drag to rearrange (home <-> dock, edge-hold to change page, drop on Remove), jiggle edit mode,
swipe-up drawer with spring physics, search, A-Z rail, grid/list, hide apps, app shortcuts menu, app info / uninstall, swipe down for notifications,
icon shapes (Circle, Squircle, Cookie, Clover, Flower), themed icons, icon size, labels, grid size, dock size, wallpaper dim,
light/dark/dynamic/seed themes, custom fonts, text scale, live app install/remove updates, Home button resets to page 1.

## Not included yet
Widgets, folders, notification badges, drag from drawer to home (use "Add to home" in the app menu).
