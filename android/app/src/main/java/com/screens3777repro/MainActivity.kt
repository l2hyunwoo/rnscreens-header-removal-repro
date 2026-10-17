package com.screens3777repro

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.swmansion.rnscreens.legacy.Screen
import com.swmansion.rnscreens.legacy.ScreenStackHeaderConfig
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate

class MainActivity : ReactActivity() {
  private var removalOwner: Screen? = null
  private val transitionProbe = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      when (intent.action) {
        "com.screens3777repro.BEGIN" -> {
          val owner = findNestedHeaderOwner(window.decorView)
          if (owner == null) {
            Log.e("ReproTransition", "No nested header owner found")
          } else {
            owner.startRemovalTransition()
            removalOwner = owner
            Log.i("ReproTransition", "BEGIN owner=${System.identityHashCode(owner)} removed=${owner.isBeingRemoved}")
          }
        }
        "com.screens3777repro.END" -> {
          removalOwner?.endRemovalTransition()
          Log.i("ReproTransition", "END owner=${removalOwner?.let { System.identityHashCode(it) }}")
          removalOwner = null
        }
      }
    }
  }

  override fun onCreate(savedInstanceState: android.os.Bundle?) {
    super.onCreate(savedInstanceState)
    val filter = IntentFilter().apply {
      addAction("com.screens3777repro.BEGIN")
      addAction("com.screens3777repro.END")
    }
    if (Build.VERSION.SDK_INT >= 33) {
      registerReceiver(transitionProbe, filter, Context.RECEIVER_EXPORTED)
    } else {
      @Suppress("DEPRECATION")
      registerReceiver(transitionProbe, filter)
    }
  }

  override fun onDestroy() {
    unregisterReceiver(transitionProbe)
    super.onDestroy()
  }

  private fun findNestedHeaderOwner(root: View): Screen? {
    val headers = mutableListOf<ScreenStackHeaderConfig>()
    fun visit(view: View) {
      if (view is ScreenStackHeaderConfig) headers.add(view)
      if (view is ViewGroup) {
        for (i in 0 until view.childCount) visit(view.getChildAt(i))
      }
    }
    visit(root)
    val candidates = headers.mapNotNull { header ->
      val screens = mutableListOf<Screen>()
      var parent = header.parent
      while (parent != null) {
        if (parent is Screen) screens.add(parent)
        parent = parent.parent
      }
      if (screens.size < 2) null else screens.size to screens.last()
    }
    return candidates.maxByOrNull { it.first }?.second
  }

  override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
    if (event.keyCode == android.view.KeyEvent.KEYCODE_BACK) {
      android.util.Log.i("ReproBack", "action=${event.action} eventTime=${event.eventTime} uptime=${android.os.SystemClock.uptimeMillis()}")
    }
    return super.dispatchKeyEvent(event)
  }


  /**
   * Returns the name of the main component registered from JavaScript. This is used to schedule
   * rendering of the component.
   */
  override fun getMainComponentName(): String = "Screens3777Repro"

  /**
   * Returns the instance of the [ReactActivityDelegate]. We use [DefaultReactActivityDelegate]
   * which allows you to enable New Architecture with a single boolean flags [fabricEnabled]
   */
  override fun createReactActivityDelegate(): ReactActivityDelegate =
      DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled)
}
