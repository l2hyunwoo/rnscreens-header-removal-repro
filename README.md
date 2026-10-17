# Android header removal reproducer

A before/after comparison for [the header removal fix](https://github.com/software-mansion/react-native-screens/pull/3777), rebased onto `react-native-screens` main at `561feef37a2713bafd3652f8ed140b452e26ccbb`.

Ordinary navigation reproduces the crash without any test hook: a native-stack route with `getId` and a nested native-stack header is moved to the top of the stack and back. The baseline throws `The specified child already has a parent` in `ScreenStackHeaderConfig.onUpdate`, called from `onAttachedToWindow`. The comparison patch guards header rebuilding during removal and completes recorded child transitions, including children that already left their parent's child array.

The repository also keeps an earlier **controlled** experiment, in which the app host calls `Screen.startRemovalTransition()` through an adb broadcast. That experiment forces the transition and is labeled as such below. The reported two-Back and dynamic-tab paths did not fail on the test device; their measurements are in the verification section.

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

## Natural reproduction: moved screen with a nested header

1. Install a release or debug build of the variant to test.
2. Choose **Move screen with nested header**.
3. Press **Open Flow B**, **Open Flow A**, then **Open Flow B**. Each press uses `navigate` with a route `getId`; the second and third presses move an existing route to the top of the stack.
4. Before: the third press ends the process with the header parent exception. After: Flow B shows `Flow B visit 3` in the header and the content.

**Move existing screen to top** is the same flow without the nested stack. It does not produce the header exception. In both variants, a fourth move leaves the stack blank; that is a separate existing issue (see below).

```sh
python3 scripts/run-move.py --serial <serial> --label before --entry 'MOVE SCREEN WITH NESTED HEADER' --name FLOW --hops 3 --repeat 10 --out evidence/move
```

The script launches a fresh process per run, taps only app buttons and records the result with app logcat.

## Controlled failure and recovery (forced transition)

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

## Reported navigation scenarios

`R1.tsx` also provides the reported three nested native stacks with LEFT `Image` and CENTER `View/Text`. From Form, inject two system Back events in one device-side shell:

```sh
adb -s <serial> shell 'input keyevent 4; sleep 0.392; input keyevent 4'
python3 scripts/run-two-back.py --serial <serial> --label back --gaps 380,400,420,440,460,480,500 --repeat 2 --out evidence/back
```

The shell sleep is not the Back interval. `MainActivity.onBackPressed` logs `ReproBack: callback uptime=...`, and a JS `BackHandler` listener logs `ReproBack js ... perfMs=...` on the same clock. The script reports both delivered intervals. The two-Back report used RN 0.85.3 / screens 4.25.2 / native-stack 7.16.0 / API 36; this repository tests current main with RN 0.87.0. Those environments must not be conflated.

`R2.tsx` provides native Tabs containing legacy stacks and LEFT/CENTER/RIGHT headers. Exercise selected-tab replacement, root replacement by a loader, and both together. Verify both tabs, their route labels, header revisions and press counters after each operation. The controls select:

- **Loader delay:** 0, 100, 450, 1000 or 3000 ms.
- **Root offset:** the root change runs before (negative) or after (positive) the tab change, from −450 to +450 ms. At 0 ms both happen in one React batch.
- **Root mode:** `unmount` deletes the tab host and mounts a new one after the loader. `hide` keeps the host mounted under `display: none`. `wrap` keeps it mounted and gives its wrapper an opacity, so Fabric moves the same native views into a new parent. `hide` and `wrap` detach and re-attach the existing native views.

```sh
python3 scripts/run-tab-timing.py --serial <serial> --label tabs --modes unmount,hide,wrap --delays 0,450,3000 --cycles 3 --out evidence/tabs
```

## Before / after and verification

### Natural reproduction: a moved screen with a nested header

This path uses no test hook and no forced transition. The only input is taps on app buttons.

`R3.tsx` has an outer native stack. Its `Flow` route uses `getId` and renders an inner native stack. The inner screen header has a LEFT `Image` and a CENTER `View`/`Text`, and its title shows the route params.

1. Choose **Move screen with nested header**.
2. Press **Open Flow B**, **Open Flow A**, then **Open Flow B**.

With `getId`, `navigate` moves the existing route to the top of the stack. Fabric mounts this move as Remove + Insert of the same native `Screen`. Temporary logging (not in the compared builds) showed this sequence:

1. When Flow A moves above Flow B, the Remove of Flow B calls `startRemovalTransition()` on Flow B. This starts view transitions on the toolbar subviews of the inner header.
2. The inner stack views then detach, and Flow B's `endRemovalTransition()` runs. It walks only the current children, so it does not end the toolbar subview transitions. The toolbar keeps them as parents.
3. When Flow B returns to the top, the inner header attaches again. `onAttachedToWindow()` calls `onUpdate()`, and `toolbar.addView()` throws because a subview still has the toolbar as its parent.

The patch records each parent/child pair when a transition starts and ends the pairs in reverse order. This includes children that already left the current child array.

| Before: current main | After: rebased fix |
| --- | --- |
| <video src="https://github.com/user-attachments/assets/fc35937d-8069-4a42-8dc9-97ea06a7be2d" controls="controls"></video> | <video src="https://github.com/user-attachments/assets/959a9dd9-98c6-407c-a4a4-88cdd5111683" controls="controls"></video> |
| The third move ends the process with `The specified child already has a parent` in `ScreenStackHeaderConfig.onUpdate`, called from `onAttachedToWindow`. | The third move shows Flow B. The header and the content both show `Flow B visit 3`. |

[Before MP4](https://github.com/user-attachments/assets/fc35937d-8069-4a42-8dc9-97ea06a7be2d) · [After MP4](https://github.com/user-attachments/assets/959a9dd9-98c6-407c-a4a4-88cdd5111683)

| Check (physical API 37, release) | Before: current main | After: rebased fix |
| --- | --- | --- |
| Nested header, six moves requested | 10/10 runs ended at the third move with the header parent exception | 10/10 runs had no header exception |
| Nested header, three moves | 1/1 recorded run failed in the same way | 20/20 runs passed. Header and content showed `Flow B visit 3` |
| Header without a nested stack, six moves requested | 5/5 runs had no header exception | 5/5 runs had no header exception |

Each run launched a fresh app process. Automation: `python3 scripts/run-move.py --serial <serial> --label <label> --entry 'MOVE SCREEN WITH NESTED HEADER' --name FLOW --hops 3 --out <dir>`.

**Separate existing issue:** in both builds, the fourth move leaves the stack blank. Logcat shows three soft exceptions `addViewAt: cannot insert view [...]: View already has a parent [...] ScreensCoordinatorLayout` for the moved `Screen`. The same result occurs before the patch in the variant without a nested stack, where no header exception occurs. This PR does not change that behavior. It matches the Android symptom in [the report about repeated `getId` transitions](https://github.com/software-mansion/react-native-screens/issues/2578).

<details>
<summary>Before: fatal exception from the recorded run</summary>

```text
java.lang.IllegalStateException: The specified child already has a parent. You must call removeView() on the child's parent first.
  at android.view.ViewGroup.addViewInner(ViewGroup.java:5776)
  at android.view.ViewGroup.addView(ViewGroup.java:5595)
  at android.view.ViewGroup.addView(ViewGroup.java:5535)
  at android.view.ViewGroup.addView(ViewGroup.java:5507)
  at com.swmansion.rnscreens.legacy.ScreenStackHeaderConfig.onUpdate(ScreenStackHeaderConfig.kt:401)
  at com.swmansion.rnscreens.legacy.ScreenStackHeaderConfig.onAttachedToWindow(ScreenStackHeaderConfig.kt:202)
  at android.view.View.dispatchAttachedToWindow(View.java:24974)
```

</details>

The videos were recorded on a physical SM-S948N, Android 17 / API 37. All three animation scales were 1. Both builds are release builds of app commit `37e6539` with RN 0.87.0, Fabric/Hermes and native-stack 7.18.2. The baseline is main `561feef37a2713bafd3652f8ed140b452e26ccbb`; the fixed library is `77ea9de22e2b33b9b73e4f4e56a174addac801a6`. Both include the tab prerequisites described below. Do Not Disturb was on during recording. The videos are edited for privacy only: launcher frames before the app starts are cut, the status bar is masked, and in the before video the frames after the process exit, which show the device home screen, are replaced with two seconds of black.

### Reported paths that did not fail here

These scenarios also use only system input or app buttons. The host logs the delivery time of each Back in `MainActivity.onBackPressed`. A JS `BackHandler` listener logs the time on the same clock. Shell `sleep` values are not used as Back intervals.

| Scenario | Builds and conditions | Result |
| --- | --- | --- |
| Two system Back presses, `R1.tsx` | Current main, release. Delivered Back intervals 1–316 ms (8 runs). | 8/8 returned to the middle stack |
| Same | Current main with temporary logging. Delivered intervals 373–540 ms (46 runs). Where the first pop transition ended, the second Back arrived between 21 ms before and 54 ms after that end. In the other runs, the second Back arrived first and the first pop transition did not end. | 46/46 returned to the middle stack. No header update ran under a removing ancestor. |
| Same, reported versions | RN 0.85.3, screens 4.25.2, native-stack 7.16.0, release. Delivered intervals 392–514 ms (51 runs). | 51/51 returned to the middle stack |
| Selected tab removal with root removal, `R2.tsx` | Current main with temporary logging. The root is unmounted, hidden with `display: none`, or moved into a new wrapper view. Loader time 0, 450 or 3000 ms. Root change offset from −450 to +450 ms relative to the tab change. 35 cases, 83 tab changes. | No header exception. 8 cases ended earlier with a different tab exception (below). |

The reports used Android 16 / API 36; these runs used Android 17 / API 37. The two-Back report used 4.25.2. Current main also includes [synchronous removal marking](https://github.com/software-mansion/react-native-screens/pull/4444), which makes `maybeUpdate()` skip a screen that the same mount removes. The 4.25.2 runs did not fail either, so these results do not show which difference matters.

In 8 of the 35 tab cases, the app ended with `[RNScreens] No selected tab present` from `TabsContainer.onAttachedToWindow()` → `restoreNavigationStateIfNeeded()`. This happened when the selected tab was removed while the tab host was detached. Six of the eight cases changed the tabs and the root in the same React batch. This is a tab restore problem. The current head of [the selected-tab removal work](https://github.com/software-mansion/react-native-screens/pull/4904) does not cover it, and this PR does not change it.

### Earlier controlled experiment

Before the natural path was found, a controlled experiment started removal on an ancestor `Screen` from a test-only broadcast receiver in the app host (`Screen.startRemovalTransition()`). A normal JS header prop update then reached `ScreenStackHeaderConfig.onUpdate()` through Fabric. **This is a forced transition, not a natural reproduction.**

| Before: current main | After: rebased fix |
| --- | --- |
| <video src="https://github.com/user-attachments/assets/de9f5dbe-d5b1-4526-bbb5-1e2babdc10f0" controls="controls"></video> | <video src="https://github.com/user-attachments/assets/a06d459a-3fe1-45ad-b8fc-a41424bf83c8" controls="controls"></video> |
| Header refresh throws `The specified child already has a parent` in `ScreenStackHeaderConfig.onUpdate`. | Header refresh succeeds. Cleanup, another refresh, Back and re-entry remain usable. |

| Check | Observed result |
| --- | --- |
| Physical API 37, baseline | 10/10 completed trials produced the same header parent exception. Each trial launched a fresh app. |
| Physical API 37, fixed | 30/30 consecutive start/update/end cycles passed in one app session. Header revisions reached 60. Back and re-entry passed on the first and last cycles. |
| API 36 arm64 emulator, debug | Baseline failed with the same exception; the fixed build passed the same controlled sequence. |
| API 36 arm64 emulator, release | The bundled fixed APK passed controlled removal, refresh, cleanup, Back and re-entry. |
| Native tabs, API 36 release | 10 selected-tab swap/root-loader operations passed. LEFT/CENTER/RIGHT headers, route labels and both content/header buttons remained usable. |
| Framework checks, API 35 and 36 | 38 test cases passed: 28 intended-flow regression cases and 10 boundary characterizations. The latter deliberately document counterexamples under injected ordering. |
| Source and build checks | Six extracted methods exactly matched the rebased source. Debug and release APKs built. Scoped Spotless/ktlint 1.3.0 and focused TypeScript/ESLint checks passed; ESLint reported two menu inline-style warnings. |

The controlled videos use app commit `c5bb24b`. The framework fixture omits RN, Fragment and native Tabs integration. Its ordering counterexamples do not establish production reachability. See the [fixture scope and limits](https://github.com/l2hyunwoo/rnscreens-header-removal-repro/tree/main/framework-tests).

<details>
<summary>Recorded APK SHA-256 values</summary>

```text
natural move, before release  e75ad9cfbfc0f952e96c94f4b8976a790ade4948d8e38e214a69da8b532be82a
natural move, after release   57fd33047ff32a3fb3e4d5bd9c1691b1c4cebdbe3a5374978bbc83efd7cfd687
controlled, before debug      7ecb3c63fb536b2181be85a2ca4e505f23acb015eeda70c331d266ef140cc22c
controlled, after debug       05748ccbfe78d187e025df653eaf1efc5bc2e27734b4715761249afc561f181a
controlled, after release     e9fb71b9a05d0ad0e49d04fe6c47bb8811582312bd8e5f5e40a85df6c0a35a9a
```

</details>
