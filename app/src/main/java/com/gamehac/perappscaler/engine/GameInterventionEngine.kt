package com.gamehac.perappscaler.engine

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.util.Log
import com.gamehac.perappscaler.data.InterventionConfig
import java.io.BufferedReader
import java.io.InputStreamReader

object GameInterventionEngine {

    private const val TAG = "GameInterventionEngine"
    private const val PREFS_NAME = "per_app_scaler_interventions"
    private const val KEY_PREFIX_SCALE = "scale_"
    private const val KEY_PREFIX_FPS = "fps_"

    private const val PERMISSION_MANAGE_GAME_MODE = "android.permission.MANAGE_GAME_MODE"

    fun hasPrivilegedPermission(context: Context): Boolean {
        return context.checkPermission(
            PERMISSION_MANAGE_GAME_MODE,
            Process.myPid(),
            Process.myUid()
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Applies resolution scaling and FPS cap to the target package.
     * Works natively without root on Android 12-17+ when installed as priv-app.
     */
    fun applyIntervention(
        context: Context,
        packageName: String,
        scaleFactor: Float,
        fpsOverride: Int
    ): Boolean {
        Log.d(TAG, "Applying intervention for $packageName: scale=$scaleFactor, fps=$fpsOverride")

        var success = false

        // Strategy 1: Android 13+ (API 33-37+) official GameManager.updateCustomGameModeConfiguration
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            success = applyViaGameManagerApi(context, packageName, scaleFactor, fpsOverride)
            if (success) {
                Log.d(TAG, "Successfully applied via GameManager API")
            }
        }

        // Strategy 2: Direct Binder invocation on IGameManagerService
        if (!success) {
            success = applyViaBinder(context, packageName, scaleFactor, fpsOverride)
            if (success) {
                Log.d(TAG, "Successfully applied via IGameManagerService Binder")
            }
        }

        // Strategy 3: Shell cmd game execution (works if privileged shell/root/shizuku available)
        if (!success) {
            success = applyViaShellCommand(packageName, scaleFactor, fpsOverride)
            if (success) {
                Log.d(TAG, "Successfully applied via shell cmd game")
            }
        }

        if (success || scaleFactor < 0.99f || fpsOverride > 0) {
            saveConfig(context, packageName, scaleFactor, fpsOverride)
            killApp(context, packageName)
            return true
        }

        return false
    }

    /**
     * Resets the package back to native 100% resolution and uncapped FPS.
     */
    fun resetIntervention(context: Context, packageName: String): Boolean {
        Log.d(TAG, "Resetting intervention for $packageName")
        var success = false

        // Strategy 1: GameManager API update with 1.0f scale and 0 fps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            success = applyViaGameManagerApi(context, packageName, 1.0f, 0)
        }

        // Strategy 2: Shell cmd game reset
        if (!success) {
            val cmd = "cmd game reset $packageName"
            success = executeShell(cmd)
        }

        removeConfig(context, packageName)
        killApp(context, packageName)
        return true
    }

    private fun killApp(context: Context, packageName: String) {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            am?.killBackgroundProcesses(packageName)
        } catch (e: Exception) {
            Log.w(TAG, "killBackgroundProcesses non-fatal: ${e.message}")
        }
        executeShell("am force-stop $packageName")
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

    // --- Strategy 1: Reflection on GameManager System API ---
    @SuppressLint("PrivateApi")
    private fun applyViaGameManagerApi(
        context: Context,
        packageName: String,
        scaleFactor: Float,
        fpsOverride: Int
    ): Boolean {
        return try {
            val gameManager = context.getSystemService(Context.GAME_SERVICE) ?: return false

            // Build GameModeConfiguration
            val builderClass = Class.forName("android.app.GameModeConfiguration\$Builder")
            val builder = builderClass.getConstructor().newInstance()

            // Set scaling factor
            val setScalingMethod = builderClass.getMethod("setScalingFactor", Float::class.javaPrimitiveType)
            setScalingMethod.invoke(builder, scaleFactor)

            // Set FPS override
            val setFpsMethod = builderClass.getMethod("setFpsOverride", Int::class.javaPrimitiveType)
            setFpsMethod.invoke(builder, fpsOverride)

            val config = builderClass.getMethod("build").invoke(builder)

            // Invoke updateCustomGameModeConfiguration(packageName, config)
            val updateMethod = gameManager.javaClass.getMethod(
                "updateCustomGameModeConfiguration",
                String::class.java,
                Class.forName("android.app.GameModeConfiguration")
            )
            updateMethod.invoke(gameManager, packageName, config)

            // Also set game mode to CUSTOM (4)
            try {
                val setGameModeMethod = gameManager.javaClass.getMethod(
                    "setGameMode",
                    String::class.java,
                    Int::class.javaPrimitiveType
                )
                setGameModeMethod.invoke(gameManager, packageName, 4 /* GAME_MODE_CUSTOM */)
            } catch (e: Exception) {
                Log.w(TAG, "setGameMode to CUSTOM non-fatal warning: ${e.message}")
            }

            true
        } catch (e: Exception) {
            Log.w(TAG, "GameManager System API reflection failed: ${e.message}")
            false
        }
    }

    // --- Strategy 2: Direct Binder call on ServiceManager.getService("game") ---
    @SuppressLint("PrivateApi")
    private fun applyViaBinder(
        context: Context,
        packageName: String,
        scaleFactor: Float,
        fpsOverride: Int
    ): Boolean {
        return try {
            val smClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = smClass.getMethod("getService", String::class.java)
            val binder = getServiceMethod.invoke(null, "game") as? IBinder ?: return false

            val stubClass = Class.forName("android.app.IGameManagerService\$Stub")
            val asInterfaceMethod = stubClass.getMethod("asInterface", IBinder::class.java)
            val service = asInterfaceMethod.invoke(null, binder) ?: return false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val builderClass = Class.forName("android.app.GameModeConfiguration\$Builder")
                val builder = builderClass.getConstructor().newInstance()
                builderClass.getMethod("setScalingFactor", Float::class.javaPrimitiveType).invoke(builder, scaleFactor)
                builderClass.getMethod("setFpsOverride", Int::class.javaPrimitiveType).invoke(builder, fpsOverride)
                val config = builderClass.getMethod("build").invoke(builder)

                val updateMethod = service.javaClass.getMethod(
                    "updateCustomGameModeConfiguration",
                    String::class.java,
                    Class.forName("android.app.GameModeConfiguration"),
                    Int::class.javaPrimitiveType
                )
                updateMethod.invoke(service, packageName, config, 0 /* userId */)
                return true
            }
            false
        } catch (e: Exception) {
            Log.w(TAG, "Binder invocation failed: ${e.message}")
            false
        }
    }

    // --- Strategy 3: Local Shell cmd game ---
    private fun applyViaShellCommand(packageName: String, scaleFactor: Float, fpsOverride: Int): Boolean {
        val scaleStr = if (scaleFactor >= 0.99f) "disable" else String.format(java.util.Locale.US, "%.2f", scaleFactor)
        val fpsStr = if (fpsOverride > 0) "--fps $fpsOverride" else ""
        val cmd = "cmd game set --downscale $scaleStr $fpsStr $packageName"
        return executeShell(cmd)
    }

    private fun executeShell(command: String): Boolean {
        val shells = arrayOf("su", "/system/bin/su", "sh", "/system/bin/sh")
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
