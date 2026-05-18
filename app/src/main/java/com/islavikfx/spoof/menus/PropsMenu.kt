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
import com.topjohnwu.superuser.Shell.cmd


class PropsMenu : BaseMenu() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchView: EditText
    private lateinit var adapter: PropsAdapter
    private val allItems = mutableListOf<PropItem>()
    private val filteredItems = mutableListOf<PropItem>()
    private val moduleDir = "/data/adb/modules/AndroidSpoofApp"
    private var warningShown = false
    private var searchText = ""
    private val searchHandler = Handler(Looper.getMainLooper())

    
    override fun draw(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        return inflater.inflate(R.layout.menu_props, container, false)
    }

    
    override fun setup(view: View, state: Bundle?) {
        if (!isRootAvailable()) {
            parentFragmentManager.popBackStack()
            return
        }

        view.findViewById<ImageView>(R.id.btn_back).setOnClickListener { parentFragmentManager.popBackStack() }
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
                    if (searchText == searchView.text.toString().lowercase()) {
                        filterList(searchText)
                    }}, 300)
            }

            override fun afterTextChanged(s: Editable?) {}
        })
        
        val searchCard: MaterialCardView = searchView.parent as MaterialCardView
        searchCard.strokeColor = getThemeColor().toColorInt()
        searchView.visibility = View.GONE
        if (!warningShown) {
            showWarningDialog()
        } else {
            searchView.visibility = View.VISIBLE
            loadPropsData()
        }
    }

    
    @SuppressLint("InflateParams")
    private fun showWarningDialog() {
        val dialog = Dialog(requireContext(), R.style.RoundedDialog)
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dlg_warn, null, false)
        dialog.setContentView(dialogView)
        dialog.setCancelable(false)

        val okButton: MaterialButton = dialogView.findViewById(R.id.btn_ok)
        okButton.backgroundTintList = android.content.res.ColorStateList.valueOf(getThemeColor().toColorInt())
        okButton.setOnClickListener { dialog.dismiss()
            warningShown = true
            searchView.visibility = View.VISIBLE
            ensureModuleExists()
            loadPropsData() }
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
    }

    
    private fun ensureModuleExists() {
        Thread {
            try { val modulePropExists = cmd("test -f $moduleDir/module.prop && echo 1 || echo 0").exec().out.firstOrNull() == "1"
                if (modulePropExists) return@Thread
                cmd("mkdir -p $moduleDir").exec()
                createCustomPropFile()
                createSePolicyRule()
                createPostFsDataScript()
                createServiceScript()
                createModuleProp()
                createSystemProp()
            } catch (_: Exception) {
            }
        }.start()
    }

    
    private fun createCustomPropFile() {
        val content = """ro.boot.verifiedbootstate=green
ro.boot.flash.locked=1
ro.boot.warranty_bit=0"""
        cmd("echo '$content' > $moduleDir/custom.prop").exec()
        cmd("chmod 644 $moduleDir/custom.prop").exec()
    }

    
    private fun createSePolicyRule() {
        val content = """allow init self:capability sys_admin
allow init kernel system syslog_read
allow init self:property_service property_set
allow init system_file file execmod
allow { init -shell } property_type property_service set"""
        cmd("echo '$content' > $moduleDir/sepolicy.rule").exec()
        cmd("chmod 644 $moduleDir/sepolicy.rule").exec()
    }

    
    private fun createPostFsDataScript() {
        val content = $$"""#!/system/bin/sh
dir=${0%/*}

if [ -f "$""" + """dir/custom.prop" ]; then
    while IFS='=' read -r name value; do
        case "$""" + """name" in
            ""|\#*) continue ;;
            *) resetprop -n "$""" + """name" "$""" + """value" ;;
        esac
    done < "$""" + """dir/custom.prop"
fi

resetprop -n ro.boot.verifiedbootstate green
resetprop -n ro.boot.flash.locked 1
resetprop -n ro.boot.warranty_bit 0

exit 0"""
        cmd("echo '$content' > $moduleDir/post-fs-data.sh").exec()
        cmd("chmod 755 $moduleDir/post-fs-data.sh").exec()
    }


    private fun createServiceScript() {
        val content = """#!/system/bin/sh
dir=$""" + """{'$'}{0%/*}

until [ "$(getprop sys.boot_completed)" = "1" ]; do
    sleep 1
done

sleep 2

if [ -f "$""" + """dir/custom.prop" ]; then
    while IFS='=' read -r name value; do
        case "$""" + """name" in
            ""|\#*) continue ;;
            *) resetprop -n "$""" + """name" "$""" + """value" ;;
        esac
    done < "$""" + """dir/custom.prop"
fi

exit 0"""
        cmd("echo '$content' > $moduleDir/service.sh").exec()
        cmd("chmod 755 $moduleDir/service.sh").exec()
    }


    private fun createModuleProp() {
        val content = """id=AndroidSpoofApp
name=Android Spoof Props (App)
version=1.1.0
versionCode=110
author=iSlavik (@islavikfx)
description=Module generated automatically by Android Spoof App. This module working with Magisk Framework and set your custom props."""
        cmd("echo '$content' > $moduleDir/module.prop").exec()
        cmd("chmod 644 $moduleDir/module.prop").exec()
    }


    private fun createSystemProp() {
        val content = """ro.boot.verifiedbootstate=green
ro.boot.flash.locked=1
ro.boot.warranty_bit=0"""
        cmd("echo '$content' > $moduleDir/system.prop").exec()
        cmd("chmod 644 $moduleDir/system.prop").exec()
    }


    private fun loadPropsData() {
        Thread {
            try {
                val result = cmd("resetprop").exec()
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
                    Toast.makeText(requireContext(), getString(R.string.error_props), Toast.LENGTH_SHORT).show()
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
                filteredItems.addAll(
                    allItems.filter {
                        it.name.lowercase().contains(lowerQuery) ||
                                it.value.lowercase().contains(lowerQuery)
                    }
                )
            }
            adapter.notifyDataSetChanged()
        } catch (_: Exception) {
        }
    }


    @SuppressLint("InflateParams")
    private fun showEditDialog(item: PropItem) {
        val dialog = Dialog(requireContext(), R.style.RoundedDialog)
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dlg_edit, null, false)

        dialog.setContentView(dialogView)
        dialog.setCancelable(true)
        val color = getThemeColor().toColorInt()
        val editText: EditText = dialogView.findViewById(R.id.edit_val)
        val selectedTxt: TextView = dialogView.findViewById(R.id.txt_sel)
        val currentTxt: TextView = dialogView.findViewById(R.id.txt_cur)
        val cancelBtn: MaterialButton = dialogView.findViewById(R.id.btn_cancel)
        val saveBtn: MaterialButton = dialogView.findViewById(R.id.btn_save)
        editText.hint = getString(R.string.type_here)
        editText.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        selectedTxt.text = getString(R.string.selected_show, item.name)
        currentTxt.text = getString(R.string.current_value, item.value)

        cancelBtn.setOnClickListener { dialog.dismiss() }
        saveBtn.setOnClickListener {
            val newVal = editText.text.toString()
            if (newVal.isNotEmpty()) {
                saveProperty(item.name, newVal)
                dialog.dismiss()
            }
        }

        saveBtn.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        cancelBtn.strokeColor = android.content.res.ColorStateList.valueOf(color)
        val editCard: MaterialCardView = editText.parent as MaterialCardView
        editCard.strokeColor = color

        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun saveProperty(name: String, value: String) {
        Thread {
            try {
                if (!isSafeProp(name)) {
                    handler.post {Toast.makeText(requireContext(), getString(R.string.error_props_critical), Toast.LENGTH_LONG).show()
                    }
                return@Thread }

                cmd("resetprop -n $name \"$value\"").exec()
                val customPropFile = "$moduleDir/custom.prop"
                val existing = mutableMapOf<String, String>()
                val readResult = cmd("cat $customPropFile 2>/dev/null").exec()
                if (readResult.isSuccess) {
                    for (line in readResult.out) {
                        if (line.contains("=") && !line.startsWith("#")) {
                            val parts = line.split("=", limit = 2)
                            if (parts.size == 2) {
                                existing[parts[0]] = parts[1]
                            }
                        }
                    }
                }

                existing[name] = value
                val sb = StringBuilder()
                for ((propName, propValue) in existing) {
                    sb.append("$propName=$propValue\n")
                }
                cmd("echo '${sb.toString()}' > $customPropFile").exec()
                cmd("chmod 644 $customPropFile").exec()
                updateSystemProp(name, value)

                handler.post {Toast.makeText(requireContext(), getString(R.string.prop_saved),Toast.LENGTH_LONG).show()
                loadPropsData()
                }

            } catch (_: Exception) {
                handler.post {Toast.makeText(requireContext(), getString(R.string.error), Toast.LENGTH_SHORT).show()}
            }
        }.start()
    }

    private fun updateSystemProp(name: String, value: String) {
        val sysPropFile = "$moduleDir/system.prop"
        val existing = cmd("cat $sysPropFile 2>/dev/null").exec().out
        val newLines = mutableListOf<String>()
        var found = false

        for (line in existing) {
            if (line.startsWith("$name=")) {
                newLines.add("$name=$value")
                found = true
            } else {
                newLines.add(line)
            }
        }
        if (!found) {
            newLines.add("$name=$value")
        }

        val content = newLines.joinToString("\n")
        cmd("echo '$content' > $sysPropFile").exec()
        cmd("chmod 644 $sysPropFile").exec()
    }

    private fun isSafeProp(name: String): Boolean {
        val critical = listOf("dalvik.vm", "dev.mnt", "init.svc", "mdc.sys", "ro.hardware", "sys.boot", "ro.baseband", "boot.", "ro.crypto.", "security.", "keystore.", "knox.",
            "persist.", "vendor.wlan.", "vendor.bluetooth", "vendor.powerhal", "debug.sf.", "persist.adb", "ro.adb.", "ro.debuggable", "selinux.", "drm.",
            "nfc.", "ota.", "ro.bootloader", "factory", "pm.dexopt", "vold.", "persist.log.", "ro.soc.", "ro.board.platform")

        val safeBoot = listOf("ro.boot.verifiedbootstate","ro.boot.flash.locked", "ro.boot.warranty_bit", "ro.boot.vbmeta.device_state")

        if (safeBoot.any { name.startsWith(it) }) return true
        return !critical.any { name.contains(it, ignoreCase = true) }
    }

    override fun title(): String = getString(R.string.hide_props)
    data class PropItem(val name: String, val value: String)
}


class PropsAdapter(private val items: List<PropsMenu.PropItem>, private val onEdit: (PropsMenu.PropItem) -> Unit, private val theme: String) : RecyclerView.Adapter<PropsAdapter.ViewHolder>() {

    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val nameTxt: TextView = v.findViewById(R.id.prop_n)
        val valueTxt: TextView = v.findViewById(R.id.prop_v)
        val editBtn: TextView = v.findViewById(R.id.btn_edit)
        val card: MaterialCardView = v.findViewById(R.id.edit_ctr)
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_prop, parent, false)
        return ViewHolder(view)
    }


    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
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