package review.flow;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {35, 36})
public class ProposedFlowTest {
    private Context context;
    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        context.setTheme(androidx.appcompat.R.style.Theme_AppCompat);
    }

    private class HeaderTree {
        final Screen outer = new Screen(context);
        final Screen inner = new Screen(context);
        final ScreenStackHeaderConfig header;
        final HeaderSubview left = new HeaderSubview(context);
        final HeaderSubview center = new HeaderSubview(context);
        HeaderTree(Mode mode) {
            FrameLayout wrapper = new FrameLayout(context);
            outer.addView(wrapper);
            wrapper.addView(inner);
            header = new ScreenStackHeaderConfig(context, mode);
            inner.addView(header);
            header.addConfigSubview(left);
            header.addConfigSubview(center);
            header.onUpdate();
        }
    }

    @Test public void currentPrCrashesOnNestedRemovalFromMaybeUpdate() {
        HeaderTree t = new HeaderTree(Mode.CURRENT_PR);
        t.outer.startRemovalTransition();
        assertTrue(t.outer.isBeingRemoved());
        assertFalse(t.inner.isBeingRemoved());
        assertThrows(IllegalStateException.class, () -> t.header.removeConfigSubview(1));
        assertEquals(1, t.header.getConfigSubviewsCount());
        assertEquals(-1, t.header.getToolbar().indexOfChild(t.left));
        assertSame(t.header.getToolbar(), t.left.getParent());
        System.out.println("BEFORE nested remove: outer=true, inner=false; logical=1, native index=-1, parent=toolbar -> crash");
    }

    @Test public void currentPrCrashesOnDirectPropsUpdateDuringOwnRemoval() {
        HeaderTree t = new HeaderTree(Mode.CURRENT_PR);
        t.inner.startRemovalTransition();
        // onAfterUpdateTransaction calls onUpdate directly, without maybeUpdate.
        assertThrows(IllegalStateException.class, t.header::onUpdate);
    }

    @Test public void proposedGuardPreservesNativeHeaderDuringNestedRemoval() {
        HeaderTree t = new HeaderTree(Mode.PROPOSED);
        t.outer.startRemovalTransition();
        t.header.removeConfigSubview(1);
        assertEquals(1, t.header.getConfigSubviewsCount());
        assertEquals(2, t.header.getToolbar().getChildCount());
        assertEquals(0, t.header.getToolbar().indexOfChild(t.left));
        assertEquals(1, t.header.getToolbar().indexOfChild(t.center));
        assertEquals(1, t.header.getSkippedUpdates());
        System.out.println("AFTER nested remove: logical=1, toolbar still holds LEFT+CENTER for the outgoing transition; no crash");
    }

    @Test public void proposedGuardAlsoBlocksDirectPropsOrAttachUpdateDuringOwnRemoval() {
        HeaderTree t = new HeaderTree(Mode.PROPOSED);
        t.inner.startRemovalTransition();
        t.header.onUpdate();
        assertEquals(2, t.header.getToolbar().getChildCount());
        assertEquals(1, t.header.getSkippedUpdates());
    }

    @Test public void proposedGuardAllowsNormalHeaderUpdates() {
        HeaderTree t = new HeaderTree(Mode.PROPOSED);
        t.header.removeConfigSubview(1);
        assertEquals(1, t.header.getToolbar().getChildCount());
        assertSame(t.left, t.header.getToolbar().getChildAt(0));
        assertNull(t.center.getParent());
        assertEquals(0, t.header.getSkippedUpdates());
    }

    @Test public void headerCanReconcileOnNextUpdateAfterTransitionEnds() {
        HeaderTree t = new HeaderTree(Mode.PROPOSED);
        t.outer.startRemovalTransition();
        t.header.removeConfigSubview(1);
        t.outer.endRemovalTransition();
        t.header.onUpdate(); // Existing onAttached/onContainerUpdate entry on reuse.
        assertEquals(1, t.header.getToolbar().getChildCount());
        assertSame(t.left, t.header.getToolbar().getChildAt(0));
        assertNull(t.center.getParent());
    }

    private class RemovalTree {
        final FrameLayout host;
        final FrameLayout coordinator;
        final Screen screen;
        final FrameLayout content;
        final View leaf;
        RemovalTree() {
            Activity activity = Robolectric.buildActivity(Activity.class).setup().visible().get();
            host = new FrameLayout(activity);
            coordinator = new FrameLayout(activity);
            screen = new Screen(activity);
            content = new FrameLayout(activity);
            leaf = new View(activity);
            activity.setContentView(host);
            host.addView(coordinator);
            coordinator.addView(screen);
            screen.addView(content);
            content.addView(leaf);
            assertTrue(leaf.isAttachedToWindow());
            screen.startRemovalTransition();
            host.startViewTransition(coordinator);
            content.removeView(leaf);
            screen.removeView(content);
            host.removeView(coordinator);
            assertTrue(leaf.isAttachedToWindow());
            assertSame(content, leaf.getParent());
            assertEquals(0, content.getChildCount());
            assertEquals(0, screen.getChildCount());
        }
    }

    @Test public void originalTraversalMissesAlreadyRemovedDescendantsEvenBeforeDetach() {
        RemovalTree t = new RemovalTree();
        t.screen.endRemovalTransitionOriginal();
        assertSame(t.content, t.leaf.getParent());
        assertTrue(t.leaf.isAttachedToWindow());
    }

    @Test public void recordingPairsAloneStillFailsIfParentDetachesFirst() {
        RemovalTree t = new RemovalTree();
        t.host.endViewTransition(t.coordinator); // Actual framework detach, not a simulated clear.
        assertFalse(t.leaf.isAttachedToWindow());
        t.screen.endRemovalTransition();
        assertSame(t.content, t.leaf.getParent());
        System.out.println("WRONG ORDER: parent detach then recorded cleanup -> leaf.parent remains content");
    }

    @Test public void recordedChildrenFirstCleanupBeforeParentDetachReleasesAllParents() {
        RemovalTree t = new RemovalTree();
        // The proposed ScreenStack.endViewTransition ordering.
        t.screen.endRemovalTransition();
        t.host.endViewTransition(t.coordinator);
        assertNull(t.leaf.getParent());
        assertNull(t.content.getParent());
        assertNull(t.coordinator.getParent());
        assertFalse(t.leaf.isAttachedToWindow());
        assertFalse(t.screen.isBeingRemoved());
        System.out.println("CORRECT ORDER: recorded descendants first, then parent detach -> all removed parents cleared");
    }

    @Test public void repeatedCompletionCannotDetachAnAlreadyReattachedView() {
        RemovalTree t = new RemovalTree();
        t.screen.endRemovalTransition();
        t.host.endViewTransition(t.coordinator);
        FrameLayout newParent = new FrameLayout(context);
        newParent.addView(t.leaf);
        t.screen.endRemovalTransition(); // Later animation-end callback.
        assertSame(newParent, t.leaf.getParent());
    }

    @Test public void cleanupKeepsRemovalGuardActiveDuringReentrantDetachCallbacks() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().visible().get();
        Screen screen = new Screen(activity);
        activity.setContentView(screen);
        final boolean[] observed = {false};
        View leaf = new View(activity) {
            @Override protected void onDetachedFromWindow() {
                observed[0] = true;
                screen.endRemovalTransition(); // Synchronous re-entry.
                assertTrue(screen.isBeingRemoved());
                super.onDetachedFromWindow();
            }
        };
        screen.addView(leaf);
        screen.startRemovalTransition();
        screen.removeView(leaf);
        screen.endRemovalTransition();
        assertTrue(observed[0]);
        assertFalse(screen.isBeingRemoved());
        assertNull(leaf.getParent());
    }

    @Test public void pendingRemovalMarkSurvivesCompletionBeforeNativeTransitionStarts() {
        HeaderTree t = new HeaderTree(Mode.PROPOSED);
        t.outer.markAsBeingRemoved();
        t.outer.endRemovalTransition(); // Mounting coordinator marked; posted UI start has not run yet.
        assertTrue(t.outer.isBeingRemoved());
        t.header.onUpdate();
        assertEquals(1, t.header.getSkippedUpdates());
        t.outer.startRemovalTransition();
        t.header.getToolbar().removeView(t.left);
        assertSame(t.header.getToolbar(), t.left.getParent());
        t.outer.endRemovalTransition();
        assertNull(t.left.getParent());
        assertFalse(t.outer.isBeingRemoved());
    }

    @Test public void duplicateStartsArePairedOnceAndNextRemovalCanStartAgain() {
        Screen screen = new Screen(context);
        final int[] calls = {0, 0};
        FrameLayout parent = new FrameLayout(context) {
            @Override public void startViewTransition(View child) {
                calls[0]++;
                super.startViewTransition(child);
            }
            @Override public void endViewTransition(View child) {
                calls[1]++;
                super.endViewTransition(child);
            }
        };
        View leaf = new View(context);
        screen.addView(parent);
        parent.addView(leaf);
        for (int cycle = 1; cycle <= 2; cycle++) {
            screen.markAsBeingRemoved();
            screen.startRemovalTransition();
            screen.startRemovalTransition();
            parent.removeView(leaf);
            screen.endRemovalTransition();
            screen.endRemovalTransition();
            assertArrayEquals(new int[] {cycle, cycle}, calls);
            assertNull(leaf.getParent());
            assertFalse(screen.isBeingRemoved());
            parent.addView(leaf);
        }
    }

    @Test public void blockedFrameworkCompletionDefersBothChildCleanupAndParentDetach() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().visible().get();
        ScreenStack host = new ScreenStack(activity);
        Screen screen = new Screen(activity);
        ScreensCoordinatorLayout coordinator = new ScreensCoordinatorLayout(activity, new FragmentFixture(screen));
        FrameLayout content = new FrameLayout(activity);
        View leaf = new View(activity);
        activity.setContentView(host);
        host.addView(coordinator);
        coordinator.addView(screen);
        screen.addView(content);
        content.addView(leaf);
        screen.startRemovalTransition();
        host.startViewTransition(coordinator);
        content.removeView(leaf);
        screen.removeView(content);
        host.removeView(coordinator);
        coordinator.setBlockFrameworkTransitionFinalization(true);
        host.endViewTransition(coordinator);
        assertTrue(screen.isBeingRemoved());
        assertTrue(leaf.isAttachedToWindow());
        assertSame(content, leaf.getParent());
        assertSame(host, coordinator.getParent());
        coordinator.setBlockFrameworkTransitionFinalization(false);
        host.endViewTransition(coordinator);
        assertFalse(screen.isBeingRemoved());
        assertFalse(leaf.isAttachedToWindow());
        assertNull(leaf.getParent());
        assertNull(content.getParent());
        assertNull(coordinator.getParent());
    }
}
