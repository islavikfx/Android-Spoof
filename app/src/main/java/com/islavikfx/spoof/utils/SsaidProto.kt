package com.islavikfx.spoof.utils


object SsaidProto {

    fun findAndroidId(data: ByteArray): String {
        val content = String(data, Charsets.ISO_8859_1)
        if (content.startsWith("ABX")) {
            val userKeyIndex = content.indexOf("userkey")
            if (userKeyIndex != -1) {
                val afterUserKey = content.substring(userKeyIndex)
                val pattern = Regex("[A-F0-9]{64}")
                pattern.find(afterUserKey)?.value?.let { return it } }

        } else {
            val pattern = Regex("""<setting[^>]*name="userkey"[^>]*value="([^"]+)"""")
            pattern.find(content)?.groupValues?.get(1)?.let { return it }

            val androidPattern = Regex("""<setting[^>]*package="android"[^>]*value="([^"]+)"""")
            androidPattern.find(content)?.groupValues?.get(1)?.let { return it } }
        return ""

    }

    fun replaceAndroidId(data: ByteArray, newId: String): ByteArray {
        var content = String(data, Charsets.ISO_8859_1)
        val oldId = findAndroidId(data)
        if (oldId.isNotEmpty()) {
            content = content.replace(oldId, newId)
            return content.toByteArray(Charsets.ISO_8859_1) }
        return data

    }

    @Suppress("unused")
    fun replaceAllPackageIds(data: ByteArray, newIdGenerator: (String) -> String): ByteArray {

        var content = String(data, Charsets.ISO_8859_1)
        if (content.startsWith("ABX")) {
            val hex16Pattern = Regex("[A-F0-9]{16}")
            val allHexIds = hex16Pattern.findAll(content).map { it.value }.toSet()
            val packageIds = mutableMapOf<String, String>()

            allHexIds.forEach { hexId ->
                val indices = hex16Pattern.findAll(content).map { it.range.first }.toList()
                indices.forEach { idx ->
                    val contextStart = maxOf(0, idx - 50)
                    val contextEnd = minOf(content.length, idx + 50)
                    val context = content.substring(contextStart, contextEnd)
                    val pkgPattern = Regex("""([a-z][a-z0-9_.]+)/""")

                    pkgPattern.find(context)?.let { pkgMatch ->
                        val pkgName = pkgMatch.groupValues[1]
                        if (pkgName.contains(".") && pkgName != "android") {
                            packageIds[hexId] = pkgName

                        }
                    }
                }
            }

            packageIds.forEach { (oldId, pkgName) ->
                val newId = newIdGenerator(pkgName)
                content = content.replace(oldId, newId) }

        } else {

            val settingPattern = Regex("""<setting[^>]*package="([^"]+)"[^>]*value="([^"]+)"[^>]*>""")
            settingPattern.findAll(content).forEach { match ->
                val pkgName = match.groupValues[1]
                val oldValue = match.groupValues[2]

                if (pkgName != "android" && oldValue.length == 16) {
                    val newId = newIdGenerator(pkgName)
                    content = content.replace(oldValue, newId)
                    val defaultPattern = Regex("""<setting[^>]*package="$pkgName"[^>]*defaultValue="([^"]+)"""")
                    defaultPattern.find(content)?.groupValues?.get(1)?.let { oldDefault ->

                        if (oldDefault.length == 16) {
                            content = content.replace(oldDefault, newId)

                        }
                    }
                }
            }
        }

        return content.toByteArray(Charsets.ISO_8859_1)
    }

    @Suppress("unused")
    private fun isAbxFormat(data: ByteArray): Boolean {
        return String(data, 0, minOf(3, data.size), Charsets.ISO_8859_1) == "ABX" }

}