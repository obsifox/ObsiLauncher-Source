package com.movtery.zalithlauncher.setting

/**
 * Minimal settings shims so the vendored Pojav/Zalith input classes compile and
 * behave sanely. Values are simple mutable states; some are wired to real
 * ObsiLauncher settings by the game screen at runtime.
 * GPL-3.0-or-later — ObsiLauncher port.
 */
object AllStaticSettings {
    /** Render resolution scale: game pixels per screen pixel (0.5 = half res). */
    @JvmField
    var scaleFactor: Float = 0.5f

    @JvmField
    var disableDoubleTap: Boolean = false

    @JvmField
    var useControllerProxy: Boolean = false

    @JvmField
    var gyroSensitivity: Float = 1.0f

    @JvmField
    var gyroInvertX: Boolean = false

    @JvmField
    var gyroInvertY: Boolean = false

    /** ms of stillness before a long-press fires */
    @JvmField
    var timeLongPressTrigger: Int = 300
}

object AllSettings {
    // simple in-memory settings with defaults (wired by ObsiSettings where useful)
    private val mouseSpeed = kotlinx.coroutines.flow.MutableStateFlow(100)
    private val mouseScale = kotlinx.coroutines.flow.MutableStateFlow(100)
    private val disableGestures = kotlinx.coroutines.flow.MutableStateFlow(false)
    private val alternateSurface = kotlinx.coroutines.flow.MutableStateFlow(true)
    private val gyroSampleRate = kotlinx.coroutines.flow.MutableStateFlow(60)
    private val gyroSmoothing = kotlinx.coroutines.flow.MutableStateFlow(true)
    private val gyroSensitivity = kotlinx.coroutines.flow.MutableStateFlow(1.0f)
    private val deadZoneScale = kotlinx.coroutines.flow.MutableStateFlow(100)
    private val buttonScale = kotlinx.coroutines.flow.MutableStateFlow(100)
    private val buttonAllCaps = kotlinx.coroutines.flow.MutableStateFlow(false)
    private val buttonSnapping = kotlinx.coroutines.flow.MutableStateFlow(false)
    private val buttonSnappingDistance = kotlinx.coroutines.flow.MutableStateFlow(16)
    private val hotbarWidth = kotlinx.coroutines.flow.MutableStateFlow(50)
    private val hotbarHeight = kotlinx.coroutines.flow.MutableStateFlow(10)
    private val defaultCtrl = kotlinx.coroutines.flow.MutableStateFlow("")

    @JvmStatic fun getMouseSpeed() = mouseSpeed
    @JvmStatic fun getMouseScale() = mouseScale
    @JvmStatic fun getDisableGestures() = disableGestures
    @JvmStatic fun getAlternateSurface() = alternateSurface
    @JvmStatic fun getGyroSampleRate() = gyroSampleRate
    @JvmStatic fun getGyroSmoothing() = gyroSmoothing
    @JvmStatic fun getGyroSensitivity() = gyroSensitivity
    @JvmStatic fun getDeadZoneScale() = deadZoneScale
    @JvmStatic fun getButtonScale() = buttonScale
    @JvmStatic fun getButtonAllCaps() = buttonAllCaps
    @JvmStatic fun getButtonSnapping() = buttonSnapping
    @JvmStatic fun getButtonSnappingDistance() = buttonSnappingDistance
    @JvmStatic fun getHotbarWidth() = hotbarWidth
    @JvmStatic fun getHotbarHeight() = hotbarHeight
    @JvmStatic fun getDefaultCtrl() = defaultCtrl
}
