package com.MyCarApp.core

import android.annotation.SuppressLint
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.MyCarApp.modules.BaseModule
import com.MyCarApp.modules.door_control.DoorControlModule
import com.MyCarApp.modules.facial_recognition.JavaFacialRecognitionModule

class IntegrationClass private constructor(private val context: Context) {

    private val moduleRegistry: MutableMap<String, BaseModule> = mutableMapOf()
    private var carPropertyManager: CarPropertyManager? = null
    private var facialRecognitionRetries = 0
    private val MAX_RETRIES = 3 // Prevents infinite loops
    private var isRecognitionActive = false

    private val facialRecognitionModule: JavaFacialRecognitionModule =
        JavaFacialRecognitionModule("facial_recognition", context)

    companion object {
        @SuppressLint("StaticFieldLeak")
        private var instance: IntegrationClass? = null

        fun getInstance(context: Context): IntegrationClass {
            if (instance == null) {
                instance = IntegrationClass(context)
            }
            return instance!!
        }
    }

    fun setCarPropertyManager(manager: CarPropertyManager) {
        this.carPropertyManager = manager
    }

    fun initModules() {
        Log.d("IntegrationClass", "Initializing modules...")

        if (!moduleRegistry.containsKey("door_control") && carPropertyManager != null) {
            val doorModule = DoorControlModule(context, carPropertyManager!!) // Pass context
            registerModule("door_control", doorModule)
        }


        if (!moduleRegistry.containsKey("facial_recognition")) {
            registerModule("facial_recognition", facialRecognitionModule)
        }

        moduleRegistry.keys.forEach { id ->
            Log.d("IntegrationClass", "Module registered: $id")
        }

        if (carPropertyManager == null) {
            Log.w("IntegrationClass", "carPropertyManager is null. Skipping door_control registration.")
        } else {
            val doorControlModule = moduleRegistry["door_control"] as? DoorControlModule
            if (doorControlModule?.isCallbackRegistered == false) {
                doorControlModule.registerDoorPropertyCallback()
                Log.d("IntegrationClass", "Registered callback for DOOR_LOCK via DoorControlModule")
            } else {
                Log.d("IntegrationClass", "Callback for DOOR_LOCK was already registered, skipping redundant registration.")
            }
        }

        Log.d("IntegrationClass", "All modules initialized successfully.")
    }

    fun registerModule(id: String, module: BaseModule) {
        if (moduleRegistry.containsKey(id)) {
            Log.w("IntegrationClass", "Module with ID '$id' is already registered.")

            return
        }
        moduleRegistry[id] = module
        Log.d("IntegrationClass", "Registering module: $id")
    }

    fun unregisterModules() {
        Log.d("IntegrationClass", "Unregistering all modules...")

        for ((id, module) in moduleRegistry) {
            module.onDestroy() // Ensure proper cleanup
            Log.d("IntegrationClass", "Unregistered module: $id")
        }

        moduleRegistry.clear() // Clear registry after cleanup
    }

    fun executeModule(id: String, input: OutputObject? = null, imageName: String? = null) {
        val module = moduleRegistry[id]
        if (module == null) {
            Log.e("IntegrationClass", "No module found with ID '$id'")
            return
        }

        val finalInput: OutputObject? = when {
            id == "facial_recognition" && imageName != null -> {
                facialRecognitionModule.processImage(imageName)
                OutputObject(
                    moduleId = id,
                    result = "imageInput",
                    status = false,
                    additionalData = mapOf("imageName" to PropertyResult.Success(imageName))
                ) // Correctly returns OutputObject
            }
            else -> input
        }


        if (finalInput == null) {
            Log.e("IntegrationClass", "No valid input for module: $id")
            return
        }

        Log.d("IntegrationClass", "Executing module: $id with input: $finalInput")
        val output = module.execute(finalInput)
        handleModuleOutput(id, output)

        if (id == "facial_recognition" && output.result == "No Reference Face") {
            if (facialRecognitionRetries < MAX_RETRIES) {
                facialRecognitionRetries++
                Log.d("IntegrationClass", "Retrying facial recognition. Attempt $facialRecognitionRetries")
                executeModule(id, imageName = imageName) // Retry with the same image
            } else {
                Log.e("IntegrationClass", "Max retries reached. Stopping further execution.")
            }
        } else {
            facialRecognitionRetries = 0
        }
    }

    // Properly Call resetMatchTrigger()
    fun stopFacialRecognition() {
        isRecognitionActive = false
        facialRecognitionModule.resetMatchTrigger()
        Log.d("IntegrationClass", "Facial recognition stopped.")
    }

    private fun handleModuleOutput(id: String, output: OutputObject) {
        when (id) {
            "facial_recognition" -> {
                if (output.result == "MatchFound") {
                    Log.d("IntegrationClass", "🚗 Facial match found. Triggering door access workflow.")
                    runDoorAccessWorkflow()
                }
            }
        }
    }

    fun getModule(id: String): BaseModule? {
        return moduleRegistry[id]
    }

    fun runDoorAccessWorkflow() {
        Log.d("IntegrationClass", "🚗 Starting Door Access Workflow.")

        val doorControlModule = getModule("door_control") as? DoorControlModule
        if (doorControlModule == null) {
            Log.e("IntegrationClass", "🚨 DoorControlModule not found. Aborting workflow.")
            return
        }

        val doorStateOutput = doorControlModule.getDoorState()
        val isLocked = (doorStateOutput.additionalData?.get("lockStatus") as? PropertyResult.Success<Boolean>)?.value ?: true
        val doorPosition = (doorStateOutput.additionalData?.get("doorPosition") as? PropertyResult.Success<Int>)?.value ?: 0

        if (isLocked) {
            Log.d("IntegrationClass", "🔓 Unlocking the door...")
            executeModule("door_control",
                OutputObject(moduleId = "door_control", result = "unlock", status = true)
            )
        } else {
            Log.d("IntegrationClass", "🚪 Door is already unlocked.")
        }

        if (doorPosition == 0) {
            Log.d("IntegrationClass", "🔹 Sending open door command...")
            executeModule("door_control", OutputObject(moduleId = "door_control", result = "open", status = true))
        } else {
            Log.d("IntegrationClass", "🚪 Door is already in motion or open.")
        }
    }

}
