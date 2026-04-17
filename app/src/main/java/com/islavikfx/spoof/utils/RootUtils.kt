package com.islavikfx.spoof.utils
import com.topjohnwu.superuser.Shell


object RootUtils {

    fun isRootAvailable(): Boolean {
        return try {
            Shell.getShell().isRoot
        } catch (_: Exception) {
            false }
    }

    @Suppress("unused")
    fun executeCommand(vararg commands: String): Boolean {
        return try {
            val result = Shell.cmd(*commands).exec()
            result.isSuccess
        } catch (_: Exception) {
            false }
    }

    @Suppress("unused")
    fun executeCommandWithResult(vararg commands: String): Shell.Result {
        return Shell.cmd(*commands).exec() }

    fun fileExists(path: String): Boolean {
        return try {
            val result = Shell.cmd("test -e $path && echo EXISTS || echo NOT_EXISTS").exec()
            result.out.firstOrNull() == "EXISTS"
        } catch (_: Exception) {
            false }
    }

    @Suppress("unused")
    fun directoryExists(path: String): Boolean {
        return try {
            val result = Shell.cmd("test -d $path && echo EXISTS || echo NOT_EXISTS").exec()
            result.out.firstOrNull() == "EXISTS"
        } catch (_: Exception) {
            false }
    }

}