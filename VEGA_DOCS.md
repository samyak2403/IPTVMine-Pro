# Vega App Documentation

This document compiles the official Vega documentation guides for reference in this project. It includes setup instructions, provider extensions, troubleshooting, and advanced integration.

---

## Table of Contents
1. [Download and Install Vega](#1-download-and-install-vega)
2. [Add Provider Sources](#2-add-provider-sources)
3. [Build Your Own Provider (Developer Guide)](#3-build-your-own-provider-developer-guide)
4. [Bypass ISP Blocking](#4-bypass-isp-blocking)
5. [External Downloader and Player](#5-external-downloader-player)

---

## 1. Download and Install Vega

Download Vega only from official release channels to ensure security and stability.

### Recommended Builds
* **Universal Build**: Works across all supported Android CPU architectures. Recommended if APK size is not a constraint.
* **arm64-v8a**: Optimized for modern 64-bit ARM devices (most modern phones and tablets).
* **armeabi-v7a**: Optimized for older 32-bit ARM devices.

### How to Find Your Device Architecture
On your Android device, install a system info tool (such as CPU-Z, Droid Hardware Info, or AIDA64) and check the **CPU ABI** or **Architecture** property.

---

## 2. Add Provider Sources

Vega allows extending its content libraries using official or custom provider sources.

### Step-by-Step Integration
1. Open the Vega app and navigate to **Settings > Provider Manager > Available Providers**.
2. Tap the **+** (plus) floating action button.
3. Enter the provider repository identifier (e.g., `vega-org` for the official repository) or a custom provider URL.
4. Press **Confirm** to load and register the source.

---

## 3. Build Your Own Provider (Developer Guide)

Vega supports custom provider extensions written in JavaScript/TypeScript. Developers can write custom scraper modules and test them locally.

### Provider Folder Structure
Each provider lives in its own dedicated directory under the `providers/` folder:
```
providers/
  myProvider/
    catalog.ts
    meta.ts
    posts.ts
    stream.ts
    episodes.ts (optional)
```

### Module Explanations

#### A. `catalog.ts`
Defines the main categories, filters, or genres displayed on the homepage.
* **Exports**:
  * `catalog`: An array of objects containing `title` (category name) and `filter` (identifier passed to `getPosts`).
  * `genres` (optional): An array of objects for genre-based navigation.
* **Example**:
  ```ts
  export const catalog = [
    { title: "Popular Movies", filter: "/category/popular-movies" },
    { title: "Latest TV Shows", filter: "/category/latest-tv-shows" }
  ];

  export const genres = [
    { title: "Action", filter: "/genre/action" },
    { title: "Drama", filter: "/genre/drama" }
  ];
  ```

#### B. `posts.ts`
Handles listing cataloged items and processing search queries.
* **Exports**:
  * `getPosts({ filter, page, providerValue, signal, providerContext })`: Fetches and returns an array of `Post` objects.
  * `getSearchPosts({ searchQuery, page, providerValue, signal, providerContext })` (optional): Fetches search results for a query.
* **Example**:
  ```ts
  import { Post, ProviderContext } from "../types";

  export const getPosts = async function ({
    filter, page, providerValue, signal, providerContext
  }: {
    filter: string;
    page: number;
    providerValue: string;
    signal: AbortSignal;
    providerContext: ProviderContext;
  }): Promise<Post[]> {
    // Custom scraping or API fetching logic here
    return [];
  };
  ```

#### C. `meta.ts`
Retrieves detailed metadata (synopsis, runtime, title, banner, seasons/episodes) for a selected item.
* **Exports**:
  * `getMeta({ link, providerContext })`: Returns an `Info` metadata object.
* **Example**:
  ```ts
  import { Info, ProviderContext } from "../types";

  export const getMeta = async function ({
    link, providerContext
  }: {
    link: string;
    providerContext: ProviderContext;
  }): Promise<Info> {
    return {
      title: "Example Title",
      synopsis: "A sample synopsis text.",
      image: "https://example.com/image.jpg",
      imdbId: "tt1234567",
      type: "movie",
      linkList: []
    };
  };
  ```

#### D. `stream.ts`
Extracts streaming video playbacks/sources from a selected post.
* **Exports**:
  * `getStream({ link, type, signal, providerContext })`: Returns an array of `Stream` resource objects.
* **Example**:
  ```ts
  import { Stream, ProviderContext } from "../types";

  export const getStream = async function ({
    link, type, signal, providerContext
  }: {
    link: string;
    type: string;
    signal: AbortSignal;
    providerContext: ProviderContext;
  }): Promise<Stream[]> {
    return [
      {
        server: "Direct Stream",
        link: "https://example.com/movie.m3u8",
        type: "m3u8",
        quality: "1080"
      }
    ];
  };
  ```

#### E. `episodes.ts` (Optional)
Handles lazy-loading episode lists for long-running series if they cannot be fully returned in `getMeta`.
* **Exports**:
  * `getEpisodes({ url, providerContext })`: Returns an array of `EpisodeLink` objects.

### Understanding `linkList` in `getMeta`
The `linkList` array handles season grouping or direct file attachments:
* **`episodesLink`**: If season episodes require a secondary fetch, provide `episodesLink`. When the user clicks the season, `getEpisodes` is invoked with this value.
* **`directLinks`**: If all episode resources are known upfront, bypass `getEpisodes` by listing them directly under `directLinks` with fields `title`, `link`, and `type`.

### testing CLI Tools
1. **End-to-End Testing**:
   ```bash
   npm run test -- <provider_name>
   ```
2. **Single Function Testing**:
   ```bash
   npm run test:provider <provider_name> <function_name>
   ```

### testing in App
1. Start the local dev server using `npm run auto`. Note the local IP and port provided.
2. In the Vega app project, open `src/lib/services/ExtensionManager.ts`.
3. Set the test parameters:
   ```ts
   private testMode = true;
   private baseUrlTestMode = "http://<your-local-ip>:3001";
   ```
4. Connect both your dev computer and your test device to the same Wi-Fi network. The app will now query your local code structure.

---

## 4. Bypass ISP Blocking

If a provider endpoint fails to load on your local Wi-Fi, your ISP may be resolving DNS blocks. Try these configurations:

### Option A: Configure Private DNS
1. Navigate to **Settings > Network & Internet**.
2. Select **Advanced > Private DNS**.
3. Choose **Private DNS provider hostname**.
4. Enter `one.one.one.one` (Cloudflare) and press **Save**.

### Option B: Cloudflare WARP
Install Cloudflare's **1.1.1.1 (WARP)** VPN layer to encapsulate request streams from ISP inspectors.

### Option C: Premium VPN
Use a verified VPN service. Note that some websites detect and reject commercial VPN IP blocks.

---

## 5. External Downloader and Player

If internal player elements fail, customize third-party apps:
1. Navigate to **Settings > Preferences**.
2. Enable external app selection hooks.
3. Recommended apps:
   * **VLC Media Player**: For HLS/M3U8 streams.
   * **Free Download Manager (FDM)**: For file downloads.
4. **Tip**: Long-press play or download components to open with external handler targets directly.
