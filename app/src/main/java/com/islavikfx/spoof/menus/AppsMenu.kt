package com.islavikfx.spoof.menus
import android.annotation.SuppressLint
import android.app.Dialog
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
import android.widget.TextView
import android.widget.ProgressBar
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.islavikfx.spoof.R
import com.topjohnwu.superuser.Shell


class AppsMenu : BaseMenu() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var recycler: RecyclerView
    private lateinit var searchEdit: EditText
    private lateinit var adapter: AppsAdapter
    private lateinit var loadingTxt: TextView
    private lateinit var loadingProgress: ProgressBar
    private val allApps = mutableListOf<AppItem>()
    private val filteredApps = mutableListOf<AppItem>()
    private var warningShown = false
    private var isBusy = false
    private var searchText = ""
    private val searchHandler = Handler(Looper.getMainLooper())
    private var isLoading = false

    override fun draw(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        return inflater.inflate(R.layout.menu_apps, container, false) }

    override fun setup(view: View, state: Bundle?) {
        if (!isRootAvailable()) {
            parentFragmentManager.popBackStack()
            return }

        view.findViewById<ImageView>(R.id.btn_back).setOnClickListener { parentFragmentManager.popBackStack() }
        view.findViewById<TextView>(R.id.bar_title).text = title()

        recycler = view.findViewById(R.id.rec)
        searchEdit = view.findViewById(R.id.search)
        loadingTxt = view.findViewById(R.id.loading_txt)
        loadingProgress = view.findViewById(R.id.loading_progress)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        adapter = AppsAdapter(filteredApps, { app -> if (!isBusy && !isLoading) showDialog(app) }, getThemeColor())
        recycler.adapter = adapter

        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {
                if (isLoading) return
                searchText = s?.toString()?.lowercase() ?: ""
                searchHandler.removeCallbacksAndMessages(null)
                searchHandler.postDelayed({
                    if (!isLoading && searchText == (searchEdit.text.toString().lowercase())) {
                        filterApps(searchText)
                    }
                }, 300)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        (searchEdit.parent as? MaterialCardView)?.strokeColor = getThemeColor().toColorInt()
        searchEdit.visibility = View.GONE
        recycler.visibility = View.GONE
        loadingTxt.visibility = View.VISIBLE
        loadingProgress.visibility = View.VISIBLE
        loadingTxt.text = getString(R.string.loading_apps)
        isLoading = true

        if (!warningShown) { showWarning()
        } else {
            searchEdit.visibility = View.VISIBLE
            loadApps() }
    }

    private fun showWarning() {
        val dialog = Dialog(requireContext(), R.style.RoundedDialog)
        val rootView = dialog.window?.decorView?.findViewById<ViewGroup>(android.R.id.content)
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dlg_warn_apps, rootView, false)
        dialog.setContentView(view)
        dialog.setCancelable(false)

        val okButton = view.findViewById<MaterialButton>(R.id.btn_ok)
        okButton.backgroundTintList = android.content.res.ColorStateList.valueOf(getThemeColor().toColorInt())
        okButton.setOnClickListener { dialog.dismiss()
            warningShown = true
            searchEdit.visibility = View.VISIBLE
            loadApps() }

        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT) }

    private fun loadApps() {
        isLoading = true
        loadingTxt.visibility = View.VISIBLE
        loadingProgress.visibility = View.VISIBLE
        recycler.visibility = View.GONE
        searchEdit.isEnabled = false

        Thread {
            try {
                val pm = requireContext().packageManager
                val tempList = mutableListOf<AppItem>()
                val seenPackages = mutableSetOf<String>()
                val result = Shell.cmd("pm list packages -f").exec()

                if (result.isSuccess && result.out.isNotEmpty()) {
                    for (line in result.out) {
                        try {
                            val equalIndex = line.indexOf('=')
                            if (equalIndex == -1) continue

                            val pkgName = line.substring(equalIndex + 1).trim()
                            if (pkgName.isEmpty() || seenPackages.contains(pkgName)) continue
                            seenPackages.add(pkgName)

                            val appInfo = try {
                                pm.getApplicationInfo(pkgName, 0)
                            } catch (_: Exception) {
                                continue
                            }

                            val label = pm.getApplicationLabel(appInfo).toString()
                            val icon = pm.getApplicationIcon(pkgName)
                            val path = appInfo.sourceDir ?: ""
                            tempList.add(AppItem(label, pkgName, icon, path))
                        } catch (_: Exception) {
                            continue
                        }
                    }
                }

                val scanResult = Shell.cmd("find /data/app -name 'base.apk' 2>/dev/null").exec()
                if (scanResult.isSuccess) {
                    for (apkPath in scanResult.out) {
                        try {
                            val pkgDir = apkPath.substringBefore("/base.apk")
                            val pkgName = pkgDir.substringAfterLast("/")
                                .replace(Regex("==.*"), "")
                                .replace(Regex("-.*"), "")

                            if (pkgName.isEmpty() || seenPackages.contains(pkgName)) continue
                            val appInfo = try {
                                pm.getApplicationInfo(pkgName, 0)
                            } catch (_: Exception) {
                                continue
                            }

                            val label = pm.getApplicationLabel(appInfo).toString()
                            val icon = pm.getApplicationIcon(pkgName)
                            seenPackages.add(pkgName)
                            tempList.add(AppItem(label, pkgName, icon, apkPath))
                        } catch (_: Exception) {
                            continue
                        }
                    }
                }

                tempList.sortBy { it.label }
                allApps.clear()
                allApps.addAll(tempList)

                handler.post {
                    isLoading = false
                    searchEdit.isEnabled = true
                    loadingTxt.visibility = View.GONE
                    loadingProgress.visibility = View.GONE
                    recycler.visibility = View.VISIBLE
                    filterApps(searchText)
                    toast("Loaded (${allApps.size}) apps.")
                }
            } catch (_: Exception) {
                handler.post {
                    isLoading = false
                    searchEdit.isEnabled = true
                    loadingTxt.visibility = View.GONE
                    loadingProgress.visibility = View.GONE
                }
            }
        }.start()
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
                filteredApps.addAll(allApps.filter {
                    it.label.lowercase().contains(lowerQuery) ||
                            it.pkg.lowercase().contains(lowerQuery)
                })
            }
            adapter.notifyDataSetChanged()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun formatPermissions(perms: Array<String>?): String {
        if (perms.isNullOrEmpty()) return "None"
        val shortPerms = perms.map { p ->
            p.substringAfterLast('.')
        }
        return if (shortPerms.size <= 30) {
            shortPerms.joinToString(", ")
        } else {
            shortPerms.take(30).joinToString(", ") + ", and (${shortPerms.size - 30}) more permissions..."
        }
    }

    private fun forceStopApp(pkg: String): Boolean {
        val result = Shell.cmd("am force-stop $pkg").exec()
        return result.isSuccess
    }

    private fun clearAppData(pkg: String): Boolean {
        var result = Shell.cmd("pm clear $pkg").exec()
        if (!result.isSuccess) {
            result = Shell.cmd("pm clear --user 0 $pkg").exec()
        }
        return result.isSuccess
    }

    private fun uninstallAppFully(pkg: String, path: String): Boolean {
        var success = false

        var result = Shell.cmd("pm uninstall $pkg").exec()
        if (result.isSuccess) {
            success = true
        } else {
            result = Shell.cmd("pm uninstall --user 0 $pkg").exec()
            if (result.isSuccess) {
                success = true
            }
        }

        if (!success && path.contains("/system/")) {
            val appName = pkg.split('.').last()
            val commands = listOf("mount -o rw,remount /system 2>/dev/null",
                "rm -rf $path 2>/dev/null",
                "rm -rf /system/priv-app/$appName* 2>/dev/null",
                "rm -rf /system/app/$appName* 2>/dev/null",
                "rm -rf /data/data/$pkg 2>/dev/null",
                "rm -rf /data/user/0/$pkg 2>/dev/null",
                "rm -rf /data/dalvik-cache/*$pkg* 2>/dev/null",
                "mount -o ro,remount /system 2>/dev/null")
            commands.forEach { cmd ->
                val r = Shell.cmd(cmd).exec()
                if (r.isSuccess) success = true
            }
        }

        return success
    }

    private fun showDialog(item: AppItem) {
        if (isLoading) return
        isBusy = true

        val dialog = Dialog(requireContext(), R.style.RoundedDialog)
        val rootView = dialog.window?.decorView?.findViewById<ViewGroup>(android.R.id.content)
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dlg_app, rootView, false)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.setOnDismissListener { isBusy = false }
        val color = getThemeColor().toColorInt()

        view.findViewById<ImageView>(R.id.app_icon).setImageDrawable(item.icon)
        view.findViewById<TextView>(R.id.app_label).text = item.label
        view.findViewById<TextView>(R.id.app_pkg).text = item.pkg

        try {
            val process = Runtime.getRuntime().exec(arrayOf("du", "-sh", item.path.replace("/base.apk", "")))
            val size = process.inputStream.bufferedReader().readLine()?.split("\t")?.first() ?: "N/A"
            view.findViewById<TextView>(R.id.app_size).text = getString(R.string.app_size, size)
        } catch (_: Exception) {
            view.findViewById<TextView>(R.id.app_size).text = getString(R.string.app_size_na)
        }

        view.findViewById<TextView>(R.id.app_path).text = getString(R.string.app_path, item.path)

        try {
            val pi = requireContext().packageManager.getPackageInfo(item.pkg, PackageManager.GET_PERMISSIONS)
            val perms = pi.requestedPermissions
            val permCount = perms?.size ?: 0
            val permsText = formatPermissions(perms)
            view.findViewById<TextView>(R.id.app_perms).text = getString(R.string.app_perms, permCount, permsText)
        } catch (_: Exception) {
            view.findViewById<TextView>(R.id.app_perms).text = getString(R.string.app_perms_na)
        }

        val cancelBtn = view.findViewById<MaterialButton>(R.id.btn_cancel)
        val clearBtn = view.findViewById<MaterialButton>(R.id.btn_clear)
        val forceStopBtn = view.findViewById<MaterialButton>(R.id.btn_forcestop)
        val deleteBtn = view.findViewById<MaterialButton>(R.id.btn_del)

        val colorState = android.content.res.ColorStateList.valueOf(color)

        clearBtn.backgroundTintList = colorState
        deleteBtn.backgroundTintList = colorState
        forceStopBtn.backgroundTintList = colorState
        cancelBtn.strokeColor = colorState

        cancelBtn.setOnClickListener { dialog.dismiss() }

        forceStopBtn.setOnClickListener {
            dialog.dismiss()
            Thread {
                val success = forceStopApp(item.pkg)
                handler.post {
                    if (success) {
                        toast("${item.label} stopped.")
                    } else {
                        toast("Failed to stop.")
                    }
                }
            }.start()
        }

        clearBtn.setOnClickListener {
            dialog.dismiss()
            Thread {
                val success = clearAppData(item.pkg)
                handler.post {
                    if (success) {
                        toast(getString(R.string.app_data_cleared, item.label))
                    } else {
                        toast("Failed to clear data.")
                    }
                }
            }.start()
        }

        deleteBtn.setOnClickListener {
            dialog.dismiss()
            Thread {
                val success = uninstallAppFully(item.pkg, item.path)
                handler.post {
                    if (success) {
                        showRemovedDialog(item)
                    } else {
                        toast("Failed to uninstall.")
                    }
                    loadApps()
                }
            }.start()
        }

        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun showRemovedDialog(item: AppItem) {
        val dialog = Dialog(requireContext(), R.style.RoundedDialog)
        val rootView = dialog.window?.decorView?.findViewById<ViewGroup>(android.R.id.content)
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dlg_removed, rootView, false)
        dialog.setContentView(view)
        dialog.setCancelable(false)

        val color = getThemeColor().toColorInt()

        view.findViewById<TextView>(R.id.msg_label).text = item.label
        view.findViewById<TextView>(R.id.msg_pkg).text = item.pkg

        val rebootBtn = view.findViewById<MaterialButton>(R.id.btn_reboot)
        val laterBtn = view.findViewById<MaterialButton>(R.id.btn_later)

        rebootBtn.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        laterBtn.strokeColor = android.content.res.ColorStateList.valueOf(color)

        rebootBtn.setOnClickListener {
            dialog.dismiss()
            Shell.cmd("reboot").submit()
            toast(getString(R.string.rebooting))
        }

        laterBtn.setOnClickListener { dialog.dismiss() }

        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT)
    }

    override fun title(): String = getString(R.string.configure_device_apps)
    data class AppItem(val label: String, val pkg: String, val icon: Drawable, val path: String)

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
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return ViewHolder(view) }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.icon.setImageDrawable(item.icon)
        holder.label.text = item.label
        holder.pkg.text = item.pkg
        val color = themeColor.toColorInt()
        holder.card.strokeColor = color
        holder.card.strokeWidth = 2
        holder.editBtn.setTextColor(color)
        holder.editBtn.setOnClickListener { onEdit(item) }
    }

    override fun getItemCount(): Int = items.size

}