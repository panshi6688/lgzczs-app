package com.lgzczs.app.service

import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color as AndroidColor
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.gson.Gson
import com.lgzczs.app.MainActivity
import com.lgzczs.app.model.ToolItem
import com.lgzczs.app.util.TokenManager
import com.lgzczs.app.util.UrlOpener

class FloatWindowService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatView: FrameLayout
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var tokenManager: TokenManager
    private lateinit var prefs: SharedPreferences
    private var badgeView: View? = null
    private var orderText: TextView? = null
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "has_unviewed_orders") {
            updateBadge()
        }
    }

    private var toolsEnabled: Boolean = false
    private var panelExpanded: Boolean = false
    private var collapsedIcon: View? = null
    private var scrimView: View? = null
    private var toolsPanelView: View? = null
    private var quickAccessItems: List<ToolItem?> = emptyList()
    private var savedKeyword: String = ""
    private val gson = Gson()

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission not granted")
            stopSelf()
            return
        }
        tokenManager = TokenManager(applicationContext)
        prefs = getSharedPreferences("token_prefs", MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        createFloatView()

        toolsEnabled = tokenManager.floatToolsEnabled
        if (toolsEnabled) {
            readQuickAccessData()
            createCollapsedIcon()
        }
    }

    private fun createFloatView() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val density = displayMetrics.density
        val viewSize = (50 * density).toInt()

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                onFloatViewClick()
                return true
            }
        })

        val appIcon = applicationInfo.loadIcon(packageManager)
        val iconView = ImageView(this).apply {
            setImageDrawable(appIcon)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding((8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt())
        }

        floatView = FrameLayout(this).apply {
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setSize(viewSize, viewSize)
                setColor(0xFFFFFFFF.toInt())
                setStroke((2 * density).toInt(), 0xFFE0E0E0.toInt())
            }
            background = bg
            addView(iconView, viewSize, viewSize)

            val hasOrders = tokenManager.hasUnviewedOrders

            val badge = View(this@FloatWindowService).apply {
                val gd = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setSize((10 * density).toInt(), (10 * density).toInt())
                    setColor(AndroidColor.RED)
                }
                background = gd
                visibility = if (hasOrders) View.VISIBLE else View.GONE
            }
            val badgeParams = FrameLayout.LayoutParams(
                (10 * density).toInt(), (10 * density).toInt()
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = (2 * density).toInt()
                rightMargin = (2 * density).toInt()
            }
            addView(badge, badgeParams)
            badgeView = badge

            val textView = TextView(this@FloatWindowService).apply {
                text = "● 新订单"
                textSize = 10f
                setTextColor(AndroidColor.RED)
                gravity = Gravity.CENTER
                visibility = if (hasOrders) View.VISIBLE else View.GONE
            }
            val textParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = (2 * density).toInt()
            }
            addView(textView, textParams)
            orderText = textView

            setOnTouchListener { v, event ->
                gestureDetector.onTouchEvent(event)
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatView, params)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        snapToEdge()
                        true
                    }
                    else -> false
                }
            }
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth - viewSize
            y = screenHeight / 2 - viewSize / 2
        }

        windowManager.addView(floatView, params)
    }

    private fun snapToEdge() {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val viewWidth = floatView.width
        val midPoint = params.x + viewWidth / 2
        params.x = if (midPoint < screenWidth / 2) 0 else screenWidth - viewWidth
        windowManager.updateViewLayout(floatView, params)
    }

    private fun onFloatViewClick() {
        tokenManager.hasUnviewedOrders = false
        badgeView?.visibility = View.GONE
        orderText?.visibility = View.GONE
        Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(this)
        }
    }

    private fun updateBadge() {
        val hasOrders = tokenManager.hasUnviewedOrders
        badgeView?.visibility = if (hasOrders) View.VISIBLE else View.GONE
        orderText?.visibility = if (hasOrders) View.VISIBLE else View.GONE
    }

    private fun readQuickAccessData() {
        val toolsPrefs = getSharedPreferences("tools_cache", MODE_PRIVATE)
        quickAccessItems = (0 until 4).map { i ->
            val json = toolsPrefs.getString("qa_$i", null)
            if (json != null) {
                try { gson.fromJson(json, ToolItem::class.java) } catch (_: Exception) { null }
            } else null
        }
        val keywordPrefs = getSharedPreferences("keyword_prefs", MODE_PRIVATE)
        savedKeyword = keywordPrefs.getString("selected_keyword", "") ?: ""
    }

    private fun createCollapsedIcon() {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val density = displayMetrics.density
        val iconSize = (24 * density).toInt()
        val positionY = ((screenHeight * 0.3).toInt() - iconSize / 2).coerceAtLeast(0)

        val icon = FrameLayout(this).apply {
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setSize(iconSize, iconSize)
                setColor(AndroidColor.argb(160, 0, 0, 0))
            }
            background = bg

            val arrow = TextView(this@FloatWindowService).apply {
                text = "◀"
                textSize = 11f
                setTextColor(AndroidColor.WHITE)
                gravity = Gravity.CENTER
            }
            addView(arrow, FrameLayout.LayoutParams(iconSize, iconSize))

            setOnClickListener { expandToolsPanel() }
        }

        val params = WindowManager.LayoutParams(
            iconSize, iconSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth - iconSize
            y = positionY
        }

        windowManager.addView(icon, params)
        collapsedIcon = icon
    }

    private fun expandToolsPanel() {
        if (panelExpanded) return
        panelExpanded = true

        collapsedIcon?.let {
            try { windowManager.removeView(it) } catch (_: Exception) { }
            collapsedIcon = null
        }

        readQuickAccessData()

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val density = displayMetrics.density
        val panelWidth = (38 * density).toInt()
        val occupiedCount = quickAccessItems.count { it != null }
        val topPad = (4 * density).toInt()
        val botPad = (4 * density).toInt()
        val collapseH = (28 * density).toInt()
        val itemH = (32 * density).toInt()
        val itemGap = (2 * density).toInt()
        val estH = topPad + collapseH + botPad +
            occupiedCount * (itemH + itemGap) -
            (if (occupiedCount > 0) itemGap else 0)
        val anchorY = ((screenHeight * 0.3).toInt() - estH / 2)
            .coerceIn(0, screenHeight - estH)

        val scrim = FrameLayout(this).apply {
            setBackgroundColor(AndroidColor.argb(0, 0, 0, 0))
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    collapseToolsPanel()
                    true
                } else false
            }
        }

        val scrimParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
        windowManager.addView(scrim, scrimParams)
        scrimView = scrim

        val verticalLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, topPad, 0, botPad)
        }

        val collapseBtn = TextView(this).apply {
            text = "—"
            textSize = 14f
            setTextColor(AndroidColor.WHITE)
            gravity = Gravity.CENTER
            setOnClickListener { collapseToolsPanel() }
        }
        verticalLayout.addView(collapseBtn, LinearLayout.LayoutParams(panelWidth, collapseH))

        if (occupiedCount > 0) {
            val sep = View(this).apply {
                setBackgroundColor(AndroidColor.argb(60, 255, 255, 255))
            }
            val sepLp = LinearLayout.LayoutParams(panelWidth - (8 * density).toInt(), 1)
            sepLp.gravity = Gravity.CENTER_HORIZONTAL
            verticalLayout.addView(sep, sepLp)
        }

        quickAccessItems.forEach { item ->
            if (item != null) {
                val btnLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding((4 * density).toInt(), (2 * density).toInt(),
                        (4 * density).toInt(), (2 * density).toInt())

                    val bg = GradientDrawable().apply {
                        setColor(AndroidColor.argb(40, 255, 255, 255))
                        cornerRadius = (4 * density).toFloat()
                    }
                    background = bg

                    val label = TextView(this@FloatWindowService).apply {
                        text = item.label
                        textSize = 10f
                        setTextColor(AndroidColor.WHITE)
                        gravity = Gravity.CENTER
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    }
                    addView(label, LinearLayout.LayoutParams(
                        panelWidth, LinearLayout.LayoutParams.WRAP_CONTENT))

                    if (item.badge != null) {
                        val badge = TextView(this@FloatWindowService).apply {
                            text = item.badge
                            textSize = 8f
                            setTextColor(AndroidColor.rgb(255, 98, 0))
                            gravity = Gravity.CENTER
                            maxLines = 1
                        }
                        addView(badge, LinearLayout.LayoutParams(
                            panelWidth, LinearLayout.LayoutParams.WRAP_CONTENT))
                    }

                    setOnClickListener {
                        UrlOpener.open(this@FloatWindowService, item.url, savedKeyword)
                    }
                }
                verticalLayout.addView(btnLayout, LinearLayout.LayoutParams(
                    panelWidth, LinearLayout.LayoutParams.WRAP_CONTENT))
            }
        }

        val panelFrame = FrameLayout(this).apply {
            val bg = GradientDrawable().apply {
                setColor(AndroidColor.argb(140, 20, 20, 20))
                cornerRadii = floatArrayOf(
                    (6 * density).toFloat(), (6 * density).toFloat(),
                    0f, 0f,
                    0f, 0f,
                    (6 * density).toFloat(), (6 * density).toFloat()
                )
            }
            background = bg
            addView(verticalLayout)
        }

        val panelParams = WindowManager.LayoutParams(
            panelWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth - panelWidth
            y = anchorY
        }

        windowManager.addView(panelFrame, panelParams)
        toolsPanelView = panelFrame
    }

    private fun collapseToolsPanel() {
        if (!panelExpanded) return
        panelExpanded = false

        listOfNotNull(scrimView, toolsPanelView).forEach {
            try { windowManager.removeView(it) } catch (_: Exception) { }
        }
        scrimView = null
        toolsPanelView = null

        createCollapsedIcon()
    }

    private fun removeQuickAccessViews() {
        listOfNotNull(toolsPanelView, scrimView, collapsedIcon).forEach {
            try { windowManager.removeView(it) } catch (_: Exception) { }
        }
        toolsPanelView = null
        scrimView = null
        collapsedIcon = null
        panelExpanded = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try {
            removeQuickAccessViews()
            if (::prefs.isInitialized) {
                prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
            }
            if (::floatView.isInitialized) {
                windowManager.removeView(floatView)
            }
        } catch (_: Exception) { }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "FloatWindowService"
    }
}
