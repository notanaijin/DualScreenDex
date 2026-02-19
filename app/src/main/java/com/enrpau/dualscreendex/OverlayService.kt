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

    private lateinit var windowManager: WindowManager
    private lateinit var overlayRoot: View
    private lateinit var viewModel: MainViewModel
    private lateinit var screenController: MainScreenController

    private var lastPokemonDetectedTime: Long = 0L


    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private val handler = Handler(Looper.getMainLooper())
    private var dimRunnable: Runnable? = null


    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
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

        super.onDestroy()
    }
    private fun startDimTimer() {

        cancelDimTimer()

        dimRunnable = Runnable {

            val now = System.currentTimeMillis()
            val timeSinceLastPokemon = now - lastPokemonDetectedTime

            if (timeSinceLastPokemon >= 5000) {
                screenController.setOverlayDimmed(true)
            }
        }

        handler.postDelayed(dimRunnable!!, 5000)
    }


    private fun cancelDimTimer() {
        dimRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun startDimChecker() {

        handler.post(object : Runnable {
            override fun run() {

                val now = System.currentTimeMillis()
                val timeSinceLastPokemon = now - lastPokemonDetectedTime

                if (timeSinceLastPokemon >= 5000) {
                    screenController.setOverlayDimmed(true)
                }

                handler.postDelayed(this, 1000)
            }
        })
    }


}
