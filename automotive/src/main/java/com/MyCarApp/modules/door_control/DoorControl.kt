package com.MyCarApp.modules.door_control

import android.util.Log
import android.car.VehiclePropertyIds
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import com.MyCarApp.core.OutputObject
import com.MyCarApp.core.PropertyResult
import com.MyCarApp.modules.BaseModule
import com.MyCarApp.core.IntegrationClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


open class DoorControlModule(
    private val context: Context,  // Pass context here
    private val carPropertyManager: CarPropertyManager
) : BaseModule("door_control") {

    private val TAG = "DoorControlModule"
    private var lastDoorLockCommand: Boolean? = null
    var isCallbackRegistered = false

    override fun execute(input: OutputObject?): OutputObject {
        Log.d(TAG, "execute() called with input: $input")

        return when (input?.result) {
            "unlock" -> unlockDoor()
            "open" -> {
                val maxPos = carPropertyManager.getIntProperty(VehiclePropertyIds.DOOR_POS, VehicleAreaDoor.ROW_1_LEFT)
                setDoorPosition(targetPosition = maxPos)
            }
            else -> OutputObject(moduleId, "InvalidAction", false)
        }
    }





    fun getDoorState(areaId: Int = VehicleAreaDoor.ROW_1_LEFT): OutputObject {
        Log.d(TAG, "Retrieving door state for area ID: $areaId")

        return try {
            val lockStatus = carPropertyManager.getBooleanProperty(VehiclePropertyIds.DOOR_LOCK, areaId)
            val doorPosition = carPropertyManager.getIntProperty(VehiclePropertyIds.DOOR_MOVE, areaId)

            Log.d(TAG, "Lock Status = $lockStatus, Door Position = $doorPosition")

            OutputObject(
                moduleId = moduleId,
                result = "DoorState",
                status = true,
                additionalData = mapOf(
                    "lockStatus" to PropertyResult.Success(lockStatus),
                    "doorPosition" to PropertyResult.Success(doorPosition)
                )
            )

        } catch (ex: Exception) {
            Log.e(TAG, "Error retrieving door state for area ID $areaId - ${ex.message}")

            OutputObject(
                moduleId = moduleId,
                result = "Error: Exception retrieving door state",
                status = false,
                additionalData = mapOf(
                    "exception" to PropertyResult.Error(ex.message ?: "Unknown error")
                )
            )
        }
    }


    fun unlockDoor(areaId: Int = VehicleAreaDoor.ROW_1_LEFT): OutputObject {
        Log.d(TAG, " Attempting to unlock door: Setting DOOR_LOCK to false for area ID: $areaId")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                lastDoorLockCommand = false // ✅ Store the last requested state
                val timestampBeforeSet = System.currentTimeMillis()

                val propertyList = carPropertyManager.propertyList
                val doorLockConfig = propertyList.firstOrNull { it.propertyId == VehiclePropertyIds.DOOR_LOCK }

                doorLockConfig?.let {
                    Log.d(TAG, "🚗 DOOR_LOCK access mode: ${it.access}")
                } ?: Log.e(TAG, "🚨 DOOR_LOCK property not found in property list.")

                // Execute property change on background thread
                carPropertyManager.setBooleanProperty(VehiclePropertyIds.DOOR_LOCK, areaId, false)
                Log.d(TAG, "⚠️ DOOR_LOCK set command sent at $timestampBeforeSet, waiting for confirmation...")

                delay(500) // Wait to check if change persists

                val updatedLockStatus = carPropertyManager.getBooleanProperty(VehiclePropertyIds.DOOR_LOCK, areaId)
                val timestampAfterSet = System.currentTimeMillis()
                Log.d(TAG, "✅ DOOR_LOCK state after command: $updatedLockStatus (Expected: false) at $timestampAfterSet")

                if (updatedLockStatus) {
                    Log.w(TAG, "🚨 DOOR_LOCK was overridden! Expected false, but got true.")
                }

            } catch (ex: Exception) {
                Log.e(TAG, "❌ Failed to unlock door for area ID $areaId - ${ex.message}")
            }
        }

        return OutputObject(
            moduleId = moduleId,
            result = "DoorUnlockCommandSent",
            status = true
        )
    }





    fun openDoor(areaId: Int = VehicleAreaDoor.ROW_1_LEFT): OutputObject {
        Log.d(TAG, "Attempting to open door: Setting DOOR_MOVE for area ID: $areaId")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val propertyList = carPropertyManager.propertyList
                val doorMoveConfig = propertyList.firstOrNull { it.propertyId == VehiclePropertyIds.DOOR_MOVE }

                // ✅ Ensure default values before accessing property config
                var minSpeed = -1
                var maxSpeed = 1

                doorMoveConfig?.let {
                    minSpeed = it.getMinValue() as? Int ?: -1
                    maxSpeed = it.getMaxValue() as? Int ?: 1
                    Log.d(TAG, "🚗 DOOR_MOVE access mode: ${it.access}, Allowed range: [$minSpeed, $maxSpeed]")
                } ?: Log.e(TAG, "🚨 DOOR_MOVE property not found in property list.")

                val timestampStart = System.currentTimeMillis()

                // Execute property change on background thread
                carPropertyManager.setIntProperty(VehiclePropertyIds.DOOR_MOVE, areaId, maxSpeed)
                Log.d(TAG, "⚠️ DOOR_MOVE set command sent at $timestampStart, speed: $maxSpeed, waiting for confirmation...")

                delay(500) // Allow time for property update

                // Poll the property to confirm if DOOR_MOVE has actually changed
                val updatedDoorState = carPropertyManager.getIntProperty(VehiclePropertyIds.DOOR_MOVE, areaId)
                val timestampEnd = System.currentTimeMillis()

                Log.d(TAG, "✅ DOOR_MOVE state after command: $updatedDoorState (Expected: $maxSpeed) at $timestampEnd")

                // ✅ If movement happened, ensure we set it back to 0 to stop movement
                if (updatedDoorState == maxSpeed) {
                    delay(1000) // Wait for door to move
                    carPropertyManager.setIntProperty(VehiclePropertyIds.DOOR_MOVE, areaId, 0)
                    Log.d(TAG, "🛑 DOOR_MOVE set to 0 to stop movement.")
                }

            } catch (ex: Exception) {
                Log.e(TAG, "❌ Failed to open door for area ID $areaId - ${ex.message}")
            }
        }

        return OutputObject(
            moduleId = moduleId,
            result = "DoorOpenCommandSent",
            status = true
        )
    }








    object VehicleAreaDoor {
        const val ROW_1_LEFT = 0x1
        const val ROW_1_RIGHT = 0x4
        const val ROW_2_LEFT = 0x10
        const val ROW_2_RIGHT = 0x40
    }


    fun registerDoorPropertyCallback() {
        if (!isCallbackRegistered) {
            carPropertyManager.registerCallback(carPropertyListener, VehiclePropertyIds.DOOR_LOCK, CarPropertyManager.SENSOR_RATE_ONCHANGE)
            isCallbackRegistered = true
            Log.d(TAG, "Registered callback for DOOR_LOCK")
        } else {
           Log.d(TAG, "Callback for DOOR_LOCK already registered, skipping.")
        }
    }

    fun unregisterDoorPropertyCallback() {
        if (isCallbackRegistered) {
            carPropertyManager.unregisterCallback(carPropertyListener, VehiclePropertyIds.DOOR_LOCK)
            isCallbackRegistered = false
            Log.d(TAG, "Unregistered callback for DOOR_LOCK")
        } else {
            Log.d(TAG, "No callback registered for DOOR_LOCK, skipping unregistration.")
        }
    }


    private val carPropertyListener = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: android.car.hardware.CarPropertyValue<*>) {
            val updatedLockStatus = value.value as Boolean
            Log.d(TAG, "🚨 DOOR_LOCK changed: ${value.propertyId}, New Value: $updatedLockStatus (Confirmation received)")

            // Confirm state change matches last requested command
            if (lastDoorLockCommand != null && updatedLockStatus == lastDoorLockCommand) {
                Log.d(TAG, "✅ DOOR_LOCK state confirmed: $updatedLockStatus")

                val output = OutputObject(
                    moduleId = "door_control",
                    result = if (updatedLockStatus) "DoorUnlocked" else "DoorLocked",
                    status = true
                )

                // Instead of notifyCompletion(), pass output to IntegrationClass
                IntegrationClass.getInstance(context).executeModule("door_control", output)

            }
        }


    override fun onErrorEvent(propertyId: Int, areaId: Int) {
            Log.e(TAG, "Error accessing property: $propertyId")
        }
    }

    fun setDoorPosition(areaId: Int = VehicleAreaDoor.ROW_1_LEFT, targetPosition: Int): OutputObject {
        Log.d(TAG, "Attempting to set DOOR_POS to $targetPosition for area ID: $areaId")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val propertyList = carPropertyManager.propertyList
                val doorPosConfig = propertyList.firstOrNull { it.propertyId == VehiclePropertyIds.DOOR_POS }

                val minPos = doorPosConfig?.getMinValue() as? Int ?: 0  // Get min position dynamically
                val maxPos = doorPosConfig?.getMaxValue() as? Int ?: 1 // Get max position dynamically

                Log.d(TAG, "🚗 DOOR_POS access mode: ${doorPosConfig?.access}, Allowed range: [$minPos, $maxPos]")


                carPropertyManager.setIntProperty(VehiclePropertyIds.DOOR_POS, areaId, maxPos)
                Log.d(TAG, "⚠️ DOOR_POS set command sent, waiting for confirmation...")

                delay(500)

                // Poll property to confirm the update
                val updatedDoorPos = carPropertyManager.getIntProperty(VehiclePropertyIds.DOOR_POS, areaId)
                Log.d(TAG, "🛑 DOOR_MOVE set to 0 to stop movement.")
                Log.d(TAG, "✅ DOOR_POS state after command: $updatedDoorPos (Expected: $maxPos)")


                if (updatedDoorPos != maxPos) {
                    Log.w(TAG, "🚨 DOOR_POS was overridden! Expected $maxPos, but got $updatedDoorPos.")
                }

            } catch (ex: Exception) {
                Log.e(TAG, "❌ Failed to set DOOR_POS for area ID $areaId - ${ex.message}")
            }
        }

        return OutputObject(
            moduleId = moduleId,
            result = "DoorPositionSet",
            status = true
        )
    }

}


