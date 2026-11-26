package com.rox.assistant

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.*
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var bubbleView: View? = null
    private var windowManager: WindowManager? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val perms = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (perms.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            ActivityCompat.requestPermissions(this, perms, 101)
        }

        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
        }

        webView.addJavascriptInterface(RoxBridge(this), "RoxNative")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                request.grant(request.resources)
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url.toString()
                if (url.startsWith("intent:") || url.startsWith("tel:") || url.startsWith("sms:") || url.startsWith("https://wa.me")) {
                    try {
                        val intent = if (url.startsWith("intent:")) Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                        else Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    return true
                }
                return false
            }
        }

        webView.loadUrl("file:///android_asset/index.html")
    }

    fun toggleFloatingBubble(show: Boolean) {
        runOnUiThread {
            if (show) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    startActivity(intent)
                    return@runOnUiThread
                }
                if (bubbleView != null) return@runOnUiThread

                windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
                val img = ImageView(this).apply {
                    setImageResource(android.R.drawable.ic_btn_speak_now)
                    setBackgroundColor(0xCC10B981.toInt())
                    setPadding(24, 24, 24, 24)
                    setOnClickListener {
                        val i = Intent(this@MainActivity, MainActivity::class.java)
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        startActivity(i)
                    }
                }
                val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else WindowManager.LayoutParams.TYPE_PHONE

                val params = WindowManager.LayoutParams(
                    140, 140, type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.CENTER_VERTICAL or Gravity.END
                    x = 20
                }
                bubbleView = img
                windowManager?.addView(bubbleView, params)
            } else {
                bubbleView?.let {
                    windowManager?.removeView(it)
                    bubbleView = null
                }
            }
        }
    }

    inner class RoxBridge(private val ctx: Context) {

        @JavascriptInterface
        fun isAccessibilityEnabled(): Boolean {
            val expected = "${ctx.packageName}/${RoxAccessibilityService::class.java.canonicalName}"
            val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabled)
            while (splitter.hasNext()) {
                if (splitter.next().equals(expected, ignoreCase = true)) return true
            }
            return false
        }

        @JavascriptInterface
        fun openAccessibilitySettings() {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
        }

        @JavascriptInterface
        fun readScreen(): String {
            return RoxAccessibilityService.instance?.getScreenText() ?: "Accessibility service not running"
        }

        @JavascriptInterface
        fun tapText(text: String): String {
            val ok = RoxAccessibilityService.instance?.clickByText(text) ?: false
            return if (ok) "Tapped $text" else "Could not find $text"
        }

        @JavascriptInterface
        fun typeText(text: String): String {
            val ok = RoxAccessibilityService.instance?.inputText(text) ?: false
            return if (ok) "Typed text" else "No editable box focused"
        }

        @JavascriptInterface
        fun scroll(direction: String): String {
            val ok = RoxAccessibilityService.instance?.scrollScreen(direction) ?: false
            return if (ok) "Scrolled $direction" else "Cannot scroll"
        }

        @JavascriptInterface
        fun globalAction(key: String): String {
            val ok = RoxAccessibilityService.instance?.performSystemKey(key) ?: false
            return if (ok) "Pressed $key" else "Failed"
        }

        @JavascriptInterface
        fun showBubble(show: Boolean) {
            toggleFloatingBubble(show)
        }

        @JavascriptInterface
        fun setWakeWord(enable: Boolean) {}
    }
}
