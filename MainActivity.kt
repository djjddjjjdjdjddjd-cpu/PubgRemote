package uz.lx7.pubgremote

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.format.Formatter
import android.view.ViewGroup
import android.widget.*
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {
    private val captureRequest = 2407
    private lateinit var server: EditText
    private lateinit var code: EditText
    private lateinit var status: TextView
    private var socket: WebSocket? = null
    private val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 40, 28, 28)
            setBackgroundColor(0xFF101014.toInt())
        }
        fun label(s: String) = TextView(this).apply { text=s; textSize=16f; setTextColor(0xFFFFFFFF.toInt()); setPadding(0,12,0,8) }
        val title = label("PUBG REMOTE").apply { textSize=26f; setTextColor(0xFFFF4545.toInt()) }
        layout.addView(title)
        layout.addView(label("Telefon IP manzili: ${localIp()}"))
        layout.addView(label("Server WebSocket URL (internetda WSS ishlating)"))
        server = EditText(this).apply { setText("ws://192.168.1.2:8765/ws"); setTextColor(-1); setHintTextColor(0xFFAAAAAA.toInt()); hint="ws:// yoki wss://" }
        layout.addView(server, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        layout.addView(label("Xona kodi (PC bilan bir xil bo‘lsin)"))
        code = EditText(this).apply { setText("PUBG2468"); setTextColor(-1) }
        layout.addView(code)
        val start = Button(this).apply { text="EKRANNI UZATISHNI BOSHLASH" }
        layout.addView(start)
        val stop = Button(this).apply { text="UZATISHNI TO‘XTATISH"; isEnabled=false }
        layout.addView(stop)
        val access = Button(this).apply { text="BOSHQARUV RUXSATINI SOZLASH" }
        layout.addView(access)
        status = label("Holat: tayyor")
        layout.addView(status)
        layout.addView(label("Ekran uzatish Android ruxsati bilan boshlanadi. Xona kodini begonalarga bermang."))
        setContentView(ScrollView(this).apply { addView(layout) })
        start.setOnClickListener {
            val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(mgr.createScreenCaptureIntent(), captureRequest)
        }
        stop.setOnClickListener { stopService(Intent(this, CaptureService::class.java)); socket?.close(1000,"user stopped"); socket=null; status.text="Holat: to‘xtatildi"; start.isEnabled=true; stop.isEnabled=false }
        access.setOnClickListener { startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    }

    @Deprecated("Deprecated in Android API, still supported")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == captureRequest && resultCode == RESULT_OK && data != null) {
            val url=server.text.toString().trim()
            val room=code.text.toString().trim()
            if (!(url.startsWith("ws://") || url.startsWith("wss://")) || room.length !in 4..32) {
                status.text="Server URL yoki xona kodi noto‘g‘ri"; return
            }
            val wsUrl = url + (if(url.contains("?")) "&" else "?") + "role=phone&code=" + java.net.URLEncoder.encode(room,"UTF-8")
            socket = client.newWebSocket(Request.Builder().url(wsUrl).build(), object: WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) { runOnUiThread { status.text="Holat: serverga ulandi"; } }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { runOnUiThread { status.text="Ulanish xatosi: ${t.message}" } }
                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val msg=JSONObject(text)
                        if(msg.optString("type")=="tap") RemoteControlService.performRemoteTap(msg.optDouble("x"),msg.optDouble("y"))
                    } catch (_: Exception) {}
                }
            })
            CaptureService.socketProvider = { socket }
            val i=Intent(this,CaptureService::class.java).putExtra("resultCode",resultCode).putExtra("data",data)
            startForegroundService(i)
            status.text="Holat: ekran uzatilmoqda"
        } else if (requestCode == captureRequest) status.text="Ekran uzatishga ruxsat berilmadi"
    }

    private fun localIp(): String {
        return try {
            val wm=applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            Formatter.formatIpAddress(wm.connectionInfo.ipAddress).takeIf { it!="0.0.0.0" } ?: "Wi-Fi ulanmagan"
        } catch (_: Exception) { "Aniqlanmadi" }
    }
}
