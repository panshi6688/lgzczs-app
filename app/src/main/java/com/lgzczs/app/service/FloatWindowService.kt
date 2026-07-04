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
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.TextView
import com.lgzczs.app.MainActivity
import com.lgzczs.app.util.TokenManager

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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try {
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
