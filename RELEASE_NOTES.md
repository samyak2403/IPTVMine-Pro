# IPTVmine Pro — Release Notes

## Version 1.1.3 (versionCode 13) — Material Black Theme Update

### 🌟 What's New

#### 🖤 Pure Material Black AMOLED Theme
- **True `#000000` AMOLED Experience**: Implemented a pure pitch-black theme designed specifically for OLED/AMOLED displays, maximizing power efficiency and providing high-contrast visual clarity.
- **Settings Toggle Switch**:
  - Added a new **Appearance** section in **Settings**.
  - Includes a modern Material 3 `Switch` to toggle **"Material Black Theme"** on and off instantly without requiring an application restart.
  - Persists preference locally using `ThemePreferenceManager` across app sessions.
- **All Screens Adapted**:
  - **Main Shell & Navigation**: Pure black Top App Bar and Bottom Navigation Bar with high-contrast active icons and sleek `#1E1E1E` dividers.
  - **Home Screen & TV**: Live channel cards and VOD movie sections styled with dark `#121212` card surfaces and subtle `#262626` borders.
  - **Category & Search**: Full black background and dynamic top bars in `CategoryScreen`, `CategoryMoviesScreen`, `SearchScreen`, and `MovieSearchScreen`.
  - **Media & Details**: Clean AMOLED styling for `MovieDetailScreen`, `VideoListScreen`, and `WatchHistoryScreen`.
  - **Settings & Management**: Dark theme consistency across `ProviderListScreen`, `DownloadsScreen`, `ExtensionsScreen`, `PairingScreen`, `AboutScreen`, `SupportScreen`, and `DonateScreen`.
  - **Shimmer Skeletons**: Dynamic dark shimmer placeholders (`#121212` to `#242424`) when loading content in Black Theme.
- **Edge-to-Edge System Bars**:
  - Status bar and navigation bar automatically switch to pure black with light icons in Material Black Theme.

---

### 🛠️ Improvements & Fixes
- **Dynamic State Flow**: Theme state updates propagate reactively across all Compose trees using `StateFlow` and `LocalIsBlackTheme`.
- **Contrast & Legibility**: Replaced hardcoded white container backgrounds and low-contrast dark text with adaptive `MaterialTheme.colorScheme` tokens (`onSurface`, `onSurfaceVariant`, `background`).
- **Resource Integrity**: Added localized strings in `res/values/strings.xml` for Appearance preferences and copy feedback.

---

### 📦 Previous Releases

#### Version 1.1.2 (versionCode 12)
- **Fixes**: Fixed "Decoder failed" playback error for 10-bit HEVC videos on devices lacking hardware Main 10 support by enabling FFmpeg software decoder fallback and graceful fallback dialog with external player option.

#### Version 1.0.9 (versionCode 9)
- **New**: Added Donate screen in Settings with Buy Me a Coffee and UPI payment options.
- **Fixes**: UPI package visibility on Android 11+, gesture bar insets padding, and dark mode readability.

#### Version 1.0.7 (versionCode 7)
- **Fixes**: Raised playlist response size cap from 10MB to 100MB for large IPTV playlists.
- **Improvements**: Updated Add Provider help guide.
