package com.islavikfx.spoof.utils
import com.topjohnwu.superuser.Shell


object SelinuxBypass {

    @Suppress("unused")
    private fun findMappingAddress(): String? {
        val result = Shell.cmd("cat /proc/kallsyms | grep -E 'selinux_map|current_mapping' | head -1 | awk '{print $1}'").exec()
        if (result.isSuccess && result.out.isNotEmpty()) {
            return result.out[0].trim()
        }
        return null
    }

    @Suppress("unused")
    fun disableSelinuxViaMapping(): Boolean {
        return try {
            Shell.cmd("echo '2' > /sys/fs/selinux/allow_unknown 2>/dev/null").exec()
            Shell.cmd("echo 1 > /sys/fs/selinux/reload_policy 2>/dev/null").exec()
            true
        } catch (@Suppress("UNUSED_PARAMETER") e: Exception) {
            false
        }
    }

    @Suppress("unused")
    fun disableSelinuxWithMagisk(): Boolean {
        return try {
            Shell.cmd("magiskpolicy --live 'allow shell app_data_file dir write' 2>/dev/null").exec()
            Shell.cmd("magiskpolicy --live 'allow shell app_data_file file write' 2>/dev/null").exec()
            Shell.cmd("magiskpolicy --live 'allow shell app_data_file dir read' 2>/dev/null").exec()
            Shell.cmd("magiskpolicy --live 'allow shell app_data_file file read' 2>/dev/null").exec()
            Shell.cmd("magiskpolicy --live 'allow untrusted_app app_data_file dir write' 2>/dev/null").exec()
            Shell.cmd("magiskpolicy --live 'allow untrusted_app app_data_file file write' 2>/dev/null").exec()
            true
        } catch (@Suppress("UNUSED_PARAMETER") e: Exception) {
            false
        }
    }

    @Suppress("unused")
    fun runWithSelinuxContext(command: String): Boolean {
        val contexts = listOf("u:r:shell:s0",
            "u:r:init:s0",
            "u:r:system_app:s0",
            "u:r:su:s0")
        for (context in contexts) {
            try {
                val result = Shell.cmd("su --context $context -c \"$command\"").exec()
                if (result.isSuccess) return true
            } catch (@Suppress("UNUSED_PARAMETER") e: Exception) { }
        }
        return false
    }

    @Suppress("unused")
    fun disableWithSupolicy(): Boolean {
        return try {
            Shell.cmd("supolicy --live 'permissive *' 2>/dev/null").exec()
            true
        } catch (@Suppress("UNUSED_PARAMETER") e: Exception) {
            false
        }
    }

    @JvmStatic
    fun executeWithBypass(command: String): Boolean {
        val methods = listOf(
            { disableSelinuxViaMapping() },
            { disableSelinuxWithMagisk() },
            { disableWithSupolicy() },
            {
                runWithSelinuxContext(command)
                true
            }
        )
        for (method in methods) {
            try {
                if (method()) return true
            } catch (@Suppress("UNUSED_PARAMETER") e: Exception) { }
        }
        val result = Shell.cmd(command).exec()
        return result.isSuccess
    }

}