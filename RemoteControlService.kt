package uz.lx7.pubgremote

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.util.DisplayMetrics
import android.content.Context
import android.view.WindowManager

class RemoteControlService : AccessibilityService() {
    companion object {
        @Volatile private var active: RemoteControlService? = null
        fun performRemoteTap(nx: Double, ny: Double) { active?.tap(nx,ny) }
    }
    override fun onServiceConnected() { super.onServiceConnected(); active=this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { if(active===this) active=null; super.onDestroy() }
    private fun tap(nx:Double,ny:Double) {
        if(nx !in 0.0..1.0 || ny !in 0.0..1.0) return
        val wm=getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        val size=android.graphics.Point().also { wm.defaultDisplay.getRealSize(it) }
        val path=Path().apply { moveTo((nx*size.x).toFloat(),(ny*size.y).toFloat()) }
        val gesture=GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path,0,80)).build()
        dispatchGesture(gesture,null,null)
    }
}
