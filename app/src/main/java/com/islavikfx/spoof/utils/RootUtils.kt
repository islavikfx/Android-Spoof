package com.islavikfx.spoof.utils
import android.content.Context
import com.topjohnwu.superuser.Shell


object RootUtils {

    private var initialized = false
    fun init(context: Context) {
        if (initialized) return
        SELinuxBypass.init(context)
        initialized = true
    }

    fun isRootAvailable(): Boolean {
        return try {
            Shell.getShell().isRoot
        } catch (_: Exception) {
            false
        }
    }

    fun fileExists(path: String): Boolean {
        return try {
            val result = Shell.cmd("test -e $path && echo 1 || echo 0").exec()
            result.out.firstOrNull() == "1"
        } catch (_: Exception) {
            false
        }
    }
}