package com.enok.sniffer

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.net.URLDecoder

class MainActivity : AppCompatActivity() {

    private var webView: WebView? = null
    private var urlBar: EditText? = null
    private var badge: TextView? = null
    private var panel: LinearLayout? = null
    private var panelList: LinearLayout? = null

    private val sniffed = LinkedHashMap<String, String>()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)

            val root = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(0xFF000000.toInt())
            }

            // ===== TOP BAR =====
            val topBar = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(8, 8, 8, 8)
                setBackgroundColor(0xFF0A0A0A.toInt())
            }

            val backBtn = mkBtn("◀") {
                if (webView?.canGoBack() == true) webView?.goBack()
            }
            val fwdBtn = mkBtn("▶") {
                if (webView?.canGoForward() == true) webView?.goForward()
            }
            val reloadBtn = mkBtn("⟳") { webView?.reload() }

            urlBar = EditText(this).apply {
                hint = "Search or type URL"
                setSingleLine()
                setTextColor(0xFFFFFFFF.toInt())
                setHintTextColor(0xFF888888.toInt())
                setBackgroundColor(0xFF1C1C1E.toInt())
                imeOptions = EditorInfo.IME_ACTION_GO
                setPadding(30, 24, 30, 24)
            }

            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.setMargins(6, 0, 6, 0)

            topBar.addView(backBtn)
            topBar.addView(fwdBtn)
            topBar.addView(reloadBtn)
            topBar.addView(urlBar, lp)

            root.addView(topBar, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))

            // ===== WEBVIEW =====
            webView = WebView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
            }

            webView?.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    try {
                        val url = request?.url?.toString() ?: return null
                        val lower = url.lowercase().split("?")[0]
                        if (lower.endsWith(".mp4") ||
                            lower.endsWith(".m3u8") ||
                            lower.endsWith(".webm") ||
                            lower.endsWith(".mov") ||
                            lower.endsWith(".m4v") ||
                            lower.endsWith(".ts")) {
                            runOnUiThread { addSniffed(url) }
                        }
                    } catch (t: Throwable) { /* never crash the page load */ }
                    return null
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val url = request?.url?.toString() ?: return false
                    return if (url.startsWith("http")) {
                        view?.loadUrl(url)
                        true
                    } else false
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    if (url != null) urlBar?.setText(url)
                }
            }

            webView?.webChromeClient = object : WebChromeClient() {}

            root.addView(webView)

            // ===== SNIFF ROW =====
            val sniffBtn = Button(this).apply {
                text = "Sniff"
                setBackgroundColor(0xFF0A84FF.toInt())
                setTextColor(0xFFFFFFFF.toInt())
                setOnClickListener {
                    sniffed.clear()
                    updateBadge()
                    webView?.reload()
                    Toast.makeText(this@MainActivity,
                        "Sniffing… scroll/play the page",
                        Toast.LENGTH_SHORT).show()
                    renderPanel()
                    panel?.visibility = View.VISIBLE
                }
            }

            badge = TextView(this).apply {
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundColor(0xFFFF3B30.toInt())
                setPadding(20, 4, 20, 4)
                text = "0"
            }

            val sniffRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(8, 4, 8, 8)
                setBackgroundColor(0xFF0A0A0A.toInt())
            }
            sniffRow.addView(sniffBtn)
            sniffRow.addView(badge)
            root.addView(sniffRow)

            // ===== PANEL =====
            panel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(0xFF161618.toInt())
                setPadding(16, 16, 16, 16)
                visibility = View.GONE
            }
            panelList = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            val closeBtn = Button(this).apply {
                text = "Close panel"
                setOnClickListener { panel?.visibility = View.GONE }
            }
            panel?.addView(closeBtn)
            panel?.addView(panelList, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            ))
            root.addView(panel, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))

            setContentView(root)

            // ===== URL BAR LISTENER =====
            urlBar?.setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_GO ||
                    (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)) {
                    var q = urlBar?.text?.toString()?.trim() ?: ""
                    if (q.isNotEmpty()) {
                        if (!q.startsWith("http")) {
                            q = if (q.contains(" ") || !q.contains("."))
                                "https://www.google.com/search?q=" + Uri.encode(q)
                            else "https://$q"
                        }
                        webView?.loadUrl(q)
                        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                            .hideSoftInputFromWindow(urlBar?.windowToken, 0)
                        urlBar?.clearFocus()
                    }
                    true
                } else false
            }

            webView?.loadUrl("https://www.google.com")

        } catch (t: Throwable) {
            // If ANYTHING goes wrong, show it on screen instead of crashing
            val tv = TextView(this).apply {
                text = "Startup error:\n\n${t.message}\n\n${t.stackTraceToString()}"
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundColor(0xFFAA0000.toInt())
                setPadding(30, 30, 30, 30)
            }
            setContentView(tv)
        }
    }

    private fun mkBtn(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            setBackgroundColor(0xFF1C1C1E.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener { onClick() }
        }
    }

    private fun addSniffed(url: String) {
        if (sniffed.containsKey(url)) return
        val clean = url.split("?")[0].lowercase()
        val mime = when {
            clean.endsWith(".mp4") || clean.endsWith(".m4v") -> "video/mp4"
            clean.endsWith(".webm") -> "video/webm"
            clean.endsWith(".m3u8") -> "application/x-mpegURL"
            clean.endsWith(".mov") -> "video/quicktime"
            clean.endsWith(".ts") -> "video/mp2t"
            else -> "video/*"
        }
        sniffed[url] = mime
        updateBadge()
        renderPanel()
    }

    private fun updateBadge() {
        badge?.text = sniffed.size.toString()
    }

    private fun renderPanel() {
        val list = panelList ?: return
        list.removeAllViews()
        if (sniffed.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "No videos yet. Tap Sniff, then scroll/play the page."
                setTextColor(0xFF8E8E93.toInt())
                setPadding(8, 16, 8, 16)
            })
            return
        }
        for ((url, mime) in sniffed) {
            val name = try {
                URLDecoder.decode(url.split("/").last().split("?")[0], "UTF-8")
            } catch (e: Exception) { "video" }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12, 12, 12, 12)
                setBackgroundColor(0xFF1C1C1E.toInt())
            }
            row.addView(TextView(this).apply {
                text = name
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 14f
            })
            row.addView(TextView(this).apply {
                text = mime
                setTextColor(0xFF0A84FF.toInt())
                textSize = 11f
            })
            row.addView(Button(this).apply {
                text = "⬇ Download"
                setOnClickListener { download(url, name) }
            })
            list.addView(row)
        }
    }

    private fun download(url: String, filename: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(url))
            request.setTitle(filename)
            request.setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            request.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS, filename
            )
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView?.canGoBack() == true) webView?.goBack() else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
