package com.islavikfx.spoof.menus
import android.annotation.SuppressLint
import android.app.Dialog
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
import android.widget.Toast
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.islavikfx.spoof.R
import com.topjohnwu.superuser.Shell


class PropsMenu : BaseMenu() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchView: EditText
    private lateinit var adapter: PropsAdapter
    private val allItems = mutableListOf<PropItem>()
    private val filteredItems = mutableListOf<PropItem>()
    private val moduleDir = "/data/adb/modules/android_spoof_magisk"
    private var warned = false
    private var searchText = ""
    private val searchHandler = Handler(Looper.getMainLooper())

    override fun draw(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        return inflater.inflate(R.layout.menu_props, container, false) }

    override fun setup(view: View, state: Bundle?) {
        if (!isRootAvailable()) {
            parentFragmentManager.popBackStack()
            return }

        view.findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            parentFragmentManager.popBackStack() }
        view.findViewById<TextView>(R.id.bar_title).text = title()

        recyclerView = view.findViewById(R.id.rec)
        searchView = view.findViewById(R.id.search)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = PropsAdapter(filteredItems, { item -> showEditDialog(item) }, getThemeColor())
        recyclerView.adapter = adapter

        searchView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchText = s?.toString()?.lowercase() ?: ""
                searchHandler.removeCallbacksAndMessages(null)
                searchHandler.postDelayed({
                    if (searchText == (searchView.text.toString().lowercase())) {
                        filterList(searchText)
                    }
                }, 300)
            }
            override fun afterTextChanged(s: Editable?) {} })

        (searchView.parent as? MaterialCardView)?.strokeColor = getThemeColor().toColorInt()
        searchView.visibility = View.GONE

        if (!warned) {
            showWarning()
        } else {
            searchView.visibility = View.VISIBLE
            loadData()
        }
    }

    private fun showWarning() {
        val dialog = Dialog(requireContext(), R.style.RoundedDialog)
        val rootView = dialog.window?.decorView?.findViewById<ViewGroup>(android.R.id.content)
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dlg_warn, rootView, false)
        dialog.setContentView(dialogView)
        dialog.setCancelable(false)

        val okBtn = dialogView.findViewById<MaterialButton>(R.id.btn_ok)
        okBtn.backgroundTintList = android.content.res.ColorStateList.valueOf(getThemeColor().toColorInt())
        okBtn.setOnClickListener { dialog.dismiss()
            warned = true
            searchView.visibility = View.VISIBLE
            loadData() }

        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT) }

    private fun loadData() {
        Thread {
            try {
                val result = Shell.cmd("resetprop").exec()
                if (result.isSuccess) {
                    allItems.clear()
                    for (line in result.out) {
                        if (line.startsWith("[") && line.contains("]: [")) {
                            val parts = line.split("]: [")
                            if (parts.size == 2) {
                                val name = parts[0].substring(1)
                                val value = parts[1].dropLast(1)
                                allItems.add(PropItem(name, value))
                            }
                        }
                    }
                    allItems.sortBy { it.name }
                    handler.post { filterList(searchText) }
                }
            } catch (_: Exception) {
                handler.post {
                    Toast.makeText(requireContext(), "Error loading props.", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun filterList(query: String) {
        try {
            filteredItems.clear()
            if (query.isEmpty()) {
                filteredItems.addAll(allItems)
            } else {
                val lowerQuery = query.lowercase()
                filteredItems.addAll(allItems.filter {
                    it.name.lowercase().contains(lowerQuery) ||
                            it.value.lowercase().contains(lowerQuery)
                })
            }
            adapter.notifyDataSetChanged()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Остальной код без изменений...
    private fun showEditDialog(item: PropItem) {
        val dialog = Dialog(requireContext(), R.style.RoundedDialog)
        val rootView = dialog.window?.decorView?.findViewById<ViewGroup>(android.R.id.content)
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dlg_edit, rootView, false)

        dialog.setContentView(dialogView)
        dialog.setCancelable(true)

        val color = getThemeColor().toColorInt()
        val editText = dialogView.findViewById<EditText>(R.id.edit_val)
        val selectedTxt = dialogView.findViewById<TextView>(R.id.txt_sel)
        val currentTxt = dialogView.findViewById<TextView>(R.id.txt_cur)
        val cancelBtn = dialogView.findViewById<MaterialButton>(R.id.btn_cancel)
        val saveBtn = dialogView.findViewById<MaterialButton>(R.id.btn_save)

        editText.hint = "Type here..."
        editText.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        selectedTxt.text = getString(R.string.selected_prop, item.name)
        currentTxt.text = getString(R.string.current_value, item.value)
        cancelBtn.setOnClickListener { dialog.dismiss() }
        saveBtn.setOnClickListener {
            val newVal = editText.text.toString()
            if (newVal.isNotEmpty()) {
                saveProperty(item.name, newVal)
                dialog.dismiss() }
        }

        saveBtn.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        cancelBtn.strokeColor = android.content.res.ColorStateList.valueOf(color)
        (editText.parent as? MaterialCardView)?.strokeColor = color

        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT) }

    private fun saveProperty(name: String, value: String) {
        Thread {
            try {
                if (isSafeProp(name)) {
                    Shell.cmd("resetprop -n $name \"$value\"").exec()
                    saveToBoot(name, value)
                    handler.post {
                        Toast.makeText(requireContext(), "Prop saved! Reboot to apply.", Toast.LENGTH_LONG).show()
                        loadData()
                    }

                } else {
                    handler.post {
                        Toast.makeText(requireContext(), "Cannot edit critical prop.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (_: Exception) { }
        }.start()
    }

    private fun saveToBoot(name: String, value: String) {
        try {
            Shell.cmd("mkdir -p $moduleDir").exec()
            val script = "$moduleDir/service.sh"
            val moduleProp = "$moduleDir/module.prop"
            if (Shell.cmd("test -f $script && echo 1 || echo 0").exec().out.firstOrNull() == "0") {
                Shell.cmd("echo '#!/system/bin/sh' > $script").exec()
                Shell.cmd("echo '' >> $script").exec() }
            if (Shell.cmd("grep -q 'resetprop -n $name ' $script && echo 1 || echo 0").exec().out.firstOrNull() == "1") {
                Shell.cmd("sed -i 's|resetprop -n $name .*|resetprop -n $name \"$value\"|g' $script").exec()
            } else {
                Shell.cmd("echo '' >> $script").exec()
                Shell.cmd("echo \"resetprop -n $name \\\"$value\\\"\" >> $script").exec() }
            Shell.cmd("chmod 755 $script").exec()
            if (Shell.cmd("test -f $moduleProp && echo 1 || echo 0").exec().out.firstOrNull() == "0") {
                Shell.cmd("echo 'id=asp\nname=Props\nversion=1.0\nauthor=iSlavik' > $moduleProp").exec() }
        } catch (_: Exception) { }
    }

    private fun isSafeProp(name: String): Boolean {
        val critical = listOf("dalvik.vm", "dev.mnt", "init.svc", "mdc.sys", "persist.sys.", "ro.boot.",
            "ro.hardware", "sys.boot", "vendor.ril.", "ril.", "ro.baseband", "boot.",
            "ro.crypto.", "security.", "keystore.", "knox.", "persist.vendor.ims",
            "vendor.wlan.", "vendor.bluetooth", "vendor.powerhal", "debug.sf.", "net.",
            "persist.adb", "ro.adb.", "ro.debuggable", "selinux.", "drm.", "nfc.",
            "ota.", "ro.bootloader", "factory", "pm.dexopt", "vold.", "persist.log.",
            "ro.soc.", "ro.board.platform")
        return !critical.any { name.startsWith(it) }
    }

    override fun title(): String = getString(R.string.hide_props)
    data class PropItem(val name: String, val value: String)

}

class PropsAdapter(private val items: List<PropsMenu.PropItem>,
                   private val onEdit: (PropsMenu.PropItem) -> Unit,
                   private val theme: String) : RecyclerView.Adapter<PropsAdapter.ViewHolder>() {

    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val nameTxt: TextView = v.findViewById(R.id.prop_n)
        val valueTxt: TextView = v.findViewById(R.id.prop_v)
        val editBtn: TextView = v.findViewById(R.id.btn_edit)
        val card: MaterialCardView = v.findViewById(R.id.edit_ctr)
    }

    override fun onCreateViewHolder(parent: ViewGroup, type: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_prop, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, pos: Int) {
        val item = items[pos]
        holder.nameTxt.text = item.name
        holder.valueTxt.text = item.value
        val color = theme.toColorInt()
        holder.card.strokeColor = color
        holder.card.strokeWidth = 2
        holder.editBtn.setTextColor(color)
        holder.editBtn.setOnClickListener { onEdit(item) }
    }

    override fun getItemCount(): Int = items.size

}