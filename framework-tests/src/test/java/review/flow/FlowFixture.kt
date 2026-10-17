package review.flow

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.children
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

// RN/Fragment scaffolding is deliberately omitted. The Screen start/end methods and
// the proposed ancestor guard below are extracted verbatim from the saved source.
// Tests execute real Android ViewGroup and AppCompat Toolbar operations.
class Screen(context: Context) : FrameLayout(context) {
    @Volatile
    var isBeingRemoved = false
        private set
    private var isRemovalTransitionStarted = false
    private var isEndingRemovalTransition = false
    private val removalTransitionViews = ArrayList<Pair<ViewGroup, View>>()
    fun markAsBeingRemoved() {
        isBeingRemoved = true
    }

    fun startRemovalTransition() {
        isBeingRemoved = true
        if (!isRemovalTransitionStarted) {
            isRemovalTransitionStarted = true
            startTransitionRecursive(this)
        }
    }

    private fun startTransitionRecursive(parent: ViewGroup?) {
        parent?.let {
            for (i in 0 until it.childCount) {
                val child = it.getChildAt(i)

                if (parent is SwipeRefreshLayout && child is ImageView) {
                    // SwipeRefreshLayout class which has CircleImageView as a child,
                    // does not handle `startViewTransition` properly.
                    // It has a custom `getChildDrawingOrder` method which returns
                    // wrong index if we called `startViewTransition` on the views on new arch.
                    // We add a simple View to bump the number of children to make it work.
                    // Appended, not inserted at `i`: a filler before the circle inflates the
                    // index `onMeasure` caches for it, which then outlives the child it points at.
                    // TODO: find a better way to handle this scenario
                    it.addView(View(context))
                } else {
                    child?.let { view ->
                        it.startViewTransition(view)
                        removalTransitionViews.add(it to view)
                    }
                }

                if (child is ScreenStackHeaderConfig) {
                    // we want to start transition on children of the toolbar too,
                    // which is not a child of ScreenStackHeaderConfig
                    startTransitionRecursive(child.toolbar)
                }

                if (child is ViewGroup) {
                    startTransitionRecursive(child)
                }
            }
        }
    }

    fun endRemovalTransition() {
        if (!isRemovalTransitionStarted || isEndingRemovalTransition) {
            return
        }
        isEndingRemovalTransition = true
        // Detach callbacks can synchronously re-enter transition cleanup.
        val viewsToFinish = removalTransitionViews.toList()
        removalTransitionViews.clear()
        try {
            for ((parent, child) in viewsToFinish.asReversed()) {
                parent.endViewTransition(child)
            }
        } finally {
            isRemovalTransitionStarted = false
            isBeingRemoved = false
            isEndingRemovalTransition = false
        }
    }

    fun endRemovalTransitionOriginal() {
        if (!isBeingRemoved) {
            return
        }
        isBeingRemoved = false
        endTransitionRecursive(this)
    }

    private fun endTransitionRecursive(parent: ViewGroup) {
        parent.children.forEach { childView ->
            parent.endViewTransition(childView)

            if (childView is ScreenStackHeaderConfig) {
                endTransitionRecursive(childView.toolbar)
            }

            if (childView is ViewGroup) {
                endTransitionRecursive(childView)
            }
        }
    }
}

enum class Mode { ORIGINAL, CURRENT_PR, PROPOSED }

class HeaderSubview(context: Context) : FrameLayout(context)

class ScreenStackHeaderConfig(context: Context, private val mode: Mode) : FrameLayout(context) {
    val toolbar = Toolbar(context)
    private val configSubviews = ArrayList<HeaderSubview>()
    val configSubviewsCount: Int get() = configSubviews.size
    var skippedUpdates = 0
        private set
    private fun isInRemovalTransition(): Boolean {
        var ancestor: ViewParent? = parent
        while (ancestor != null) {
            if (ancestor is Screen && ancestor.isBeingRemoved) {
                return true
            }
            ancestor = ancestor.parent
        }
        return false
    }

    fun addConfigSubview(view: HeaderSubview) { configSubviews.add(view) }

    fun removeConfigSubview(index: Int) {
        configSubviews.removeAt(index)
        maybeUpdate()
    }

    private fun maybeUpdate() {
        if (mode == Mode.PROPOSED) {
            if (parent != null) onUpdate()
        } else if (parent != null && (parent as? Screen)?.isBeingRemoved == false) {
            onUpdate()
        }
    }

    // The other guards (attached, top screen, destroyed) are assumed to pass in
    // this fixture. Activity/actionbar styling does not affect the failing operations.
    fun onUpdate() {
        if (mode == Mode.PROPOSED && isInRemovalTransition()) {
            skippedUpdates++
            return
        }
        for (i in toolbar.childCount - 1 downTo 0) {
            if (toolbar.getChildAt(i) is HeaderSubview) toolbar.removeViewAt(i)
        }
        for (view in configSubviews) {
            if (mode == Mode.CURRENT_PR) (view.parent as? ViewGroup)?.removeView(view)
            toolbar.addView(view)
        }
    }
}

class FragmentFixture(val screen: Screen)
class ScreensCoordinatorLayout(context: Context, val fragment: FragmentFixture) : FrameLayout(context) {
    var blockFrameworkTransitionFinalization = false
}
class DrawingOrderFixture { fun disable() {} }
class ScreenStack(context: Context) : FrameLayout(context) {
    private val disappearingTransitioningChildren = ArrayList<View>()
    private var childrenDrawingOrderStrategy: DrawingOrderFixture? = null
    private var removalTransitionStarted = false
    private fun dispatchOnFinishTransitioning() {}
    override fun endViewTransition(view: View) {
        require(view is ScreensCoordinatorLayout) {
            "[RNScreens] Expected children of type: ${ScreensCoordinatorLayout::class.java.simpleName}"
        }
        if (view.blockFrameworkTransitionFinalization) {
            return
        }

        // Finish descendants before Android detaches the fragment's root view.
        view.fragment.screen.endRemovalTransition()
        super.endViewTransition(view)

        disappearingTransitioningChildren.remove(view)

        if (disappearingTransitioningChildren.isEmpty()) {
            childrenDrawingOrderStrategy?.disable()
        }
        if (removalTransitionStarted) {
            removalTransitionStarted = false
            dispatchOnFinishTransitioning()
        }
    }
}
