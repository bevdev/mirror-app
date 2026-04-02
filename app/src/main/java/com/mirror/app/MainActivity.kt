package com.mirror.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var zoomContainer: LinearLayout
    private var camera: Camera? = null
    private var activeZoom = 1.0f

    private val candidateZooms = listOf(0.5f, 0.7f, 1.0f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val root = FrameLayout(this)

        previewView = PreviewView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        root.addView(previewView)

        zoomContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).also { lp ->
                lp.gravity = Gravity.BOTTOM or Gravity.START
                lp.setMargins(dp(20), 0, 0, dp(40))
            }
        }
        root.addView(zoomContainer)

        setContentView(root)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            finish()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                this, CameraSelector.DEFAULT_FRONT_CAMERA, preview
            )
            setupZoomButtons()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun setupZoomButtons() {
        val cam = camera ?: return
        cam.cameraInfo.zoomState.observe(this) { zoomState ->
            cam.cameraInfo.zoomState.removeObservers(this)

            val minZoom = zoomState.minZoomRatio
            val available = candidateZooms.filter { it >= minZoom - 0.05f }

            zoomContainer.removeAllViews()
            available.forEach { zoom ->
                val label = when {
                    zoom <= 0.5f -> ".5\u00D7"
                    zoom <= 0.7f -> ".7\u00D7"
                    else -> "1\u00D7"
                }
                val tv = TextView(this).apply {
                    text = label
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                    tag = zoom
                    setOnClickListener { selectZoom(zoom) }
                }
                zoomContainer.addView(tv)
            }

            selectZoom(1.0f)
        }
    }

    private fun selectZoom(zoom: Float) {
        activeZoom = zoom
        camera?.cameraControl?.setZoomRatio(zoom)
        for (i in 0 until zoomContainer.childCount) {
            val tv = zoomContainer.getChildAt(i) as? TextView ?: continue
            val btnZoom = tv.tag as? Float ?: continue
            if (btnZoom == zoom) {
                tv.setTextColor(Color.WHITE)
                tv.textSize = 16f
                tv.setTypeface(null, Typeface.BOLD)
            } else {
                tv.setTextColor(Color.parseColor("#80FFFFFF"))
                tv.textSize = 14f
                tv.setTypeface(null, Typeface.NORMAL)
            }
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
