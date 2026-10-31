package uz.lx7.pubgremote

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.Base64
import okhttp3.WebSocket
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.concurrent.thread

class CaptureService : Service() {
    companion object { var socketProvider: (() -> WebSocket?)? = null }
    private var projection: MediaProjection?=null
    private var display: VirtualDisplay?=null
    private var reader: ImageReader?=null
    private var running=false
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channel="pubg_remote_capture"
        if (Build.VERSION.SDK_INT>=26) {
            val nm=getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(channel,"PUBG Remote screen sharing",NotificationManager.IMPORTANCE_LOW))
            val n=Notification.Builder(this,channel).setContentTitle("PUBG Remote").setContentText("Ekran uzatilmoqda — to‘xtatish uchun ilovani oching").setSmallIcon(android.R.drawable.ic_menu_view).build()
            if (Build.VERSION.SDK_INT>=29) startForeground(1,n,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) else startForeground(1,n)
        }
        val result=intent?.getIntExtra("resultCode",0) ?: 0
        val data=intent?.getParcelableExtra<Intent>("data")
        if (data!=null) startCapture(result,data)
        return START_NOT_STICKY
    }
    private fun startCapture(result:Int,data:Intent) {
        try {
            val mgr=getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection=mgr.getMediaProjection(result,data)
            projection?.registerCallback(object: MediaProjection.Callback(){ override fun onStop(){ stopSelf() } }, null)
            val dm=resources.displayMetrics
            val width=dm.widthPixels.coerceAtMost(720)
            val height=(width.toFloat()*dm.heightPixels/dm.widthPixels).toInt().coerceAtLeast(1)
            reader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,2)
            display=projection?.createVirtualDisplay("PUBGRemote",width,height,dm.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader?.surface,null,null)
            running=true
            thread(name="pubg-frame-sender") {
                while(running) {
                    try {
                        val image=reader?.acquireLatestImage()
                        if(image!=null) {
                            val plane=image.planes[0]
                            val buffer=plane.buffer
                            val pixelStride=plane.pixelStride
                            val rowStride=plane.rowStride
                            val rowPadding=rowStride-pixelStride*width
                            val bmp=android.graphics.Bitmap.createBitmap(width+rowPadding/pixelStride,height,android.graphics.Bitmap.Config.ARGB_8888)
                            bmp.copyPixelsFromBuffer(buffer)
                            val cropped=android.graphics.Bitmap.createBitmap(bmp,0,0,width,height)
                            val out=ByteArrayOutputStream()
                            cropped.compress(android.graphics.Bitmap.CompressFormat.JPEG,55,out)
                            val b64=Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP)
                            socketProvider?.invoke()?.send(JSONObject().put("type","frame").put("data",b64).toString())
                            image.close(); bmp.recycle(); cropped.recycle()
                        }
                    } catch (_: Exception) {}
                    try { Thread.sleep(400) } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) { stopSelf() }
    }
    override fun onDestroy() { running=false; display?.release(); reader?.close(); projection?.stop(); super.onDestroy() }
}
