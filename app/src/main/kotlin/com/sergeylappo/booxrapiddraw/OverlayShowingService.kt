package com.sergeylappo.booxrapiddraw
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.View
import android.view.View.OnLayoutChangeListener
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
import android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
import android.view.WindowManager.LayoutParams.MATCH_PARENT
import android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.RawInputCallback
import com.onyx.android.sdk.pen.TouchHelper
import com.onyx.android.sdk.pen.data.TouchPointList

private const val CHANNEL_ID = "rapid_draw_channel_overlay_01"
private const val STROKE_WIDTH = 3.0f

class OverlayShowingService : Service() {
    private val paint = Paint()

    private lateinit var touchHelper: TouchHelper
    private lateinit var wm: WindowManager
    private lateinit var overlayPaintingView: SurfaceView
    private var settingsOverlayView: View? = null

    override fun onBind(intent: Intent) = null

    override fun onCreate() {
        super.onCreate()

        createForegroundNotification()

        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        createOverlayPaintingView()

        initPaint()
        initSurfaceView()
    }
    private fun showSettingsOverlay() {
        Toast.makeText(this, "showSettingsOverlay called", Toast.LENGTH_SHORT).show()

        if (settingsOverlayView != null) {
            Toast.makeText(this, "already showing", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val dm = resources.displayMetrics
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            val left = prefs.getInt(PREF_LEFT, 0)
            val top = prefs.getInt(PREF_TOP, 50)
            val right = prefs.getInt(PREF_RIGHT, dm.widthPixels)
            val bottom = prefs.getInt(PREF_BOTTOM, dm.heightPixels - 10)

            Toast.makeText(this, "region: $left $top $right $bottom", Toast.LENGTH_SHORT).show()

            val regionView = RegionSelectorView(
                this,
                RectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
            )

            val saveButton = Button(this).apply {
                text = "Save"
                setOnClickListener {
                    val r = regionView.getRegion()
                    prefs.edit()
                        .putInt(PREF_LEFT, r.left.toInt())
                        .putInt(PREF_TOP, r.top.toInt())
                        .putInt(PREF_RIGHT, r.right.toInt())
                        .putInt(PREF_BOTTOM, r.bottom.toInt())
                        .apply()
                    dismissSettingsOverlay()
                    Toast.makeText(
                        this@OverlayShowingService,
                        "Region saved.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            val cancelButton = Button(this).apply {
                text = "Cancel"
                setOnClickListener { dismissSettingsOverlay() }
            }

            val buttonRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setBackgroundColor(Color.TRANSPARENT)

                val saveParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                saveParams.marginEnd = 16

                val cancelParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )

                addView(saveButton, saveParams)
                addView(cancelButton, cancelParams)
            }

            val container = FrameLayout(this).apply {
                setBackgroundColor(Color.TRANSPARENT)
                addView(
                    regionView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
                addView(
                    buttonRow,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    ).apply { bottomMargin = 20 }
                )
            }

            val params = WindowManager.LayoutParams().apply {
                width = MATCH_PARENT
                height = MATCH_PARENT
                type = TYPE_APPLICATION_OVERLAY
                format = PixelFormat.TRANSLUCENT
                flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                gravity = Gravity.START or Gravity.TOP
                x = 0
                y = 0
            }
            Toast.makeText(this, "adding view to wm", Toast.LENGTH_SHORT).show()
            settingsOverlayView = container
            wm.addView(container, params)
            Toast.makeText(this, "view added successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "ERROR: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun dismissSettingsOverlay() {
        settingsOverlayView?.let {
            wm.removeViewImmediate(it)
            settingsOverlayView = null
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "STOP" -> {
                Toast.makeText(this, "Terminating Rapid Draw Service...", Toast.LENGTH_SHORT).show()
                stopSelf()
                return START_NOT_STICKY
            }
            "SETTINGS" -> {
                showSettingsOverlay()
                return START_STICKY
            }
            else -> {
                Toast.makeText(this, "Starting Rapid Draw Service", Toast.LENGTH_SHORT).show()
                return START_STICKY
            }
        }
    }
    private fun createForegroundNotification() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Boox Rapid draw overlay service",
                NotificationManager.IMPORTANCE_HIGH
            )
        )

        // add notification intent to finish the service
        val pendingIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, OverlayShowingService::class.java).apply { action = "STOP" },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val settingsPendingIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, OverlayShowingService::class.java).apply { action = "SETTINGS" },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.overlay_service_notification_content_title))
            .setContentText(getString(R.string.overlay_service_notification_content))
            .setSmallIcon(R.drawable.rapid_draw)
            .addAction(NotificationCompat.Action.Builder(null, "Stop", pendingIntent).build())
            .addAction(
                NotificationCompat.Action.Builder(null, "Settings", settingsPendingIntent).build()
            )
            .build()

        //noinspection InlinedApi (Seems to work, IDK why, maybe older Android versions might not support this)
        ServiceCompat.startForeground(this, 1, notification, FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    private fun createOverlayPaintingView() {
        overlayPaintingView = SurfaceView(this)
        overlayPaintingView.setZOrderOnTop(true)
        overlayPaintingView.holder.setFormat(PixelFormat.TRANSPARENT)
        overlayPaintingView.alpha = 1.0f

        val params = WindowManager.LayoutParams().apply {
            width = MATCH_PARENT
            height = MATCH_PARENT
            type = TYPE_APPLICATION_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCHABLE
            alpha = 0.2f
            gravity = Gravity.START or Gravity.TOP
        }

        wm.addView(overlayPaintingView, params)
    }

    private fun initPaint() {
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.color = Color.BLACK
        paint.strokeWidth = STROKE_WIDTH
    }

    //    TODO fix suppress
    @SuppressLint("ClickableViewAccessibility")
    private fun initSurfaceView() {
        touchHelper = TouchHelper.create(overlayPaintingView, 2, callback)
        touchHelper.setPenUpRefreshTimeMs(1000)
        overlayPaintingView.addOnLayoutChangeListener(object : OnLayoutChangeListener {
            override fun onLayoutChange(
                v: View,
                left: Int,
                top: Int,
                right: Int,
                bottom: Int,
                oldLeft: Int,
                oldTop: Int,
                oldRight: Int,
                oldBottom: Int
            ) {
                val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val dm = resources.displayMetrics
	
                // Load saved region, defaulting to full screen minus top 50 and bottom 10
                val rLeft = prefs.getInt(PREF_LEFT, 0)
                val rTop = prefs.getInt(PREF_TOP, 50)
                val rRight = prefs.getInt(PREF_RIGHT, dm.widthPixels)
                val rBottom = prefs.getInt(PREF_BOTTOM, dm.heightPixels - 10)
	
                val limitRect = Rect(rLeft, rTop, rRight, rBottom)
	
                touchHelper.setStrokeColor(Color.BLACK)
                touchHelper.setStrokeStyle(TouchHelper.STROKE_STYLE_PENCIL)
                touchHelper.openRawDrawing()
                touchHelper.setStrokeWidth(STROKE_WIDTH).setLimitRect(limitRect, listOf())
                touchHelper.setRawInputReaderEnable(!touchHelper.isRawDrawingInputEnabled)
                overlayPaintingView.addOnLayoutChangeListener(this)
            }
        })

        overlayPaintingView.setOnTouchListener { _: View?, _: MotionEvent? -> true }
    }

    override fun onDestroy() {
        super.onDestroy()
        wm.removeViewImmediate(overlayPaintingView)
        touchHelper.closeRawDrawing()
    }

    private val callback: RawInputCallback = object : RawInputCallback() {
        override fun onBeginRawDrawing(b: Boolean, touchPoint: TouchPoint?) {}

        override fun onEndRawDrawing(b: Boolean, touchPoint: TouchPoint?) {}

        override fun onRawDrawingTouchPointMoveReceived(touchPoint: TouchPoint?) {}

        override fun onPenActive(point: TouchPoint?) {
            touchHelper.setRawDrawingEnabled(true)
        }

        override fun onRawDrawingTouchPointListReceived(touchPointList: TouchPointList) {}

        override fun onBeginRawErasing(b: Boolean, touchPoint: TouchPoint?) {}

        override fun onEndRawErasing(b: Boolean, touchPoint: TouchPoint?) {}

        override fun onRawErasingTouchPointMoveReceived(touchPoint: TouchPoint?) {}

        override fun onRawErasingTouchPointListReceived(touchPointList: TouchPointList?) {}

        override fun onPenUpRefresh(refreshRect: RectF?) {
            touchHelper.isRawDrawingRenderEnabled = false
            super.onPenUpRefresh(refreshRect)
        }
    }
}
