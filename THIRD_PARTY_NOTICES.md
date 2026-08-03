# Third-party notices

Pace uses open-source Android libraries distributed through Google Maven and Maven Central. The application is built with Kotlin, AndroidX Core, Activity, Lifecycle, Navigation 3, Jetpack Compose and Material 3, Room, DataStore, WorkManager, Glance, AndroidX Browser, Protocol Buffers, Kotlin Serialization, and OkHttp, plus their transitive dependencies.

- AndroidX, Jetpack Compose, Material, and WorkManager components are generally licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).
- Kotlin and Kotlin Serialization are provided by JetBrains under the [Apache License 2.0](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt).
- OkHttp is provided by Square under the [Apache License 2.0](https://github.com/square/okhttp/blob/master/LICENSE.txt).
- Protocol Buffers is provided by Google under its [BSD 3-Clause license](https://github.com/protocolbuffers/protobuf/blob/main/LICENSE).

Optional, user-initiated connected features include:

- [Ollama Cloud](https://ollama.com/) for the optional AI coach. The user supplies their own API key; requests are governed by Ollama's terms and privacy policy, and the models served through it carry their own upstream licenses.
- [Original 2048](https://github.com/gabrielecirulli/2048) by Gabriele Cirulli, MIT licensed, opened as an external web experience.
- [Simon Tatham's Portable Puzzle Collection](https://www.chiark.greenend.org.uk/~sgtatham/puzzles/) opened as an external web experience; its source distribution contains the applicable per-file free-software notices.
- [Smokefree.gov](https://smokefree.gov/) (US National Cancer Institute) for reviewed craving guidance, opened in a browser Custom Tab.

Body-recovery milestone timings follow published cessation guidance from the US Centers for Disease Control and Prevention and the UK National Health Service.

External pages are not bundled into the APK and remain governed by their respective terms and privacy notices. Maven artifacts may also include their own `META-INF` license materials.
