package com.islavikfx.spoof.utils
import com.topjohnwu.superuser.Shell

object SsidProto {

    data class PackageSsid(val packageName: String,
        val ssid: String,
        val defaultValue: String,
        val defaultSysSet: Boolean)

    fun generateRandomId(isAndroidPackage: Boolean): String {
        val chars = if (isAndroidPackage) "0123456789ABCDEF" else "0123456789abcdef"
        val length = if (isAndroidPackage) 64 else 17
        return (1..length).map { chars[kotlin.random.Random.nextInt(chars.length)] }.joinToString("")
    }

    fun parseFile(filePath: String): List<PackageSsid> {
        val result = mutableListOf<PackageSsid>()
        try {
            val xmlContent = decodeAbxToXml(filePath)
            if (xmlContent.isNotEmpty()) {
                parseXmlContent(xmlContent, result)
            }
        } catch (_: Exception) { }

        if (result.isEmpty()) {
            tryParseBinaryRaw(filePath, result)
        }
        return result
    }


    private fun decodeAbxToXml(filePath: String): String {
        try {
            val tmpFile = "/data/local/tmp/ssid_temp.xml"
            Shell.cmd("rm -f $tmpFile").exec()
            val checkResult = Shell.cmd("head -c 3 $filePath 2>/dev/null").exec()
            val header = checkResult.out.firstOrNull() ?: ""
            if (header == "ABX") {
                val result = Shell.cmd("abx2xml $filePath $tmpFile 2>/dev/null && cat $tmpFile 2>/dev/null").exec()
                Shell.cmd("rm -f $tmpFile").exec()
                if (result.isSuccess && result.out.isNotEmpty()) {
                    return result.out.joinToString("\n")
                }
            } else {
                val result = Shell.cmd("cat $filePath 2>/dev/null").exec()
                if (result.isSuccess && result.out.isNotEmpty()) {
                    return result.out.joinToString("\n")
                }
            }
            return ""
        } catch (_: Exception) {
            return ""
        }
    }


    private fun parseXmlContent(content: String, result: MutableList<PackageSsid>) {
        try {
            val settingPattern = Regex("""<setting\s+[^>]*package="([^"]+)"[^>]*value="([^"]+)"[^>]*(?:defaultValue="([^"]*)")?[^>]*(?:defaultSysSet-bool="([^"]*)")?[^>]*>""")
            for (match in settingPattern.findAll(content)) {
                val groups = match.groupValues
                val packageName = groups[1]
                val value = groups[2]
                val defaultValue = groups.getOrNull(3) ?: ""
                val defaultSysSet = groups.getOrNull(4) == "true"
                if (packageName.isNotEmpty() && value.isNotEmpty()) {
                    result.add(
                        PackageSsid(
                            packageName = packageName,
                            ssid = value,
                            defaultValue = defaultValue.ifEmpty { value },
                            defaultSysSet = defaultSysSet
                        )
                    )
                }
            }
        } catch (_: Exception) { }
    }


    private fun tryParseBinaryRaw(filePath: String, result: MutableList<PackageSsid>) {
        try {
            val shellResult = Shell.cmd("cat $filePath | base64").exec()
            if (!shellResult.isSuccess || shellResult.out.isEmpty()) return
            val rawBytes = android.util.Base64.decode(shellResult.out.joinToString(""),
                android.util.Base64.DEFAULT)
            val content = String(rawBytes, Charsets.ISO_8859_1)
            val userKeyIndex = content.indexOf("userkey")
            if (userKeyIndex != -1) {
                val afterUserKey = content.substring(userKeyIndex)
                val pattern = Regex("[A-Fa-f0-9]{64}")
                val match = pattern.find(afterUserKey)
                if (match != null) {
                    result.add(PackageSsid(packageName = "android",
                            ssid = match.value, defaultValue = match.value, defaultSysSet = true)) }
            }

            val pkgPattern = Regex("""([a-z][a-z0-9_]+\.[a-z][a-z0-9_.]+)/""")
            val hexPattern = Regex("[A-Fa-f0-9]{16,17}")
            val foundPackages = mutableSetOf("android")
            for (hexMatch in hexPattern.findAll(content)) {
                val hexId = hexMatch.value
                val idx = hexMatch.range.first
                val contextStart = maxOf(0, idx - 200)
                val contextEnd = minOf(content.length, idx + 200)
                val context = content.substring(contextStart, contextEnd)
                val pkgMatch = pkgPattern.find(context)

                if (pkgMatch != null) {
                    val pkgName = pkgMatch.groupValues[1]
                    if (pkgName !in foundPackages && pkgName.contains(".") && !pkgName.endsWith(".")) {
                        foundPackages.add(pkgName)
                        result.add(PackageSsid(packageName = pkgName,
                                ssid = hexId, defaultValue = hexId, defaultSysSet = false)) }
                }
            }
        } catch (_: Exception) { }
    }


    fun updateFile(filePath: String, packageName: String, newValue: String): Boolean {
        try {
            Shell.cmd("rm -f /data/local/tmp/ssid_work.xml /data/local/tmp/ssid_work_new.xml").exec()
            val xmlResult = Shell.cmd("abx2xml $filePath /data/local/tmp/ssid_work.xml 2>/dev/null && cat /data/local/tmp/ssid_work.xml").exec()
            if (!xmlResult.isSuccess || xmlResult.out.isEmpty()) {
                return updateBinaryDirect(filePath, packageName, newValue)
            }
            Shell.cmd("sed -i '/package=\"${packageName}\"/ { s/value=\"[^\"]*\"/value=\"${newValue}\"/; s/defaultValue=\"[^\"]*\"/defaultValue=\"${newValue}\"/; }' /data/local/tmp/ssid_work.xml").exec()
            Shell.cmd("xml2abx /data/local/tmp/ssid_work.xml $filePath").exec()
            val fallbackPath = "$filePath.fallback"
            Shell.cmd("cp $filePath $fallbackPath").exec()
            Shell.cmd("chown system:system $filePath").exec()
            Shell.cmd("chown system:system $fallbackPath").exec()
            Shell.cmd("chmod 660 $filePath").exec()
            Shell.cmd("chmod 660 $fallbackPath").exec()
            Shell.cmd("restorecon $filePath 2>/dev/null || true").exec()
            Shell.cmd("restorecon $fallbackPath 2>/dev/null || true").exec()
            Shell.cmd("sync").exec()
            Shell.cmd("rm -f /data/local/tmp/ssid_work.xml /data/local/tmp/ssid_work_new.xml").exec()
            return true
        } catch (_: Exception) {
            return false
        }
    }


    private fun updateBinaryDirect(filePath: String, packageName: String, newValue: String): Boolean {
        try {
            val result = Shell.cmd("cat $filePath | base64").exec()
            if (!result.isSuccess || result.out.isEmpty()) return false

            val rawBytes = android.util.Base64.decode(result.out.joinToString(""),android.util.Base64.DEFAULT)
            val data = parseFile(filePath)
            val item = data.find { it.packageName == packageName } ?: return false
            val charset = Charsets.ISO_8859_1
            var modified = rawBytes
            modified = replaceInBytes(modified, item.ssid.toByteArray(charset), newValue.toByteArray(charset))
            if (item.defaultValue.isNotEmpty() && item.defaultValue != item.ssid) {
                modified = replaceInBytes(modified, item.defaultValue.toByteArray(charset), newValue.toByteArray(charset))
            }
            val encoded = android.util.Base64.encodeToString(modified, android.util.Base64.DEFAULT)
            Shell.cmd("echo '$encoded' | base64 -d > $filePath").exec()
            val fallbackPath = "$filePath.fallback"
            Shell.cmd("cp $filePath $fallbackPath").exec()
            Shell.cmd("chown system:system $filePath").exec()
            Shell.cmd("chown system:system $fallbackPath").exec()
            Shell.cmd("chmod 660 $filePath").exec()
            Shell.cmd("chmod 660 $fallbackPath").exec()
            Shell.cmd("sync").exec()
            return true
        } catch (_: Exception) {
            return false
        }
    }


    private fun replaceInBytes(data: ByteArray, oldBytes: ByteArray, newBytes: ByteArray): ByteArray {
        if (oldBytes.isEmpty()) return data
        val paddedNew = ByteArray(oldBytes.size)
        System.arraycopy(newBytes, 0, paddedNew, 0, minOf(newBytes.size, oldBytes.size))
        val output = data.copyOf()
        var pos = 0
        while (pos <= output.size - oldBytes.size) {
            var match = true
            for (j in oldBytes.indices) {
                if (output[pos + j] != oldBytes[j]) {
                    match = false
                    break
                }
            }
            if (match) {
                System.arraycopy(paddedNew, 0, output, pos, paddedNew.size)
                return output
            }
            pos++
        }
        return data
    }
}