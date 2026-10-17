# Android header removal reproducer

A before/after comparison for [the header removal fix](https://github.com/software-mansion/react-native-screens/pull/3777), rebased onto `react-native-screens` main at `561feef37a2713bafd3652f8ed140b452e26ccbb`.

The controlled scenario starts removal on an actual ancestor `Screen`, then changes a descendant native-stack header through React Navigation and Fabric. The baseline throws `The specified child already has a parent` in `ScreenStackHeaderConfig.onUpdate`. The comparison patch guards header rebuilding during removal and completes recorded child transitions before their root detaches.

**This is a controlled native lifecycle reproduction.** The app host deliberately calls `Screen.startRemovalTransition()` through an adb broadcast. It does not inject a failure into the library. Ordinary two-Back navigation and tab replacement remain separate scenarios; success in this controlled test does not establish that every production timing race is fixed.

## Versions and comparison

- React Native 0.87.0, Fabric/Hermes, React 19.2.3.
- React Navigation native 7.3.10, native-stack 7.18.2.
- Android compile SDK 37, target SDK 36, NDK 27.1.12297006, Kotlin 2.3.21, Gradle 9.4.1.
- `vendor/react-native-screens` is a public submodule pinned to the main SHA above.
- Both variants apply `patches/tabs-prerequisites.patch`, the three native tab files from [the selected-tab removal work](https://github.com/software-mansion/react-native-screens/pull/4904) at `0206c07831b7c84fc9a358ccc21381161cdf5ff3`. That work includes the [logical tab child fix](https://github.com/software-mansion/react-native-screens/pull/4812). These prerequisites prevent unrelated tab failures from blocking the tab scenario.
- Only **after** applies `patches/header-removal.patch`. Its three library files match the rebased PR implementation. The host, JS, dependencies and tab prerequisites are the same in both variants.

## Build and install

Requires Node 22.11+, JDK 17+, Android SDK 37/build-tools 37.0.0, SDK 36 for the API 36 emulator, and the NDK above. Set `ANDROID_HOME` to your SDK. Do not disable Android animation scales.

```sh
git clone --recurse-submodules https://github.com/l2hyunwoo/rnscreens-header-removal-repro.git
cd rnscreens-header-removal-repro
npm ci --ignore-scripts
bash scripts/build.sh before
npm start -- --reset-cache
```

In another terminal:

```sh
adb -s <serial> install -r android/app/build/outputs/apk/debug/app-debug.apk
adb -s <serial> reverse tcp:8081 tcp:8081
adb -s <serial> shell am start -n com.screens3777repro/.MainActivity
```

For the patched version, run `bash scripts/build.sh after`, install the newly built APK and repeat. Native code changes require rebuilding and reinstalling. The build script cleans the screens native build to avoid stale Kotlin incremental output when switching patches. `bash scripts/build.sh after Release` builds a bundled release APK signed with the template debug key, for local verification only.

`set-variant.py` applies/reverses the documented patch after checking the expected source state; it does not reset unrelated edits. The submodule intentionally appears modified while patches are applied.

## Controlled failure and recovery

1. Choose **Nested stack and two Back events**.
2. Open the middle stack, inner stack, then Form. Wait for transitions to settle.
3. Start the ancestor's native removal transition:

   ```sh
   adb -s <serial> shell am broadcast -a com.screens3777repro.BEGIN -p com.screens3777repro
   ```

4. Press **Refresh header after BEGIN**. This changes `headerTintColor` through React Navigation and Fabric, while the ancestor removal flag and native child transitions are active.
5. Before: the app shows the reported parent exception at `ScreenStackHeaderConfig.onUpdate`.
6. After: the screen stays usable and the revision reaches 1. Finish the controlled transition:

   ```sh
   adb -s <serial> shell am broadcast -a com.screens3777repro.END -p com.screens3777repro
   ```

7. Press the refresh button again. The revision reaches 2. Press system Back, open Form again, and verify input and header rendering.

The broadcast receiver exists only in this test host. `BEGIN` finds a header with multiple ancestor Screens and starts removal on its outermost Screen. `END` calls the matching cleanup. The library patch contains neither receiver nor test-only fault injection. Back event times are logged under `ReproBack`; controlled start/end under `ReproTransition`.

Automate the exact sequence and retain app-only logcat, UI assertions and the first run's recording:

```sh
python3 scripts/run-controlled.py --serial <serial> --label before --count 10 --record
# Rebuild/reinstall after, then:
python3 scripts/run-controlled.py --serial <serial> --label after --fixed --reuse --count 30 --record
```

## Ordinary navigation scenarios

`R1.tsx` also provides the reported three nested native stacks with LEFT `Image` and CENTER `View/Text`. From Form, inject two system Back events in one device-side shell:

```sh
adb -s <serial> shell 'input keyevent 4; sleep 0.392; input keyevent 4'
```

Calibrate the sleep against actual event timestamps. It is not the Back dispatch interval. The earlier report used RN 0.85.3 / screens 4.25.2 / native-stack 7.16.0 / API 36; this repository tests current main with RN 0.87.0. Those environments must not be conflated.

`R2.tsx` provides native Tabs containing legacy stacks and LEFT/CENTER/RIGHT headers. Exercise selected-tab replacement, root replacement by a loader, and both together. Delays are selectable. Verify both tabs, their route labels, header revisions and press counters after each operation. A combined React batch unmounts the old host and mounts the new set after the loader; the swap-only action tests removal while the host remains mounted.

## Before / after and verification

**This comparison uses a controlled native removal start on actual production classes.** The test host calls `Screen.startRemovalTransition()` on an ancestor Screen. A normal JS header prop update then reaches `ScreenStackHeaderConfig.onUpdate()` through Fabric.

Both builds use RN 0.87.0, Fabric/Hermes, native-stack 7.18.2 and the same host, JS and dependencies. Both include the native tab prerequisites from [the selected-tab removal work](https://github.com/software-mansion/react-native-screens/pull/4904). Those prerequisites include [the logical tab child fix](https://github.com/software-mansion/react-native-screens/pull/4812). They are test setup and are not part of this PR's library patch.

| Before: current main | After: rebased fix |
| --- | --- |
| <video src="https://github.com/user-attachments/assets/de9f5dbe-d5b1-4526-bbb5-1e2babdc10f0" controls="controls"></video> | <video src="https://github.com/user-attachments/assets/a06d459a-3fe1-45ad-b8fc-a41424bf83c8" controls="controls"></video> |
| Header refresh throws `The specified child already has a parent` in `ScreenStackHeaderConfig.onUpdate`. | Header refresh succeeds. Cleanup, another refresh, Back and re-entry remain usable. |

[Before MP4](https://github.com/user-attachments/assets/de9f5dbe-d5b1-4526-bbb5-1e2babdc10f0) · [After MP4](https://github.com/user-attachments/assets/a06d459a-3fe1-45ad-b8fc-a41424bf83c8)

The videos were recorded on a physical SM-S948N, Android 17 / API 37. All three animation scales were 1. The baseline is main `561feef37a2713bafd3652f8ed140b452e26ccbb`; the fixed library is `77ea9de22e2b33b9b73e4f4e56a174addac801a6`. The app source is commit `c5bb24b` in the [public reproducer](https://github.com/l2hyunwoo/rnscreens-header-removal-repro).

| Check | Observed result |
| --- | --- |
| Physical API 37, baseline | 10/10 completed trials produced the same header parent exception. Each trial launched a fresh app. |
| Physical API 37, fixed | 30/30 consecutive start/update/end cycles passed in one app session. Header revisions reached 60. Back and re-entry passed on the first and last cycles. |
| API 36 arm64 emulator, debug | Baseline failed with the same exception; the fixed build passed the same controlled sequence. |
| API 36 arm64 emulator, release | The bundled fixed APK passed controlled removal, refresh, cleanup, Back and re-entry. |
| Native tabs, API 36 release | 10 selected-tab swap/root-loader operations passed. LEFT/CENTER/RIGHT headers, route labels and both content/header buttons remained usable. |
| Framework checks, API 35 and 36 | 38 test cases passed: 28 intended-flow regression cases and 10 boundary characterizations. The latter deliberately document counterexamples under injected ordering. |
| Source and build checks | Six extracted methods exactly matched the rebased source. Debug and release APKs built. Scoped Spotless/ktlint 1.3.0 and focused TypeScript/ESLint checks passed; ESLint reported two menu inline-style warnings. |

One additional baseline setup attempt stopped before removal injection because the UI snapshot did not match. It is excluded from the 10 completed trials.

The framework fixture omits RN, Fragment and native Tabs integration. Its ordering counterexamples do not establish production reachability. See the [fixture scope and limits](https://github.com/l2hyunwoo/rnscreens-header-removal-repro/tree/main/framework-tests).

**Remaining limit:** ordinary two-Back navigation and the reported dynamic-tab production crash have not independently failed in these minimal apps. Ten ordinary Back-pair runs on the earlier RN 0.85.3 / screens 4.25.2 setup returned normally on API 37. The controlled comparison proves the targeted native lifecycle condition and its guard; it does not verify every production timing path.

<details>
<summary>Recorded APK SHA-256 values</summary>

```text
before debug  7ecb3c63fb536b2181be85a2ca4e505f23acb015eeda70c331d266ef140cc22c
after debug   05748ccbfe78d187e025df653eaf1efc5bc2e27734b4715761249afc561f181a
after release e9fb71b9a05d0ad0e49d04fe6c47bb8811582312bd8e5f5e40a85df6c0a35a9a
```

</details>
