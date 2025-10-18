# COMP2008 MAD Assignment 2

Part A: Build a Pocket Library application 
- Amanda Huyen Nguyen, 22223850
- Raunak Arora, 21355176 

## Libraries Used
- Jetpack Compose UI & Material3: UI toolkit and components (includes NavigationRail, TopAppBar, etc.).
- AndroidX Lifecycle/ViewModel: state holder for screen logic and async work.
- Kotlin Coroutines/Flow: background work and reactive streams.
- Retrofit + OkHttp: type-safe HTTP client to call the OpenLibrary API.
- Room: local database for offline library storage.
- Coil: image loading/caching with Compose AsyncImage.
- DataStore (Preferences): persistence of last query and scroll state.

## Additional third-part libraries
- Firebase Firestore: cloud sync for saved books, provides offline queing and automatic merge, enabling "offline save to auto upload on reconnect" without maintaining a custom backend.
- Firebase Auth (Anonymous): per-device/user-identity so Firestore can isolate each user's library.

## Benefit to the Project
- Delivers an offline-first experience (Room + DataStore) with reliable, low-effort cross-device sync,
- Efficient image loading (Coil),
- Responsive, accessible UI (Compose/Material3) that keeps query and scroll across rotation/relaunch.
