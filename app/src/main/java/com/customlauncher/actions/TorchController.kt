package com.customlauncher.actions

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper

/** Flashlight toggle. setTorchMode needs no CAMERA permission. */
class TorchController(context: Context) {
    private val cameraManager = context.getSystemService(CameraManager::class.java)
    private var torchOn = false

    private val cameraId: String? by lazy {
        runCatching {
            cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
    }

    init {
        cameraManager.registerTorchCallback(object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(id: String, enabled: Boolean) {
                if (id == cameraId) torchOn = enabled
            }
        }, Handler(Looper.getMainLooper()))
    }

    /** @return false when the device has no flash. */
    fun toggle(): Boolean {
        val id = cameraId ?: return false
        return runCatching { cameraManager.setTorchMode(id, !torchOn) }.isSuccess
    }
}
