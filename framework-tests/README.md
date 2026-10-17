# Framework lifecycle checks

These tests run real Android `ViewGroup` and AppCompat `Toolbar` operations under Robolectric on API 35 and 36. Six methods in `FlowFixture.kt` are copied exactly from the patched library; `verify_source.py` checks them against the submodule after the comparison patch is applied. Header `onUpdate` is reduced to the guard and toolbar operations. RN, Fragment and native Tabs integration is omitted.

```sh
python3 scripts/set-variant.py after
python3 framework-tests/verify_source.py
# JDK 21 or newer for the Robolectric runtime:
./android/gradlew -p framework-tests testDebugUnitTest --tests review.flow.ProposedFlowTest --tests review.flow.LifecycleBoundaryTest
```

`ProposedFlowTest` covers 14 scenarios on each API: direct/ancestor header guards, removed children, descendant-before-parent cleanup, reentrant end, repeated end, pending marks and blocked framework completion. Historical fixture names are preserved: `PROPOSED` means the current comparison patch; `CURRENT_PR` means the earlier removeView-only implementation.

`LifecycleBoundaryTest` adds five characterization scenarios on each API. **Passing assertions intentionally document problematic behavior under controlled ordering:** a new start inside an injected detach callback is lost; a deliberately late posted start revives removal state; duplicate nested ownership can detach a child before the second owner ends; a plain root detached before cleanup leaves the old parent; and an unmarked root detach/reattach succeeds as a negative control.

The first four are not demonstrated production regressions of this PR. In the actual code, UI queue dispatch is posted, and `ScreenContainer` synchronously removes fragments before its child detach; a plain FrameLayout fixture bypasses that cleanup path. No production caller was found that starts a new transition from the injected callback. Do not count these passing characterizations as proof that all lifecycle orderings are safe, or that native Tabs reproduces the reported crash.

The methods are derived from react-native-screens under its MIT license. The controlled app scenario separately exercises the full production Screen/Header classes through React Native and Fabric.
