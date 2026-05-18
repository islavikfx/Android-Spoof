package com.islavikfx.spoof.utils
import android.content.Context
import com.topjohnwu.superuser.Shell
import org.json.JSONObject
import java.io.File


object SELinuxBypass {

    private const val BYPASS = "SELinuxBypass"
    private lateinit var binaryPath: String
    fun init(context: Context) {
        binaryPath = "${context.filesDir.absolutePath}/$BYPASS"
        extractBinary(context)
        Shell.cmd("chmod 777 $binaryPath").exec()
    }

    private fun extractBinary(context: Context) {
        val binaryFile = File(binaryPath)
        if (binaryFile.exists()) return
        context.assets.open("bin/$BYPASS").use { input ->
            binaryFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }


    @JvmStatic
    fun executeWithBypass(command: String): Boolean {
        val result = Shell.cmd("$binaryPath bypass \"$command\"").exec()
        val json = JSONObject(result.out.joinToString("\n"))
        return json.optBoolean("success", false)
    }

    @JvmStatic
    fun getSelinuxStatus(): String {
        val result = Shell.cmd("$binaryPath status").exec()
        val json = JSONObject(result.out.joinToString("\n"))
        return json.optString("selinux_status", "Unknown")
    }

    @JvmStatic
    fun isEnforcing(): Boolean {
        val result = Shell.cmd("$binaryPath status").exec()
        val json = JSONObject(result.out.joinToString("\n"))
        return json.optBoolean("enforcing", false)
    }

    @JvmStatic
    fun installPolicy(): Boolean {
        val result = Shell.cmd("$binaryPath install_policy").exec()
        val json = JSONObject(result.out.joinToString("\n"))
        return json.optBoolean("success", false)
    }

    @JvmStatic
    fun executeCommand(command: String): Pair<Int, String> {
        val result = Shell.cmd("$binaryPath execute \"$command\"").exec()
        val json = JSONObject(result.out.joinToString("\n"))
        return Pair(
            json.optInt("exit_code", -1),
            json.optString("output", "")
        )
    }
}