package com.islavikfx.spoof.menus
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.toColorInt
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.islavikfx.spoof.R
import com.islavikfx.spoof.AppActivity
import com.islavikfx.spoof.utils.RootUtils
import com.islavikfx.spoof.utils.SsaidProto
import com.topjohnwu.superuser.Shell
import kotlin.random.Random


class AndroidIdMenu : BaseMenu() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var rebootCheckbox: MaterialCheckBox
    private lateinit var comCheckbox: MaterialCheckBox
    private lateinit var setCheckbox: MaterialCheckBox
    private lateinit var comTextView: TextView
    private lateinit var setTextView: TextView
    private lateinit var goButton: MaterialButton
    private val ssaidPath = "/data/system/users/0/settings_ssaid.xml"
    private val fallbackPath = "/data/system/users/0/settings_ssaid.xml.fallback"

    override fun draw(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        return inflater.inflate(R.layout.menu_aid, container, false) }

    override fun setup(view: View, state: Bundle?) { setupBackButton(view)
        setupTitle(view)
        initializeViews(view)
        setupCheckboxes()
        setupGoButton()
        applyTheme()
        loadCurrentIds() }

    private fun setupBackButton(view: View) {
        view.findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            parentFragmentManager.popBackStack() }
    }

    private fun setupTitle(view: View) {
        view.findViewById<TextView>(R.id.bar_title).text = title() }

    private fun initializeViews(view: View) {
        rebootCheckbox = view.findViewById(R.id.chk_reboot)
        comCheckbox = view.findViewById(R.id.chk_com)
        setCheckbox = view.findViewById(R.id.chk_set)
        comTextView = view.findViewById(R.id.txt_com)
        setTextView = view.findViewById(R.id.txt_set)
        goButton = view.findViewById(R.id.btn_go) }

    private fun setupCheckboxes() {
        rebootCheckbox.isChecked = true
        comCheckbox.isChecked = true
        setCheckbox.isChecked = true }

    private fun setupGoButton() {
        goButton.setOnClickListener {
            if (isRootAccessAvailable()) execute() }
    }

    private fun applyTheme() {
        val color = getCurrentThemeColor().toColorInt()
        goButton.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        val stateList = android.content.res.ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_checked),
                intArrayOf(android.R.attr.state_checked)),
            intArrayOf(0xFF757575.toInt(), color))
        rebootCheckbox.buttonTintList = stateList
        comCheckbox.buttonTintList = stateList
        setCheckbox.buttonTintList = stateList
    }

    private fun loadCurrentIds() {
        Thread {
            try {
                val comId = getComAndroidId()
                val settingsId = getSettingsAndroidId()
                handler.post {
                    comTextView.text = getString(R.string.current_id_com_android, comId)
                    setTextView.text = getString(R.string.current_id_settings, settingsId)
                }
            } catch (@Suppress("UNUSED_PARAMETER") _: Exception) {
                handler.post {
                    comTextView.text = getString(R.string.current_id_com_android, getString(R.string.error))
                    setTextView.text = getString(R.string.current_id_settings, getString(R.string.error)) } }
        }.start()
    }

    private fun getComAndroidId(): String {
        return try {
            if (!RootUtils.fileExists(ssaidPath)) return getString(R.string.not_found)
            val result = Shell.cmd("cat $ssaidPath | base64").exec()
            if (!result.isSuccess || result.out.isEmpty()) return getString(R.string.error)
            val decodedBytes = android.util.Base64.decode(result.out.joinToString(""), android.util.Base64.DEFAULT)
            val androidId = SsaidProto.findAndroidId(decodedBytes)
            androidId.ifEmpty { getString(R.string.not_found) }
        } catch (@Suppress("UNUSED_PARAMETER") _: Exception) {
            getString(R.string.error) }
    }

    private fun getSettingsAndroidId(): String {
        return try {
            val result = Shell.cmd("settings get secure android_id").exec()
            if (result.isSuccess && result.out.isNotEmpty() && result.out[0] != "null") {
                result.out[0].trim()
            } else {
                getString(R.string.not_found)
            }
        } catch (@Suppress("UNUSED_PARAMETER") _: Exception) {
            getString(R.string.error) }
    }

    private fun execute() {
        Thread {
            try {
                var newComId = ""
                var newSettingsId = ""

                if (comCheckbox.isChecked && RootUtils.fileExists(ssaidPath)) {
                    newComId = updateComAndroidId() }
                if (setCheckbox.isChecked) {
                    newSettingsId = updateSettingsAndroidId() }
                updateUIAfterUpdate(newComId, newSettingsId)

                if (rebootCheckbox.isChecked) {
                    scheduleReboot() }
            } catch (@Suppress("UNUSED_PARAMETER") _: Exception) {
            }
        }.start()
    }

    private fun updateComAndroidId(): String {
        val result = Shell.cmd("cat $ssaidPath | base64").exec()
        if (!result.isSuccess || result.out.isEmpty()) return ""

        var decodedBytes = android.util.Base64.decode(result.out.joinToString(""), android.util.Base64.DEFAULT)
        val newId = generateRandomHex(64).uppercase()
        decodedBytes = SsaidProto.replaceAndroidId(decodedBytes, newId)
        val encodedBytes = android.util.Base64.encodeToString(decodedBytes, android.util.Base64.DEFAULT)
        Shell.cmd("echo '$encodedBytes' | base64 -d > $ssaidPath").exec()
        Shell.cmd("cp $ssaidPath $fallbackPath").exec()
        Shell.cmd("chmod 660 $ssaidPath").exec()
        Shell.cmd("chmod 660 $fallbackPath").exec()

        return newId
    }

    private fun updateSettingsAndroidId(): String {
        val newId = generateRandomHex(16).lowercase()
        Shell.cmd("settings put secure android_id $newId").exec()
        return newId }

    private fun updateUIAfterUpdate(newComId: String, newSettingsId: String) {
        handler.post {
            if (newComId.isNotEmpty()) {
                comTextView.text = getString(R.string.current_id_com_android, newComId)
            }
            if (newSettingsId.isNotEmpty()) {
                setTextView.text = getString(R.string.current_id_settings, newSettingsId)
            }
            toast(getString(R.string.android_id_updated)) }
    }

    private fun scheduleReboot() {
        handler.post { toast(getString(R.string.rebooting)) }
        handler.postDelayed({ Shell.cmd("reboot").submit() }, 1000)
    }

    private fun generateRandomHex(length: Int): String {
        return (1..length).map { "0123456789ABCDEF"[Random.nextInt(16)] }.joinToString("") }

    private fun isRootAccessAvailable(): Boolean {
        return (activity as? AppActivity)?.isRt() ?: false }

    private fun getCurrentThemeColor(): String {
        return (activity as? AppActivity)?.getCol() ?: "#9C27B0" }

    override fun title(): String = getString(R.string.reset_android_id)

}