package com.MyCarApp.modules

import android.util.Log
import com.MyCarApp.core.OutputObject

abstract class BaseModule(protected val moduleId: String) {  // moduleId already has an implicit getter

    abstract fun execute(input: OutputObject?): OutputObject

    open fun onDestroy() {
        Log.d("BaseModule", "Cleaning up resources for module: $moduleId")
    }
}
