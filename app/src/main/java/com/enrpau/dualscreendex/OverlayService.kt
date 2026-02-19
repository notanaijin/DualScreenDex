package com.enrpau.dualscreendex

import android.app.Service
import android.content.*
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.*
import android.widget.ImageButton
import androidx.appcompat.view.ContextThemeWrapper
import androidx.lifecycle.ViewModelProvider
import android.os.Handler
import android.os.Looper


class OverlayService : Service() {
    
    private lateinit var prefListener: SharedPreferences.OnSharedPreferenceChangeListener

    private lateinit var windowManager: WindowManager
    private lateinit var overlayRoot: View
    private lateinit var viewModel: MainViewModel
    private lateinit var screenController: MainScreenController

    private lateinit var prefs: SharedPreferences
    private lateinit var displayMetrics: android.util.DisplayMetrics

    private var lastPokemonDetectedTime: Long = 0L

    private val DIM_DELAY_MS = 10000L

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private val handler = Handler(Looper.getMainLooper())
    


    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {

        prefs = getSharedPreferences("DualDexPrefs", Context.MODE_PRIVATE)

        displayMetrics = resources.displayMetrics

        super.onCreate()

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val themedContext = ContextThemeWrapper(this, R.style.Theme_DualScreenDex)

        overlayRoot = LayoutInflater.from(themedContext)
            .inflate(R.layout.overlay_container, null)

        viewModel = ViewModelProvider.AndroidViewModelFactory
            .getInstance(application)
            .create(MainViewModel::class.java)

        screenController = MainScreenController(
            context = themedContext,
            root = overlayRoot,
            viewModel = viewModel,
            isOverlay = true
        )

        // 🔥 Watch real detected Pokémon only
        viewModel.displayedPokemon.observeForever { pokemon ->
            if (pokemon != null) {
                lastPokemonDetectedTime = System.currentTimeMillis()
                screenController.setOverlayDimmed(false)
            }
        }

        prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "SCAN_ALIGN") {
                val layoutParams = overlayRoot.layoutParams as WindowManager.LayoutParams
                enforceOverlaySide(layoutParams)
            }
        }

        prefs.registerOnSharedPreferenceChangeListener(prefListener)







        registerReceiver()

        val params = WindowManager.LayoutParams(
            960,
            540,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 100

        windowManager.addView(overlayRoot, params)

        enforceOverlaySide(params)


        // Initial scale
        screenController.scaleUI(params.width)

        enableDrag(params)
        enableResize(params)
        enableCloseButton()
        startDimChecker()

    }

    private val pokemonReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {

            val found = intent.getBooleanExtra("FOUND", false)

            if (!found) {
                viewModel.onScanResult(null, null, null, null)
                return
            }


            

            viewModel.onScanResult(
                intent.getStringArrayListExtra("NAMES"),
                intent.getIntegerArrayListExtra("IDS"),
                intent.getStringArrayListExtra("TYPE1S"),
                intent.getStringArrayListExtra("TYPE2S")
            )

            viewModel.onBattleTabClicked()
        }
    }


    private fun registerReceiver() {
        val filter = IntentFilter("com.enrpau.dualscreendex.POKEMON_DETECTED")
        registerReceiver(pokemonReceiver, filter)
    }

    private fun enableDrag(params: WindowManager.LayoutParams) {
        val dragBar = overlayRoot.findViewById<View>(R.id.overlayDragBar)

        dragBar.setOnTouchListener { _, event ->
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

                    enforceOverlaySide(params)

                    windowManager.updateViewLayout(overlayRoot, params)

                    true
                }


                else -> false
            }
        }
    }

    private fun enableResize(params: WindowManager.LayoutParams) {
        val resizeHandle = overlayRoot.findViewById<View>(R.id.overlayResizeHandle)

        resizeHandle.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_MOVE) {
                val newWidth = event.rawX.toInt() - params.x
                val newHeight = (newWidth * 9) / 16

                if (newWidth > 500 && newHeight > 300) {
                    params.width = newWidth
                    params.height = newHeight
                    windowManager.updateViewLayout(overlayRoot, params)

                    // 🔥 Scale UI dynamically
                    screenController.scaleUI(params.width)
                }
            }
            true
        }
    }

    private fun enableCloseButton() {
        val closeBtn = overlayRoot.findViewById<ImageButton>(R.id.overlayCloseBtn)
        closeBtn.setOnClickListener {
            stopSelf()
        }
    }

    override fun onDestroy() {
        unregisterReceiver(pokemonReceiver)

        if (::overlayRoot.isInitialized) {
            windowManager.removeView(overlayRoot)
        }
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)

        super.onDestroy()
    }
    


    

    private fun startDimChecker() {

        handler.post(object : Runnable {
            override fun run() {

                val now = System.currentTimeMillis()
                val timeSinceLastPokemon = now - lastPokemonDetectedTime

                if (timeSinceLastPokemon >= DIM_DELAY_MS) {
                    screenController.setOverlayDimmed(true)
                }

                handler.postDelayed(this, 1000)
            }
        })
    }

    private fun enforceOverlaySide(params: WindowManager.LayoutParams) {

        val scanAlign = prefs.getString("SCAN_ALIGN", "left") ?: "left"

        val screenWidth = displayMetrics.widthPixels
        val overlayWidth = params.width

        val screenMid = screenWidth / 2

        if (scanAlign == "left") {
            // OCR scans LEFT → overlay must stay RIGHT
            if (params.x < screenMid) {
                params.x = screenMid
            }
        } else {
            // OCR scans RIGHT → overlay must stay LEFT
            if (params.x + overlayWidth > screenMid) {
                params.x = screenMid - overlayWidth
            }
        }

        // Clamp inside screen
        if (params.x < 0) params.x = 0
        if (params.x + overlayWidth > screenWidth) {
            params.x = screenWidth - overlayWidth
        }

        windowManager.updateViewLayout(overlayRoot, params)
    }




}
