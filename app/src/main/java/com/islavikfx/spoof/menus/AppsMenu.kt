package com.islavikfx.spoof.menus
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.islavikfx.spoof.R
import com.topjohnwu.superuser.Shell


class AppsMenu : BaseMenu() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchEdit: EditText
    private lateinit var adapter: AppsAdapter
    private lateinit var loadingText: TextView
    private lateinit var loadingProgress: ProgressBar
    private val allApps = mutableListOf<AppItem>()
    private val filteredApps = mutableListOf<AppItem>()
    private var warningShown = false
    private var isBusy = false
    private var searchText = ""
    private val searchHandler = Handler(Looper.getMainLooper())
    private var isLoading = false

    override fun draw(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        return inflater.inflate(R.layout.menu_apps, container, false)
    }

    override fun setup(view: View, state: Bundle?) {
        if (!isRootAvailable()) {
            parentFragmentManager.popBackStack()
            return }

        view.findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            parentFragmentManager.popBackStack() }
        view.findViewById<TextView>(R.id.bar_title).text = title()
        recyclerView = view.findViewById(R.id.rec)
        searchEdit = view.findViewById(R.id.search)
        loadingText = view.findViewById(R.id.loading_txt)
        loadingProgress = view.findViewById(R.id.loading_progress)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = AppsAdapter(filteredApps,
            { app -> if (!isBusy && !isLoading) showAppDialog(app) },
            getThemeColor())
        recyclerView.adapter = adapter

        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isLoading) return
                searchText = s?.toString()?.lowercase() ?: ""
                searchHandler.removeCallbacksAndMessages(null)
                searchHandler.postDelayed({
                    if (!isLoading && searchText == (searchEdit.text.toString().lowercase())) {
                        filterApps(searchText)
                    } }, 300) }
            override fun afterTextChanged(s: Editable?) {}
        })


        val searchCard: MaterialCardView = searchEdit.parent as MaterialCardView
        searchCard.strokeColor = getThemeColor().toColorInt()
        searchEdit.visibility = View.GONE
        recyclerView.visibility = View.GONE
        loadingText.visibility = View.VISIBLE
        loadingProgress.visibility = View.VISIBLE
        loadingText.text = getString(R.string.loading_apps)
        isLoading = true

        if (!warningShown) {
            showWarningDialog()
        } else { searchEdit.visibility = View.VISIBLE
            loadInstalledApps()
        }
    }


    @SuppressLint("InflateParams")
    private fun showWarningDialog() {
        val dialog = Dialog(requireContext(), R.style.RoundedDialog)
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dlg_warn_apps, null, false)
        dialog.setContentView(view)
        dialog.setCancelable(false)
        val okButton: MaterialButton = view.findViewById(R.id.btn_ok)
        okButton.backgroundTintList =
            android.content.res.ColorStateList.valueOf(getThemeColor().toColorInt())
        okButton.setOnClickListener {
            dialog.dismiss()
            warningShown = true
            searchEdit.visibility = View.VISIBLE
            loadInstalledApps()
        }
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
    }


    private fun loadInstalledApps() {
        isLoading = true
        loadingText.visibility = View.VISIBLE
        loadingProgress.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        searchEdit.isEnabled = false
        handler.postDelayed({
            if (isLoading) {
                isLoading = false
                searchEdit.isEnabled = true
                loadingText.visibility = View.GONE
                loadingProgress.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                if (allApps.isEmpty()) { showToast(getString(R.string.error_loading))
                } else {
                    showToast("[+] Loaded (${allApps.size}) apps.") }
            }
        }, 15000)


        Thread {
            try {
                val pm = requireContext().packageManager
                val tempList = mutableListOf<AppItem>()
                val seenPackages = mutableSetOf<String>()
                val pmResult = Shell.cmd("pm list packages -f --user 0").exec()
                val pmSystemResult = Shell.cmd("pm list packages -f -s").exec()
                if (pmSystemResult.isSuccess && pmResult.isSuccess) {
                    pmResult.out.addAll(pmSystemResult.out) }
                if (!pmResult.isSuccess || pmResult.out.isEmpty()) {
                    val pmResultAll = Shell.cmd("pm list packages -f").exec()
                    if (pmResultAll.isSuccess && pmResult.isSuccess) {
                        pmResult.out.addAll(pmResultAll.out) }
                }

                if (pmResult.isSuccess) {
                    for (line in pmResult.out) {
                        try {
                            val trimmedLine = line.trim()
                            if (!trimmedLine.startsWith("package:")) continue
                            val content = trimmedLine.removePrefix("package:")
                            val lastEquals = content.lastIndexOf("=")
                            if (lastEquals == -1) continue

                            val apkPath = content.substring(0, lastEquals).trim()
                            val pkgName = content.substring(lastEquals + 1).trim()
                            if (pkgName.isEmpty() || seenPackages.contains(pkgName)) continue

                            var label = pkgName.substringAfterLast(".")
                            var icon: Drawable = pm.defaultActivityIcon
                            var appInfo: ApplicationInfo? = null
                            try {
                                appInfo = pm.getApplicationInfo(pkgName, 0)
                            } catch (_: Exception) {
                                try {
                                    appInfo = pm.getApplicationInfo(pkgName, 131072)
                                } catch (_: Exception) {
                                }
                            }
                            if (appInfo != null) {
                                label = pm.getApplicationLabel(appInfo).toString()
                                icon = try { appInfo.loadIcon(pm)
                                } catch (_: Exception) {
                                    try { pm.getApplicationIcon(pkgName)
                                    } catch (_: Exception) {
                                        getIconFromApkFile(apkPath, pm) }
                                }
                            } else {
                                val archiveInfo = pm.getPackageArchiveInfo(apkPath, 0)
                                val archiveAppInfo = archiveInfo?.applicationInfo

                                if (archiveAppInfo != null) {
                                    archiveAppInfo.sourceDir = apkPath
                                    archiveAppInfo.publicSourceDir = apkPath
                                    label = try { pm.getApplicationLabel(archiveAppInfo).toString()
                                    } catch (_: Exception) { archiveAppInfo.name ?: pkgName.substringAfterLast(".")
                                    }
                                    icon = try { archiveAppInfo.loadIcon(pm)
                                    } catch (_: Exception) { pm.defaultActivityIcon
                                    }
                                }
                            }
                            seenPackages.add(pkgName)
                            tempList.add(AppItem(label, pkgName, icon, apkPath))
                        } catch (_: Exception) {
                            continue
                        }
                    }
                }

                val sortedApps = tempList.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
                allApps.clear()
                allApps.addAll(sortedApps)
                handler.post { isLoading = false
                    searchEdit.isEnabled = true
                    loadingText.visibility = View.GONE
                    loadingProgress.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    filterApps(searchText)
                    showToast("[+] Loaded (${allApps.size}) apps.")
                }

            } catch (_: Exception) {
                handler.post {
                    isLoading = false
                    searchEdit.isEnabled = true
                    loadingText.visibility = View.GONE
                    loadingProgress.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    showToast(getString(R.string.error_loading))
                } }
        }.start()
    }


    private fun getIconFromApkFile(apkPath: String, pm: PackageManager): Drawable {
        return try {
            val packageInfo = pm.getPackageArchiveInfo(apkPath, 0)
            val appInfo = packageInfo?.applicationInfo
            if (appInfo != null) {
                appInfo.sourceDir = apkPath
                appInfo.publicSourceDir = apkPath
                appInfo.loadIcon(pm)
            } else {
                pm.defaultActivityIcon }
        } catch (_: Exception) {
            pm.defaultActivityIcon }
    }


    @SuppressLint("NotifyDataSetChanged")
    private fun filterApps(query: String) {
        if (isLoading) return
        try {
            filteredApps.clear()
            if (query.isEmpty()) {
                filteredApps.addAll(allApps)
            } else {
                val lowerQuery = query.lowercase()
                filteredApps.addAll(allApps.filter { it.label.lowercase().contains(lowerQuery) ||
                        it.packageName.lowercase().contains(lowerQuery) })
            }
            adapter.notifyDataSetChanged()
        } catch (_: Exception) {
        }
    }


    private fun forceStopApp(packageName: String): Boolean {
        return Shell.cmd("am force-stop $packageName").exec().isSuccess
    }

    private fun clearAppData(packageName: String): Boolean {
        var result = Shell.cmd("pm clear $packageName").exec()
        if (!result.isSuccess) {
            result = Shell.cmd("pm clear --user 0 $packageName").exec() }
        if (!result.isSuccess) {
            Shell.cmd("rm -rf /data/data/$packageName", "rm -rf /data/user/0/$packageName", "rm -rf /data/user_de/0/$packageName").exec()
            result = Shell.cmd("echo 1").exec()
        }
        return result.isSuccess
    }


    @SuppressLint("SdCardPath")
    private fun getAppSize(packageName: String, apkPath: String): String {
        try {
            val paths = mutableListOf<String>()
            if (apkPath.isNotEmpty()) {
                val apkDir = apkPath.substringBeforeLast("/")
                if (apkDir.isNotEmpty()) paths.add(apkDir)
            }
            paths.add("/data/data/$packageName")
            var totalSize = 0L
            for (path in paths) {
                val result = Shell.cmd("""du -sk $path 2>/dev/null | awk '{print $1}'""").exec()
                if (result.isSuccess && result.out.isNotEmpty()) {
                    val sizeStr = result.out[0].trim()
                    if (sizeStr.isNotEmpty() && sizeStr.all { it.isDigit() }) {
                        totalSize += sizeStr.toLong() * 1024
                    }
                }
            }
            return if (totalSize > 0) formatSize(totalSize) else "N/A"
        } catch (_: Exception) {
            return "N/A"
        }
    }


    private fun formatSize(bytes: Long): String {
        return when {bytes >= 1024 * 1024 * 1024 -> "%.2f GB".format(bytes.toDouble() / (1024 * 1024 * 1024))
            bytes >= 1024 * 1024 -> "%.1f MB".format(bytes.toDouble() / (1024 * 1024))
            bytes >= 1024 -> "%.0f KB".format(bytes.toDouble() / 1024)
            else -> "$bytes B"
        }
    }


    private fun uninstallAppCompletely(packageName: String, apkPath: String): Boolean {
        try {
            Shell.cmd("am force-stop $packageName", "pm disable $packageName 2>/dev/null", "pm disable --user 0 $packageName 2>/dev/null", "pm suspend $packageName 2>/dev/null").exec()
            Shell.cmd("pm uninstall $packageName 2>/dev/null", "pm uninstall --user 0 $packageName 2>/dev/null", "pm uninstall -k --user 0 $packageName 2>/dev/null").exec()
            Shell.cmd("mount -o rw,remount / 2>/dev/null", "mount -o rw,remount /system 2>/dev/null", "mount -o rw,remount /product 2>/dev/null", "mount -o rw,remount /vendor 2>/dev/null").exec()
            if (apkPath.isNotEmpty()) {
                val appDir = apkPath.substringBeforeLast("/")
                Shell.cmd("rm -rf $appDir", "rm -f $apkPath").exec()
            }
            Shell.cmd("rm -rf /data/data/$packageName", "rm -rf /data/user/0/$packageName", "rm -rf /data/user_de/0/$packageName", "rm -rf /data/media/0/Android/data/$packageName",
                "rm -rf /sdcard/Android/data/$packageName", "rm -rf /storage/emulated/0/Android/data/$packageName", "rm -rf /data/dalvik-cache/*${packageName.replace(".", "-")}*").exec()
            Shell.cmd("sed -i '/$packageName/d' /data/system/packages.xml 2>/dev/null", "sed -i '/$packageName/d' /data/system/packages.list 2>/dev/null", "rm -f /data/system/package_cache/* 2>/dev/null").exec()
            Shell.cmd("mount -o ro,remount / 2>/dev/null", "mount -o ro,remount /system 2>/dev/null", "mount -o ro,remount /product 2>/dev/null", "mount -o ro,remount /vendor 2>/dev/null").exec()
            return true
        } catch (_: Exception) {
            return false
        }
    }


    @SuppressLint("InflateParams", "SetTextI18n")
    private fun showAppDialog(item: AppItem) {
        if (isLoading) return
        isBusy = true

        val dialog = Dialog(requireContext(), R.style.RoundedDialog)
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dlg_app, null, false)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.setOnDismissListener { isBusy = false }
        val color = getThemeColor().toColorInt()
        view.findViewById<ImageView>(R.id.app_icon).setImageDrawable(item.icon)
        view.findViewById<TextView>(R.id.app_label).text = item.label
        view.findViewById<TextView>(R.id.app_pkg).text = item.packageName
        view.findViewById<TextView>(R.id.app_size).text = getString(R.string.calculating)
        Thread {
            val size = getAppSize(item.packageName, item.apkPath)
            handler.post { view.findViewById<TextView>(R.id.app_size).text = "Size: $size" } }.start()
        view.findViewById<TextView>(R.id.app_path).text = "Path: ${item.apkPath.ifEmpty { "N/A" }}"

        try {
            val pi = requireContext().packageManager.getPackageInfo(item.packageName, PackageManager.GET_PERMISSIONS)
            val perms = pi.requestedPermissions
            if (!perms.isNullOrEmpty()) {
                val shortPerms = perms.map { it.substringAfterLast('.') }
                val permsText = if (shortPerms.size <= 5) {
                    shortPerms.joinToString(", ")
                } else {
                    shortPerms.take(5).joinToString(", ") + " and (${shortPerms.size - 5}) more…"
                }
                view.findViewById<TextView>(R.id.app_perms).text = "Permissions: $permsText."
            } else {
                view.findViewById<TextView>(R.id.app_perms).text = "No permissions."
            }
        } catch (_: Exception) {
            view.findViewById<TextView>(R.id.app_perms).text = "Failed extracting permissions."
        }

        val cancelBtn: MaterialButton = view.findViewById(R.id.btn_cancel)
        val clearBtn: MaterialButton = view.findViewById(R.id.btn_clear)
        val forceStopBtn: MaterialButton = view.findViewById(R.id.btn_forcestop)
        val deleteBtn: MaterialButton = view.findViewById(R.id.btn_del)
        val colorState = android.content.res.ColorStateList.valueOf(color)
        clearBtn.backgroundTintList = colorState
        deleteBtn.backgroundTintList = colorState
        forceStopBtn.backgroundTintList = colorState
        cancelBtn.strokeColor = colorState
        cancelBtn.setOnClickListener { dialog.dismiss() }
        forceStopBtn.setOnClickListener {
            dialog.dismiss()
            Thread {
                val success = forceStopApp(item.packageName)
                handler.post { showToast(if (success) "[+] ${item.label} stopped." else "[-] Failed to stop.") }
            }.start()
        }
        clearBtn.setOnClickListener {
            dialog.dismiss()
            Thread {
                val success = clearAppData(item.packageName)
                handler.post {
                    showToast(if (success) "[+] Data cleared." else "[-] Failed to clear data.")
                } }.start()
        }
        deleteBtn.setOnClickListener {
            dialog.dismiss()
            Thread {
                uninstallAppCompletely(item.packageName, item.apkPath)
                handler.post {
                    showRemovedDialog(item)
                    loadInstalledApps()
                } }.start()
        }
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
    }


    @SuppressLint("InflateParams")
    private fun showRemovedDialog(item: AppItem) {
        val dialog = Dialog(requireContext(), R.style.RoundedDialog)
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dlg_removed, null, false)
        dialog.setContentView(view)
        dialog.setCancelable(false)

        val color = getThemeColor().toColorInt()
        view.findViewById<TextView>(R.id.msg_label).text = item.label
        view.findViewById<TextView>(R.id.msg_pkg).text = item.packageName
        val rebootBtn: MaterialButton = view.findViewById(R.id.btn_reboot)
        val laterBtn: MaterialButton = view.findViewById(R.id.btn_later)
        rebootBtn.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        laterBtn.strokeColor = android.content.res.ColorStateList.valueOf(color)

        rebootBtn.setOnClickListener { dialog.dismiss()
            Shell.cmd("reboot").submit()
            showToast(getString(R.string.rebooting))
        }
        laterBtn.setOnClickListener { dialog.dismiss() }
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
    }

    override fun title(): String = getString(R.string.configure_device_apps)
    data class AppItem(val label: String, val packageName: String, val icon: Drawable, val apkPath: String)
}


class AppsAdapter(private val items: List<AppsMenu.AppItem>,
    private val onEdit: (AppsMenu.AppItem) -> Unit,
    private val themeColor: String) : RecyclerView.Adapter<AppsAdapter.ViewHolder>() {


    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.app_icon)
        val label: TextView = view.findViewById(R.id.app_label)
        val pkg: TextView = view.findViewById(R.id.app_pkg)
        val editBtn: TextView = view.findViewById(R.id.btn_edit)
        val card: MaterialCardView = view.findViewById(R.id.edit_ctr)
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return ViewHolder(view)
    }


    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.icon.setImageDrawable(item.icon)
        holder.label.text = item.label
        holder.pkg.text = item.packageName
        val color = themeColor.toColorInt()
        holder.card.strokeColor = color
        holder.card.strokeWidth = 2
        holder.editBtn.setTextColor(color)
        holder.editBtn.setOnClickListener { onEdit(item) }
    }

    override fun getItemCount(): Int = items.size
}