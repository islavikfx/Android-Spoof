package com.islavikfx.spoof.menus
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.pm.PackageManager
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
import com.islavikfx.spoof.utils.*
import com.topjohnwu.superuser.Shell


class AndroidIdMenu : BaseMenu() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchEdit: EditText
    private lateinit var adapter: SsidAdapter
    private lateinit var loadingText: TextView
    private lateinit var loadingProgress: ProgressBar
    private val allItems = mutableListOf<SsidProto.PackageSsid>()
    private val filteredItems = mutableListOf<SsidProto.PackageSsid>()
    private var isLoading = false
    private var searchText = ""
    private val searchHandler = Handler(Looper.getMainLooper())
    private val ssidFilePath = "/data/system/users/0/settings_ssaid.xml"

    override fun draw(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        return inflater.inflate(R.layout.menu_aid, container, false)
    }

    override fun setup(view: View, state: Bundle?) {
        if (!isRootAvailable()) {
            parentFragmentManager.popBackStack()
            return
        }

        view.findViewById<ImageView>(R.id.btn_back).setOnClickListener { parentFragmentManager.popBackStack() }
        view.findViewById<TextView>(R.id.bar_title).text = title()
        recyclerView = view.findViewById(R.id.rec)
        searchEdit = view.findViewById(R.id.search)
        loadingText = view.findViewById(R.id.loading_txt)
        loadingProgress = view.findViewById(R.id.loading_progress)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = SsidAdapter(filteredItems, { item -> showEditDialog(item) }, getThemeColor())
        recyclerView.adapter = adapter

        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isLoading) return
                searchText = s?.toString()?.lowercase() ?: ""
                searchHandler.removeCallbacksAndMessages(null)
                searchHandler.postDelayed({
                    if (!isLoading && searchText == (searchEdit.text.toString().lowercase())) {
                        filterItems(searchText) }
                }, 300) }
            override fun afterTextChanged(s: Editable?) {}
        })

        val searchCard: MaterialCardView = searchEdit.parent as MaterialCardView
        searchCard.strokeColor = getThemeColor().toColorInt()
        loadItems()
    }

    private fun loadItems() {
        isLoading = true
        loadingText.visibility = View.VISIBLE
        loadingProgress.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        searchEdit.isEnabled = false
        Thread {
            try {
                val items = if (RootUtils.fileExists(ssidFilePath)) {
                    SsidProto.parseFile(ssidFilePath)
                } else { emptyList() }
                allItems.clear()
                allItems.addAll(items.sortedBy { it.packageName })
                handler.post { isLoading = false
                    searchEdit.isEnabled = true
                    loadingText.visibility = View.GONE
                    loadingProgress.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    filterItems(searchText)
                    showToast("[+] Loaded (${allItems.size}) apps.") }
            } catch (_: Exception) {
                handler.post { isLoading = false
                    searchEdit.isEnabled = true
                    loadingText.visibility = View.GONE
                    loadingProgress.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    showToast(getString(R.string.error_loading)) }
            }
        }.start()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun filterItems(query: String) {
        if (isLoading) return
        try {
            filteredItems.clear()
            if (query.isEmpty()) {
                filteredItems.addAll(allItems)
            } else {
                val lowerQuery = query.lowercase()
                filteredItems.addAll(
                    allItems.filter { it.packageName.lowercase().contains(lowerQuery) || it.ssid.lowercase().contains(lowerQuery)
                    }) }
            adapter.notifyDataSetChanged()
        } catch (_: Exception) { }
    }

    @SuppressLint("InflateParams")
    private fun showEditDialog(item: SsidProto.PackageSsid) {
        val dialog = Dialog(requireContext(), R.style.RoundedDialog)
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dlg_ssid_edit, null, false)
        dialog.setContentView(dialogView)
        dialog.setCancelable(true)

        val color = getThemeColor().toColorInt()
        val editText: EditText = dialogView.findViewById(R.id.edit_val)
        val selectedTxt: TextView = dialogView.findViewById(R.id.txt_sel)
        val currentTxt: TextView = dialogView.findViewById(R.id.txt_cur)
        val cancelBtn: MaterialButton = dialogView.findViewById(R.id.btn_cancel)
        val applyBtn: MaterialButton = dialogView.findViewById(R.id.btn_apply)
        val applyRebootBtn: MaterialButton = dialogView.findViewById(R.id.btn_apply_reboot)
        val isAndroidPackage = item.packageName == "android"
        val newRandomId = SsidProto.generateRandomId(isAndroidPackage)

        selectedTxt.text = getString(R.string.selected_show, item.packageName)
        currentTxt.text = getString(R.string.current_ssaid, item.ssid)
        editText.hint = getString(R.string.type_here)
        editText.setText(newRandomId)
        editText.backgroundTintList = android.content.res.ColorStateList.valueOf(color)

        val editCard: MaterialCardView = editText.parent as MaterialCardView
        editCard.strokeColor = color
        cancelBtn.setOnClickListener { dialog.dismiss() }
        cancelBtn.strokeColor = android.content.res.ColorStateList.valueOf(color)
        applyBtn.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        applyBtn.setOnClickListener {
            val newValue = editText.text.toString().trim()
            if (newValue.isNotEmpty() && newValue != item.ssid) {
                updateItem(item.packageName, newValue, false)
                dialog.dismiss()
            }
        }

        applyRebootBtn.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        applyRebootBtn.setOnClickListener {
            val newValue = editText.text.toString().trim()
            if (newValue.isNotEmpty() && newValue != item.ssid) {
                updateItem(item.packageName, newValue, true)
                dialog.dismiss()
            }
        }

        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun updateItem(packageName: String, newValue: String, shouldReboot: Boolean) {
        Thread {
            try {
                val success = SsidProto.updateFile(ssidFilePath, packageName, newValue)
                if (success) {
                    Shell.cmd("am force-stop $packageName 2>/dev/null").exec() }
                handler.post {
                    if (success) {
                        if (shouldReboot) {
                            showToast(getString(R.string.rebooting))
                            handler.postDelayed({ Shell.cmd("reboot").submit() }, 1000)
                        } else { showToast(getString(R.string.ssaid_updated))
                        }
                    } else { showToast(getString(R.string.update_fail)) }
                    loadItems()
                }
            } catch (_: Exception) {
                handler.post {
                    showToast(getString(R.string.update_fail)) } }
        }.start()
    }

    override fun title(): String = getString(R.string.android_id_settings)
}


class SsidAdapter(private val items: List<SsidProto.PackageSsid>,
    private val onEdit: (SsidProto.PackageSsid) -> Unit,
    private val themeColor: String) : RecyclerView.Adapter<SsidAdapter.ViewHolder>() {

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
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        try {
            val drawable = holder.icon.context.packageManager.getApplicationIcon(item.packageName)
            holder.icon.setImageDrawable(drawable)
        } catch (_: PackageManager.NameNotFoundException) {
            holder.icon.setImageResource(android.R.drawable.sym_def_app_icon)
        }
        holder.label.text = item.packageName.split('.').last().replaceFirstChar { it.uppercase() }
        holder.pkg.text = item.packageName
        val color = themeColor.toColorInt()
        holder.card.strokeColor = color
        holder.card.strokeWidth = 2
        holder.editBtn.setTextColor(color)
        holder.editBtn.setOnClickListener { onEdit(item) }
    }

    override fun getItemCount(): Int = items.size
}