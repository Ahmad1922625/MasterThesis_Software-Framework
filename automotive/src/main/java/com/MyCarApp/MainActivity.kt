package com.MyCarApp

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.SurfaceView
import androidx.appcompat.app.AppCompatActivity
import com.MyCarApp.core.IntegrationClass
import android.car.Car
import android.car.hardware.property.CarPropertyManager
import org.opencv.android.OpenCVLoader
import com.MyCarApp.modules.door_control.DoorControlModule
import com.MyCarApp.AnimationManager  // ✅ Import AnimationManager

class MainActivity : AppCompatActivity() {
    private lateinit var integrationClass: IntegrationClass
    private lateinit var doorControlModule: DoorControlModule
    private var car: Car? = null
    private var carPropertyManager: CarPropertyManager? = null


    private lateinit var animationManager: AnimationManager
    private lateinit var surfaceView: SurfaceView

    companion object {
        init {
            System.loadLibrary("filament-jni")
            System.loadLibrary("gltfio-jni")
            System.loadLibrary("filament-utils-jni") // Force-load utils library
        }
    }


    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d("MainActivity", "Car service connected.")

            val manager = car?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
            if (manager == null) {
                Log.e("MainActivity", "CarPropertyManager is null. Facial recognition won't proceed.")
                return
            }

            carPropertyManager = manager

            // Initialize IntegrationClass before using it
            integrationClass = IntegrationClass.getInstance(applicationContext)
            integrationClass.setCarPropertyManager(carPropertyManager!!)
            integrationClass.initModules()

            // Ensure image is valid
            val testImage = "Photo2.jpg"  // Ensure the correct file extension

            Log.d("IntegrationClass", "Executing facial recognition test with: $testImage")
            integrationClass.executeModule("facial_recognition", imageName = "Photo1.jpg")

            // Play animation when service connects
            if (::animationManager.isInitialized) {
                animationManager.playAnimation()
            } else {
                Log.e("MainActivity", "❌ AnimationManager is not initialized yet!")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w("MainActivity", "Car API disconnected.")
            carPropertyManager = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize OpenCV early
        if (OpenCVLoader.initDebug()) {
            Log.d("OpenCV", "OpenCV initialized successfully.")
        } else {
            Log.e("OpenCV", "Failed to initialize OpenCV.")
        }

        // Initialize animationManager BEFORE car service
        surfaceView = findViewById(R.id.animationSurfaceView)
        animationManager = AnimationManager(this, surfaceView) // Move outside `post`

// Start car service after AnimationManager is ready
        car = Car.createCar(this, serviceConnection)
        car?.connect()

    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MainActivity", "Unbinding Car service to prevent leaks.")
        car?.disconnect()

        if (::doorControlModule.isInitialized) {
            Log.d("MainActivity", "Unregistering door property callback")
            doorControlModule.unregisterDoorPropertyCallback()
        }

        // Stop animation to free resources
        if (::animationManager.isInitialized) {
            animationManager.stopAnimation()
        }
    }
}
