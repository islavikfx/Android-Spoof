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
            return
        }

        view.findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            parentFragmentManager.popBackStack() }
        view.findViewById<TextView>(R.id.bar_title).text = title()
        recyclerView = view.findViewById(R.id.rec)
        searchEdit = view.findViewById(R.id.search)
        loadingText = view.findViewById(R.id.loading_txt)
        loadingProgress = view.findViewById(R.id.loading_progress)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = AppsAdapter(filteredApps, { app -> if (!isBusy && !isLoading) showAppDialog(app) }, getThemeColor())
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
                    }
                }, 300)
            }
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
        } else {
            searchEdit.visibility = View.VISIBLE
            loadAppsFromPm()
        }
    }

    @SuppressLint("InflateParams")
    private fun showWarningDialog() {
        val dialog = Dialog(requireContext(), R.style.RoundedDialog)
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dlg_warn_apps, null, false)
        dialog.setContentView(view)
        dialog.setCancelable(false)
        val okButton: MaterialButton = view.findViewById(R.id.btn_ok)
        okButton.backgroundTintList = android.content.res.ColorStateList.valueOf(getThemeColor().toColorInt())
        okButton.setOnClickListener {
            dialog.dismiss()
            warningShown = true
            searchEdit.visibility = View.VISIBLE
            loadAppsFromPm()
        }
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun loadAppsFromPm() {
        isLoading = true
        loadingText.visibility = View.VISIBLE
        loadingProgress.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        searchEdit.isEnabled = false

        Thread {
            try {
                val pm = requireContext().packageManager
                val tempList = mutableListOf<AppItem>()
                val seen = mutableSetOf<String>()

                val listResult = Shell.cmd("pm list packages").exec()
                if (listResult.isSuccess) {
                    for (line in listResult.out) {
                        val pkg = line.removePrefix("package:").trim()
                        if (pkg.isEmpty() || !pkg.contains(".") || seen.contains(pkg)) continue
                        seen.add(pkg)

                        var label = pkg.substringAfterLast(".")
                        var icon: Drawable = pm.defaultActivityIcon
                        var primaryApk = ""
                        var permissions: List<String> = emptyList()

                        try {
                            val ai = pm.getApplicationInfo(pkg, 0)
                            label = pm.getApplicationLabel(ai).toString()
                            icon = ai.loadIcon(pm)
                        } catch (_: Exception) {}

                        val pathResult = Shell.cmd("pm path $pkg").exec()
                        if (pathResult.isSuccess) {
                            for (p in pathResult.out) {
                                val path = p.removePrefix("package:").trim()
                                if (path.isEmpty()) continue
                                if (path.contains("split_config")) continue
                                if (path.contains("/base.apk")) { primaryApk = path; break }
                                if (primaryApk.isEmpty()) primaryApk = path
                            }
                        }

                        if (primaryApk.isNotEmpty()) {
                            try {
                                val archive = pm.getPackageArchiveInfo(primaryApk, PackageManager.GET_PERMISSIONS)
                                val aai = archive?.applicationInfo
                                if (aai != null) {
                                    aai.sourceDir = primaryApk
                                    aai.publicSourceDir = primaryApk
                                    try { label = pm.getApplicationLabel(aai).toString() } catch (_: Exception) {}
                                    try { icon = aai.loadIcon(pm) } catch (_: Exception) {}
                                }
                                permissions = archive?.requestedPermissions?.toList() ?: emptyList()
                            } catch (_: Exception) {}
                        }

                        tempList.add(AppItem(label, pkg, icon, primaryApk, permissions))
                    }
                }

                val sorted = tempList.sortedBy { it.label.lowercase() }
                allApps.clear()
                allApps.addAll(sorted)

                handler.post {
                    isLoading = false
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
                filteredApps.addAll(allApps.filter { it.label.lowercase().contains(lowerQuery) || it.packageName.lowercase().contains(lowerQuery) })
            }
            adapter.notifyDataSetChanged()
        } catch (_: Exception) {}
    }

    private fun forceStopApp(packageName: String): Boolean {
        return Shell.cmd("am force-stop --user 0 $packageName 2>/dev/null").exec().isSuccess
    }

    private fun clearAppData(packageName: String): Boolean {
        return Shell.cmd("rm -rf /data/data/$packageName/*").exec().isSuccess
    }

    private fun uninstallAppCompletely(packageName: String): Boolean {
        try {
            Shell.cmd("am force-stop --user 0 $packageName 2>/dev/null",
                "pm disable-user --user 0 $packageName 2>/dev/null",
                "pm uninstall --user 0 $packageName 2>/dev/null").exec()
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
        view.findViewById<TextView>(R.id.app_path).text = "Path: ${item.primaryApk.ifEmpty { getString(R.string.not_found_base_apk) }}"

        if (item.permissions.isEmpty()) {
            view.findViewById<TextView>(R.id.app_perms).text = getString(R.string.no_permissions)
        } else {
            val shortPerms = item.permissions.map { it.substringAfterLast('.') }
            val permsText = if (shortPerms.size <= 5) {
                shortPerms.joinToString(", ")
            } else {
                shortPerms.take(5).joinToString(", ") + " and (${shortPerms.size - 5}) more…"
            }
            view.findViewById<TextView>(R.id.app_perms).text = getString(R.string.app_perms, permsText)
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
                handler.post {
                    showToast(getString(if (success) R.string.app_stopped else R.string.app_stop_fail, item.label))
                }
            }.start()
        }

        clearBtn.setOnClickListener {
            dialog.dismiss()
            Thread {
                val success = clearAppData(item.packageName)
                handler.post {
                    showToast(getString(if (success) R.string.app_data_cleared else R.string.app_data_clear_fail, item.label))
                }
            }.start()
        }

        deleteBtn.setOnClickListener {
            dialog.dismiss()
            Thread {
                uninstallAppCompletely(item.packageName)
                handler.post {
                    showRemovedDialog(item)
                    loadAppsFromPm()
                }
            }.start()
        }

        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
    }

    @SuppressLint("InflateParams")
    private fun showRemovedDialog(item: AppItem) {
        val dialog = Dialog(requireContext(), R.style.RoundedDialog)
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dlg_removed, null, false)
        dialog.setContentView(view)
        dialog.setCancelable(false)

        val color = getThemeColor().toColorInt()
        view.findViewById<TextView>(R.id.msg_label).text = item.label
        view.findViewById<TextView>(R.id.msg_pkg).text = item.packageName

        val rebootBtn: MaterialButton = view.findViewById(R.id.btn_reboot)
        val laterBtn: MaterialButton = view.findViewById(R.id.btn_later)
        rebootBtn.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        laterBtn.strokeColor = android.content.res.ColorStateList.valueOf(color)

        rebootBtn.setOnClickListener {
            dialog.dismiss()
            Shell.cmd("reboot").submit()
            showToast(getString(R.string.rebooting))
        }
        laterBtn.setOnClickListener { dialog.dismiss() }

        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
    }

    override fun title(): String = getString(R.string.configure_device_apps)

    data class AppItem(val label: String,
        val packageName: String, val icon: Drawable,
        val primaryApk: String, val permissions: List<String>)
}


class AppsAdapter(private val items: List<AppsMenu.AppItem>, private val onEdit: (AppsMenu.AppItem) -> Unit,
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
