package review.flow;

import android.app.Activity;
import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

/** Characterization tests: passing means the stated boundary behavior was observed,
 * not that its production ordering or user-visible impact was reproduced. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {35, 36})
@LooperMode(LooperMode.Mode.PAUSED)
public class LifecycleBoundaryTest {
    private Context context;
    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        context.setTheme(androidx.appcompat.R.style.Theme_AppCompat);
    }
    private Activity activity() {
        return Robolectric.buildActivity(Activity.class).setup().visible().get();
    }
    private int pairCount(Screen screen) throws Exception {
        Field field = Screen.class.getDeclaredField("removalTransitionViews");
        field.setAccessible(true);
        return ((List<?>) field.get(screen)).size();
    }
    private boolean started(Screen screen) throws Exception {
        Field field = Screen.class.getDeclaredField("isRemovalTransitionStarted");
        field.setAccessible(true);
        return field.getBoolean(screen);
    }
    @Test public void reentrantStartDuringRealDetachIsLost() throws Exception {
        Activity activity = activity();
        Screen screen = new Screen(activity);
        activity.setContentView(screen);
        final boolean[] callback = {false};
        final View next = new View(activity);
        View leaf = new View(activity) {
            @Override protected void onDetachedFromWindow() {
                callback[0] = true;
                screen.addView(next);
                screen.startRemovalTransition();
                assertTrue(screen.isBeingRemoved());
                super.onDetachedFromWindow();
            }
        };
        screen.addView(leaf);
        screen.startRemovalTransition();
        screen.removeView(leaf);
        screen.endRemovalTransition();
        assertTrue(callback[0]);
        assertFalse("Observed: the callback's new start is cleared", screen.isBeingRemoved());
        assertFalse(started(screen));
        assertEquals(0, pairCount(screen));
        screen.removeView(next);
        assertNull("New child was never protected by the requested transition", next.getParent());
        System.out.println("BOUNDARY reentrant start: real detach callback ran; new start lost, no pairs retained");
    }
    @Test public void postedStartAfterSynchronousCompletionRestartsRemovedScreen() throws Exception {
        Activity activity = activity();
        FrameLayout host = new FrameLayout(activity);
        Screen screen = new Screen(activity);
        screen.addView(new View(activity));
        activity.setContentView(host);
        host.addView(screen);
        // NativeProxy mark/post ordering, with a controlled intervening synchronous lifecycle.
        screen.markAsBeingRemoved();
        assertTrue(screen.post(screen::startRemovalTransition));
        screen.startRemovalTransition();
        screen.endRemovalTransition();
        host.removeView(screen);
        assertFalse(screen.isBeingRemoved());
        shadowOf(Looper.getMainLooper()).idle();
        assertFalse(screen.isAttachedToWindow());
        assertTrue("Observed: stale runnable revives removal state", screen.isBeingRemoved());
        assertTrue(started(screen));
        assertEquals(1, pairCount(screen));
        System.out.println("BOUNDARY delayed start: detached screen became removing again after queued callback");
    }
    @Test public void nestedOwnersFirstEndDetachesChildWhileSecondOwnerStillActive() throws Exception {
        Activity activity = activity();
        Screen outer = new Screen(activity);
        Screen inner = new Screen(activity);
        FrameLayout content = new FrameLayout(activity);
        View leaf = new View(activity);
        activity.setContentView(outer);
        outer.addView(inner);
        inner.addView(content);
        content.addView(leaf);
        outer.startRemovalTransition();
        inner.startRemovalTransition();
        content.removeView(leaf);
        assertSame(content, leaf.getParent());
        assertTrue(leaf.isAttachedToWindow());
        inner.endRemovalTransition();
        assertTrue(outer.isBeingRemoved());
        assertTrue(pairCount(outer) > 0);
        assertNull("Observed: first owner ends shared disappearing child", leaf.getParent());
        assertFalse(leaf.isAttachedToWindow());
        outer.endRemovalTransition();
        assertFalse(outer.isBeingRemoved());
        System.out.println("BOUNDARY nested owners: inner end detaches shared leaf before outer end");
    }
    @Test public void ancestorRootDetachBeforeCleanupLeavesRemovedChildParent() {
        Activity activity = activity();
        FrameLayout host = new FrameLayout(activity);
        Screen screen = new Screen(activity);
        FrameLayout content = new FrameLayout(activity);
        View leaf = new View(activity);
        activity.setContentView(host);
        host.addView(screen);
        screen.addView(content);
        content.addView(leaf);
        screen.startRemovalTransition();
        content.removeView(leaf);
        host.removeView(screen); // Plain ancestor detach bypasses fixture ScreenStack completion.
        assertFalse(leaf.isAttachedToWindow());
        screen.endRemovalTransition();
        assertSame("Observed: ViewGroup detach cleared transition bookkeeping before end", content, leaf.getParent());
        assertThrows(IllegalStateException.class, () -> new FrameLayout(activity).addView(leaf));
        System.out.println("BOUNDARY root detach: cleanup after root detach leaves removed leaf with old parent");
    }
    @Test public void unmarkedRootDetachDoesNotActivateHeaderRemovalGuard() {
        Activity activity = activity();
        FrameLayout host = new FrameLayout(activity);
        Screen screen = new Screen(activity);
        ScreenStackHeaderConfig header = new ScreenStackHeaderConfig(activity, Mode.PROPOSED);
        HeaderSubview child = new HeaderSubview(activity);
        header.addConfigSubview(child);
        screen.addView(header);
        activity.setContentView(host);
        host.addView(screen);
        header.onUpdate();
        host.removeView(screen);
        assertFalse(screen.isBeingRemoved());
        host.addView(screen);
        header.onUpdate();
        assertEquals(0, header.getSkippedUpdates());
        assertSame(header.getToolbar(), child.getParent());
        System.out.println("CONTROL root detach without transition: no removal mark; reattach/header update succeeds");
    }
}
