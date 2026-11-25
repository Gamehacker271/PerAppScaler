package com.gamehac.perappscaler.engine

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.util.Log
import com.gamehac.perappscaler.data.InterventionConfig
import kotlin.math.roundToInt

object GameInterventionEngine {

    private const val TAG = "GameInterventionEngine"
    private const val PREFS_NAME = "per_app_scaler_interventions"
    private const val KEY_PREFIX_SCALE = "scale_"
    private const val KEY_PREFIX_FPS = "fps_"

    private const val PERMISSION_MANAGE_GAME_MODE = "android.permission.MANAGE_GAME_MODE"
    private const val PERMISSION_OVERRIDE_COMPAT = "android.permission.OVERRIDE_COMPAT_CHANGE_CONFIG_ON_RELEASE_BUILD"

    // AOSP WindowManager Compat Change IDs for per-app resolution downscaling (Android 12-17+)
    private const val CHANGE_ID_DOWNSCALED = 168419799L
    private val DOWNSCALE_CHANGE_IDS = mapOf(
        30 to 189970040L,
        35 to 189969749L,
        40 to 189970038L,
        45 to 189969782L,
        50 to 176926741L,
        55 to 189970036L,
        60 to 176926771L,
        65 to 189969744L,
        70 to 176926829L,
        75 to 189969779L,
        80 to 176926753L,
        85 to 189969734L,
        90 to 182811243L
    )

    fun hasPrivilegedPermission(context: Context): Boolean {
        val hasGameMode = context.checkPermission(
            PERMISSION_MANAGE_GAME_MODE,
            Process.myPid(),
            Process.myUid()
        ) == PackageManager.PERMISSION_GRANTED
        val hasCompatOverride = context.checkPermission(
            PERMISSION_OVERRIDE_COMPAT,
            Process.myPid(),
            Process.myUid()
        ) == PackageManager.PERMISSION_GRANTED
        return hasGameMode || hasCompatOverride
    }

    private fun isGameApp(context: Context, packageName: String): Boolean {
        return try {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                info.category == ApplicationInfo.CATEGORY_GAME
            } else {
                @Suppress("DEPRECATION")
                (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun nearestCompatStep(scaleFactor: Float): Int {
        val rawPercent = (scaleFactor * 100f).roundToInt()
        val snapped = ((rawPercent + 2) / 5) * 5
        return snapped.coerceIn(30, 90)
    }

    /**
     * Applies resolution scaling and FPS cap to the target package.
     * Supports both games (via GameManagerService) and regular apps (via PlatformCompat DOWNSCALE).
     */
    fun applyIntervention(
        context: Context,
        packageName: String,
        scaleFactor: Float,
        fpsOverride: Int
    ): Boolean {
        Log.d(TAG, "Applying intervention for $packageName: scale=$scaleFactor, fps=$fpsOverride")

        val isGame = isGameApp(context, packageName)
        var success = false

        if (isGame) {
            // Strategy 1 (Games): Android 13+ (API 33-37+) GameManager.updateCustomGameModeConfiguration
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                success = applyViaGameManagerApi(context, packageName, scaleFactor, fpsOverride)
                if (success) {
                    Log.d(TAG, "Successfully applied via GameManager API")
                }
            }

            // Strategy 2 (Games): Direct Binder invocation on IGameManagerService
            if (!success) {
                success = applyViaBinder(packageName, scaleFactor, fpsOverride)
                if (success) {
                    Log.d(TAG, "Successfully applied via IGameManagerService Binder")
                }
            }
        }

        // Strategy 3 (All Apps & fallback for Games): IPlatformCompat DOWNSCALED + DOWNSCALE_XX
        if (!success && scaleFactor < 0.99f) {
            success = applyViaPlatformCompat(packageName, scaleFactor)
            if (success) {
                Log.d(TAG, "Successfully applied via IPlatformCompat")
            }
        }

        // If user selected 100% and 0 FPS via Apply button, treat as reset
        if (!success && scaleFactor >= 0.99f && fpsOverride <= 0) {
            return resetIntervention(context, packageName)
        }

        // Strategy 4: Shell fallback (works if root su is available)
        if (!success) {
            success = applyViaShellCommand(packageName, scaleFactor, fpsOverride, isGame)
            if (success) {
                Log.d(TAG, "Successfully applied via shell command")
            }
        }

        if (success) {
            saveConfig(context, packageName, scaleFactor, fpsOverride)
            killApp(context, packageName)
            return true
        }

        Log.e(TAG, "All strategies failed for $packageName")
        return false
    }

    /**
     * Resets the package back to native 100% resolution and uncapped FPS.
     */
    fun resetIntervention(context: Context, packageName: String): Boolean {
        Log.d(TAG, "Resetting intervention for $packageName")
        val isGame = isGameApp(context, packageName)
        var success = false

        if (isGame && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (applyViaGameManagerApi(context, packageName, 1.0f, 0, gameMode = 1 /* STANDARD */)) {
                success = true
            }
        }

        if (resetViaPlatformCompat(packageName)) {
            success = true
        }

        if (!success) {
            val gameReset = if (isGame) executeShell("cmd game reset $packageName") else false
            val compatReset = executeShell("am compat reset-all $packageName")
            success = gameReset || compatReset
        }

        removeConfig(context, packageName)
        killApp(context, packageName)
        return success
    }

    @SuppressLint("PrivateApi")
    private fun killApp(context: Context, packageName: String) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        var stopped = false
        if (am != null) {
            try {
                val forceStopMethod = am.javaClass.getMethod("forceStopPackage", String::class.java)
                forceStopMethod.invoke(am, packageName)
                stopped = true
                Log.d(TAG, "forceStopPackage succeeded for $packageName")
            } catch (e: Exception) {
                Log.w(TAG, "forceStopPackage non-fatal: ${e.message}")
            }

            if (!stopped) {
                try {
                    am.killBackgroundProcesses(packageName)
                } catch (e: Exception) {
                    Log.w(TAG, "killBackgroundProcesses non-fatal: ${e.message}")
                }
            }
        }
        if (!stopped) {
            executeShell("am force-stop $packageName")
        }
    }

    /**
     * Resets all modified apps back to native resolution.
     */
    fun resetAll(context: Context): Boolean {
        val allConfigured = getAllConfiguredPackages(context)
        for (pkg in allConfigured.keys) {
            resetIntervention(context, pkg)
        }
        return true
    }

    // --- Strategy 1: Reflection on GameManager System API (Games only) ---
    @SuppressLint("PrivateApi")
    private fun applyViaGameManagerApi(
        context: Context,
        packageName: String,
        scaleFactor: Float,
        fpsOverride: Int,
        gameMode: Int = 4 /* GAME_MODE_CUSTOM */
    ): Boolean {
        return try {
            val gameManager = context.getSystemService(Context.GAME_SERVICE) ?: return false

            val builderClass = Class.forName("android.app.GameModeConfiguration\$Builder")
            val builder = builderClass.getConstructor().newInstance()
            builderClass.getMethod("setScalingFactor", Float::class.javaPrimitiveType)
                .invoke(builder, scaleFactor)
            builderClass.getMethod("setFpsOverride", Int::class.javaPrimitiveType)
                .invoke(builder, fpsOverride)
            val config = builderClass.getMethod("build").invoke(builder)

            val updateMethod = gameManager.javaClass.getMethod(
                "updateCustomGameModeConfiguration",
                String::class.java,
                Class.forName("android.app.GameModeConfiguration")
            )
            updateMethod.invoke(gameManager, packageName, config)

            val setGameModeMethod = gameManager.javaClass.getMethod(
                "setGameMode",
                String::class.java,
                Int::class.javaPrimitiveType
            )
            setGameModeMethod.invoke(gameManager, packageName, gameMode)

            true
        } catch (e: Exception) {
            Log.w(TAG, "GameManager System API failed for $packageName: ${e.cause?.message ?: e.message}")
            false
        }
    }

    // --- Strategy 2: Direct Binder call on ServiceManager.getService("game") (Games only) ---
    @SuppressLint("PrivateApi")
    private fun applyViaBinder(
        packageName: String,
        scaleFactor: Float,
        fpsOverride: Int
    ): Boolean {
        return try {
            val smClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = smClass.getMethod("getService", String::class.java)
            val binder = getServiceMethod.invoke(null, "game") as? IBinder ?: return false

            val stubClass = Class.forName("android.app.IGameManagerService\$Stub")
            val service = stubClass.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
                ?: return false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val builderClass = Class.forName("android.app.GameModeConfiguration\$Builder")
                val builder = builderClass.getConstructor().newInstance()
                builderClass.getMethod("setScalingFactor", Float::class.javaPrimitiveType)
                    .invoke(builder, scaleFactor)
                builderClass.getMethod("setFpsOverride", Int::class.javaPrimitiveType)
                    .invoke(builder, fpsOverride)
                val config = builderClass.getMethod("build").invoke(builder)

                service.javaClass.getMethod(
                    "updateCustomGameModeConfiguration",
                    String::class.java,
                    Class.forName("android.app.GameModeConfiguration"),
                    Int::class.javaPrimitiveType
                ).invoke(service, packageName, config, 0 /* userId */)

                service.javaClass.getMethod(
                    "setGameMode",
                    String::class.java,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                ).invoke(service, packageName, 4 /* GAME_MODE_CUSTOM */, 0 /* userId */)
                return true
            }
            false
        } catch (e: Exception) {
            Log.w(TAG, "IGameManagerService Binder failed for $packageName: ${e.cause?.message ?: e.message}")
            false
        }
    }

    // --- Strategy 3: IPlatformCompat DOWNSCALED + DOWNSCALE_XX (Works for ALL apps & games) ---
    @SuppressLint("PrivateApi")
    private fun getPlatformCompatService(): Any? {
        return try {
            val smClass = Class.forName("android.os.ServiceManager")
            val binder = smClass.getMethod("getService", String::class.java)
                .invoke(null, "platform_compat") as? IBinder ?: return null
            val stubClass = Class.forName("com.android.internal.compat.IPlatformCompat\$Stub")
            stubClass.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to get IPlatformCompat: ${e.cause?.message ?: e.message}")
            null
        }
    }

    @SuppressLint("PrivateApi")
    private fun applyViaPlatformCompat(packageName: String, scaleFactor: Float): Boolean {
        val service = getPlatformCompatService() ?: return false
        val step = nearestCompatStep(scaleFactor)
        val targetChangeId = DOWNSCALE_CHANGE_IDS[step] ?: return false

        // Attempt 3A: Release builds API (putOverridesOnReleaseBuilds)
        try {
            val pkgOverrideBuilderClass = Class.forName("android.app.compat.PackageOverride\$Builder")
            val buildMethod = pkgOverrideBuilderClass.getMethod("build")
            val setEnabledMethod = pkgOverrideBuilderClass.getMethod("setEnabled", Boolean::class.javaPrimitiveType)

            fun makeOverride(enabled: Boolean): Any {
                val b = pkgOverrideBuilderClass.getConstructor().newInstance()
                setEnabledMethod.invoke(b, enabled)
                return buildMethod.invoke(b)!!
            }

            val overridesMap = HashMap<Long, Any>()
            overridesMap[CHANGE_ID_DOWNSCALED] = makeOverride(true)
            for ((pct, id) in DOWNSCALE_CHANGE_IDS) {
                overridesMap[id] = makeOverride(pct == step)
            }

            val overrideConfigClass = Class.forName("com.android.internal.compat.CompatibilityOverrideConfig")
            val overrideConfig = overrideConfigClass.getConstructor(Map::class.java).newInstance(overridesMap)

            service.javaClass.getMethod(
                "putOverridesOnReleaseBuilds",
                overrideConfigClass,
                String::class.java
            ).invoke(service, overrideConfig, packageName)
            return true
        } catch (e: Exception) {
            Log.w(TAG, "putOverridesOnReleaseBuilds failed for $packageName: ${e.cause?.message ?: e.message}")
        }

        // Attempt 3B: Debug/Eng builds API (setOverrides)
        try {
            val enabledSet = HashSet<Long>().apply {
                add(CHANGE_ID_DOWNSCALED)
                add(targetChangeId)
            }
            val disabledSet = HashSet<Long>().apply {
                for ((pct, id) in DOWNSCALE_CHANGE_IDS) {
                    if (pct != step) add(id)
                }
            }
            val innerConfigClass = Class.forName("android.compat.Compatibility\$ChangeConfig")
            val innerConfig = innerConfigClass.getConstructor(Set::class.java, Set::class.java)
                .newInstance(enabledSet, disabledSet)
            val compatConfigClass = Class.forName("com.android.internal.compat.CompatibilityChangeConfig")
            val compatConfig = compatConfigClass.getConstructor(innerConfigClass).newInstance(innerConfig)

            service.javaClass.getMethod(
                "setOverrides",
                compatConfigClass,
                String::class.java
            ).invoke(service, compatConfig, packageName)
            return true
        } catch (e: Exception) {
            Log.w(TAG, "setOverrides failed for $packageName: ${e.cause?.message ?: e.message}")
        }

        return false
    }

    @SuppressLint("PrivateApi")
    private fun resetViaPlatformCompat(packageName: String): Boolean {
        val service = getPlatformCompatService() ?: return false
        var cleared = false

        try {
            val allIds = HashSet<Long>().apply {
                add(CHANGE_ID_DOWNSCALED)
                addAll(DOWNSCALE_CHANGE_IDS.values)
            }
            val removeConfigClass = Class.forName("com.android.internal.compat.CompatibilityOverridesToRemoveConfig")
            val removeConfig = removeConfigClass.getConstructor(Set::class.java).newInstance(allIds)
            service.javaClass.getMethod(
                "removeOverridesOnReleaseBuilds",
                removeConfigClass,
                String::class.java
            ).invoke(service, removeConfig, packageName)
            cleared = true
        } catch (e: Exception) {
            Log.w(TAG, "removeOverridesOnReleaseBuilds failed for $packageName: ${e.cause?.message ?: e.message}")
        }

        try {
            service.javaClass.getMethod("clearOverrides", String::class.java)
                .invoke(service, packageName)
            cleared = true
        } catch (e: Exception) {
            Log.w(TAG, "clearOverrides failed for $packageName: ${e.cause?.message ?: e.message}")
        }

        return cleared
    }

    // --- Strategy 4: Root Shell fallback ---
    private fun applyViaShellCommand(
        packageName: String,
        scaleFactor: Float,
        fpsOverride: Int,
        isGame: Boolean
    ): Boolean {
        if (isGame) {
            val scaleStr = if (scaleFactor >= 0.99f) "disable" else String.format(java.util.Locale.US, "%.2f", scaleFactor)
            val fpsStr = if (fpsOverride > 0) "--fps $fpsOverride" else ""
            if (executeShell("cmd game set --downscale $scaleStr $fpsStr $packageName")) {
                return true
            }
        }
        if (scaleFactor < 0.99f) {
            val step = nearestCompatStep(scaleFactor)
            val cmd = "am compat reset-all $packageName && am compat enable DOWNSCALED $packageName && am compat enable DOWNSCALE_$step $packageName"
            return executeShell(cmd)
        }
        return false
    }

    private fun executeShell(command: String): Boolean {
        val shells = arrayOf("su", "/system/bin/su")
        for (sh in shells) {
            try {
                val process = Runtime.getRuntime().exec(arrayOf(sh, "-c", command))
                val exitCode = process.waitFor()
                if (exitCode == 0) return true
            } catch (e: Exception) {
                // Ignore and try next shell
            }
        }
        return false
    }

    // --- Preferences & Persistence ---
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveConfig(context: Context, packageName: String, scaleFactor: Float, fpsOverride: Int) {
        val prefs = getPrefs(context)
        prefs.edit()
            .putFloat(KEY_PREFIX_SCALE + packageName, scaleFactor)
            .putInt(KEY_PREFIX_FPS + packageName, fpsOverride)
            .apply()
    }

    fun removeConfig(context: Context, packageName: String) {
        val prefs = getPrefs(context)
        prefs.edit()
            .remove(KEY_PREFIX_SCALE + packageName)
            .remove(KEY_PREFIX_FPS + packageName)
            .apply()
    }

    fun getConfig(context: Context, packageName: String): InterventionConfig {
        val prefs = getPrefs(context)
        val scale = prefs.getFloat(KEY_PREFIX_SCALE + packageName, 1.0f)
        val fps = prefs.getInt(KEY_PREFIX_FPS + packageName, 0)
        return InterventionConfig(scale, fps)
    }

    fun getAllConfiguredPackages(context: Context): Map<String, InterventionConfig> {
        val prefs = getPrefs(context)
        val result = mutableMapOf<String, InterventionConfig>()
        val all = prefs.all
        for ((key, value) in all) {
            if (key.startsWith(KEY_PREFIX_SCALE)) {
                val pkg = key.removePrefix(KEY_PREFIX_SCALE)
                val scale = (value as? Float) ?: 1.0f
                val fps = prefs.getInt(KEY_PREFIX_FPS + pkg, 0)
                if (scale < 0.99f || fps > 0) {
                    result[pkg] = InterventionConfig(scale, fps)
                }
            }
        }
        return result
    }
}
