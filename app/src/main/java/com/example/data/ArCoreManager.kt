package com.example.data

import android.content.Context
import android.util.Log
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException

class ArCoreManager(private val context: Context) {

    private val TAG = "ArCoreManager"
    var session: Session? = null
        private set

    var isArCoreSupported = false
        private set

    init {
        checkArCoreSupport()
    }

    /**
     * Checks if ARCore is supported on this device.
     */
    private fun checkArCoreSupport() {
        try {
            val availability = com.google.ar.core.ArCoreApk.getInstance().checkAvailability(context)
            isArCoreSupported = availability.isSupported
            Log.d(TAG, "ARCore availability: $availability, supported = $isArCoreSupported")
        } catch (e: Throwable) {
            isArCoreSupported = false
            Log.e(TAG, "Error checking ARCore availability", e)
        }
    }

    private fun findActivity(ctx: Context): android.app.Activity? {
        var currentContext = ctx
        while (currentContext is android.content.ContextWrapper) {
            if (currentContext is android.app.Activity) {
                return currentContext
            }
            currentContext = currentContext.baseContext
        }
        return null
    }

    /**
     * Initializes the ARCore Session with Plane Detection and Motion Tracking.
     * Handles unsupported or uninstalled environments gracefully.
     */
    fun initArSession(): String? {
        if (!isArCoreSupported) {
            return "ARCore is not supported on this device. Sandbox simulation activated."
        }

        val activity = findActivity(context) ?: return "Unable to resolve active Android Activity Context."

        try {
            // Attempt to create session. May request installation if not present on physical device.
            val installStatus = com.google.ar.core.ArCoreApk.getInstance().requestInstall(
                activity,
                true
            )
            if (installStatus == com.google.ar.core.ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                return "ARCore installation requested. Please approve the prompt."
            }

            // Create and configure the ARCore session
            val newSession = Session(context)
            val config = Config(newSession)
            
            // Enable horizontal & vertical plane detection
            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
            // Enable light estimation for visually authentic object overlay placement
            config.lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY
            // Enable update mode LATEST_CAMERA_IMAGE for responsive frame processing
            config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            
            newSession.configure(config)
            session = newSession
            
            Log.i(TAG, "ARCore Session successfully initialized.")
            return null // No error
        } catch (e: UnavailableArcoreNotInstalledException) {
            Log.e(TAG, "ARCore not installed", e)
            return "Please install Google Play Services for AR to use real camera tracking."
        } catch (e: UnavailableApkTooOldException) {
            Log.e(TAG, "ARCore APK too old", e)
            return "Please update Google Play Services for AR to continue."
        } catch (e: UnavailableSdkTooOldException) {
            Log.e(TAG, "ARCore SDK too old", e)
            return "ARCore SDK version mismatch. Fallback activated."
        } catch (e: UnavailableDeviceNotCompatibleException) {
            Log.e(TAG, "Device not compatible with ARCore", e)
            return "This Android device is not compatible with ARCore."
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AR session", e)
            return "Failed to initialize camera sensor fusion: ${e.localizedMessage}"
        }
    }

    /**
     * Performs a frame update, gathering the active trackables, planes, and motion matrices.
     */
    fun onFrameUpdate(): List<com.google.ar.core.Plane> {
        val currentSession = session ?: return emptyList()
        return try {
            val frame = currentSession.update()
            currentSession.getAllTrackables(com.google.ar.core.Plane::class.java).toList()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating AR Frame", e)
            emptyList()
        }
    }

    /**
     * Closes the session safely.
     */
    fun release() {
        try {
            session?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing AR session", e)
        } finally {
            session = null
        }
    }
}
