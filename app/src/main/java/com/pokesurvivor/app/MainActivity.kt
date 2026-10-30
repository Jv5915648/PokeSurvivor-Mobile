package com.pokesurvivor.app

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.*
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private var isHighPerformance = false

    private data class FpsMode(val modeId: Int, val label: String, val refreshRate: Float)
    private val availableFpsModes = mutableListOf<FpsMode>()
    private var currentFpsIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootLayout = FrameLayout(this)

        webView = WebView(this)
        rootLayout.addView(
            webView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        detectSupportedRefreshRates()

        // Bar de botones flotantes
        val buttonBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val toggleButton = Button(this).apply {
            text = "Normal"
            setTextColor(Color.WHITE)
            textSize = 11f
            setPadding(20, 10, 20, 10)
            background = createButtonBackground("#80000000")
        }

        val fpsButton = Button(this).apply {
            text = availableFpsModes.getOrNull(currentFpsIndex)?.label ?: "Auto"
            setTextColor(Color.WHITE)
            textSize = 11f
            setPadding(20, 10, 20, 10)
            background = createButtonBackground("#80000000")
        }

        val buttonMargin = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, 16, 0)
        }

        buttonBar.addView(toggleButton, buttonMargin)
        buttonBar.addView(fpsButton)

        val barParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            setMargins(32, 0, 0, 32)
        }

        rootLayout.addView(buttonBar, barParams)
        setContentView(rootLayout)

        hideSystemUI()

        // -------------------------------------------------------------
        // CONMUTACIÓN DE MODOS (RENDIMIENTO VS BATERÍA/FRESCO)
        // -------------------------------------------------------------
        toggleButton.setOnClickListener {
            isHighPerformance = !isHighPerformance

            if (isHighPerformance) {
                // MODO ALTO RENDIMIENTO (Forzado total de GPU)
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
                }

                toggleButton.text = "Rendimiento"
                toggleButton.background = createButtonBackground("#CC2E7D32")
                Toast.makeText(this, "Modo Alto Rendimiento", Toast.LENGTH_SHORT).show()
            } else {
                // MODO NORMAL (Frío, eficiente y ahorro de batería)
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                webView.setLayerType(View.LAYER_TYPE_NONE, null)
                resetRefreshRate()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_WAIVED, true)
                }

                toggleButton.text = "Normal"
                toggleButton.background = createButtonBackground("#80000000")
                Toast.makeText(this, "Modo Normal: Teléfono Fresco", Toast.LENGTH_SHORT).show()
            }
        }

        // Selector de Hercios
        fpsButton.setOnClickListener {
            if (availableFpsModes.isNotEmpty()) {
                currentFpsIndex = (currentFpsIndex + 1) % availableFpsModes.size
                val selectedMode = availableFpsModes[currentFpsIndex]

                applyRefreshRate(selectedMode.modeId)
                fpsButton.text = selectedMode.label
                Toast.makeText(this, "Tasa de pantalla: ${selectedMode.label}", Toast.LENGTH_SHORT).show()
            }
        }

        // Ajustes del motor WebView
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            mediaPlaybackRequiresUserGesture = false
            displayZoomControls = false
            builtInZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Aceleración ligera de renderizado por CSS sin sobrecargar GPU
                val cssInject = """
                    (function() {
                        var style = document.createElement('style');
                        style.innerHTML = 'canvas { transform: translateZ(0); -webkit-transform: translateZ(0); }';
                        document.head.appendChild(style);
                    })();
                """.trimIndent()
                view?.evaluateJavascript(cssInject, null)
            }
        }

        // Ventana emergente de inicio de sesión de Google
        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                val popupWebView = WebView(this@MainActivity)

                with(popupWebView.settings) {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    javaScriptCanOpenWindowsAutomatically = true
                    userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
                }

                CookieManager.getInstance().setAcceptThirdPartyCookies(popupWebView, true)

                val dialog = Dialog(this@MainActivity, android.R.style.Theme_NoTitleBar_Fullscreen)
                dialog.setContentView(popupWebView)

                // Limpieza absoluta de memoria al cerrar el popup para cuidar la batería
                dialog.setOnDismissListener {
                    popupWebView.stopLoading()
                    popupWebView.clearHistory()
                    popupWebView.removeAllViews()
                    popupWebView.destroy()
                }

                dialog.show()

                popupWebView.webChromeClient = object : WebChromeClient() {
                    override fun onCloseWindow(window: WebView?) {
                        if (dialog.isShowing) {
                            dialog.dismiss()
                        }
                    }
                }

                popupWebView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        return false
                    }
                }

                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = popupWebView
                resultMsg?.sendToTarget()

                return true
            }
        }

        webView.loadUrl("https://pokesurvivor.com")
    }

    private fun detectSupportedRefreshRates() {
        availableFpsModes.clear()
        availableFpsModes.add(FpsMode(0, "Auto", 0f))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val modes = display?.supportedModes ?: emptyArray()
            val uniqueRates = mutableSetOf<Int>()

            for (mode in modes) {
                val rateInt = mode.refreshRate.toInt()
                if (rateInt >= 50 && uniqueRates.add(rateInt)) {
                    availableFpsModes.add(FpsMode(mode.modeId, "${rateInt} Hz", mode.refreshRate))
                }
            }
        }
    }

    private fun applyRefreshRate(modeId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes = window.attributes.apply {
                preferredDisplayModeId = modeId
            }
        }
    }

    private fun resetRefreshRate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes = window.attributes.apply {
                preferredDisplayModeId = 0
            }
        }
    }

    private fun createButtonBackground(hexColor: String): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor(hexColor))
            cornerRadius = 24f
        }
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::webView.isInitialized) {
            webView.onPause()
            webView.pauseTimers()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) {
            webView.onResume()
            webView.resumeTimers()
        }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.clearHistory()
            webView.removeAllViews()
            webView.destroy()
        }
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}