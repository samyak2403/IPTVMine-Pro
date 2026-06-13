# IPTVMine Pro

> [!WARNING]
> **This project is currently under construction.** Features are being actively developed and some parts of the codebase might be unstable.

IPTVMine Pro is a powerful and versatile Android IPTV application built with modern technologies. It allows users to stream Live TV, Movies, and TV Shows by integrating custom provider sources (M3U or Vega-compatible extensions).

## 🚀 Features

- **Live TV Streaming**: Support for M3U playlists with category filtering.
- **VOD Support**: Integrated movie and series catalogs through provider extensions.
- **Provider Management**: Easily add, remove, and manage multiple IPTV providers.
- **Global Search**: Search for channels and movies across all active providers.
- **Modern UI**: Built entirely with Jetpack Compose for a smooth, responsive, and teal-themed user experience.
- **Advanced Player**: Powered by AndroidX Media3 (ExoPlayer) with support for HLS, DASH, RTSP, and more.
- **Extension System**: Support for Vega-style JavaScript/TypeScript provider extensions for dynamic content scraping.
- **Leanback Support**: Basic integration for TV and Cast functionality.

## 🛠 Tech Stack

- **Language**: [Kotlin](https://kotlinlang.org/)
- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose)
- **Navigation**: [Compose Navigation](https://developer.android.com/jetpack/compose/navigation)
- **Media Playback**: [AndroidX Media3](https://developer.android.com/guide/topics/media/media3) (ExoPlayer)
- **Networking**: [OkHttp](https://square.github.io/okhttp/) & [Gson](https://github.com/google/gson)
- **Image Loading**: [Coil](https://coil-kt.github.io/coil/)
- **Animations**: [Lottie for Compose](https://github.com/airbnb/lottie-android)
- **Architecture**: MVVM with ViewModel and State management.

## 📂 Project Structure

- `app/`: Main application module containing UI screens, ViewModels, and provider logic.
- `Player/`: Dedicated module for the video playback activity and Media3 implementation.
- `VEGA_DOCS.md`: Comprehensive documentation for the Vega extension system and developer guide.

## 🏁 Getting Started

### Prerequisites

- Android Studio Meerkat (or newer)
- JDK 17 or 11
- Android SDK 24+ (Minimum) / 36+ (Target)

### Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/samyak2403/IPTVMine-Pro.git
   ```
2. Open the project in Android Studio.
3. Sync Project with Gradle Files.
4. Build and run the `app` module on your device or emulator.

## 📺 Adding Providers

### M3U Providers
1. Open the app and navigate to **Settings > Manage Providers**.
2. Tap the **+** button to add a new provider.
3. Enter your M3U URL or select a local file.

### Vega Extensions
1. Check [VEGA_DOCS.md](VEGA_DOCS.md) for detailed instructions on how to build and add Vega-compatible provider extensions.
2. Extensions can be managed under **Settings > Extensions**.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details (if available).

---
*Built with ❤️ for the IPTV community.*
