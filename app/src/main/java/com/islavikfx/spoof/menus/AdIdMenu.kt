package com.islavikfx.spoof.menus
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Environment
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
import com.topjohnwu.superuser.Shell
import java.util.UUID


class AdIdMenu : BaseMenu() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var forceCheckbox: MaterialCheckBox
    private lateinit var resetCheckbox: MaterialCheckBox
    private lateinit var clearCheckbox: MaterialCheckBox
    private lateinit var idTextView: TextView
    private lateinit var applyButton: MaterialButton
    private val androidDataDir = "${Environment.getDataDirectory().absolutePath}/data"
    private val gmsDataDir = "$androidDataDir/com.google.android.gms"
    private val gsfDataDir = "$androidDataDir/com.google.android.gsf"
    private val adidFilePath = "$gmsDataDir/shared_prefs/adid_settings.xml"

    override fun draw(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        return inflater.inflate(R.layout.menu_adid, container, false) }

    override fun setup(view: View, state: Bundle?) { setupBackButton(view)
        setupTitle(view)
        initializeViews(view)
        setupCheckboxes()
        setupApplyButton()
        applyTheme()
        loadCurrentAdvertisingId() }

    private fun setupBackButton(view: View) {
        view.findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            parentFragmentManager.popBackStack() }
    }

    private fun setupTitle(view: View) {
        view.findViewById<TextView>(R.id.bar_title).text = title() }

    private fun initializeViews(view: View) { forceCheckbox = view.findViewById(R.id.chk_force)
        resetCheckbox = view.findViewById(R.id.chk_reset)
        clearCheckbox = view.findViewById(R.id.chk_clear)
        idTextView = view.findViewById(R.id.txt_id)
        applyButton = view.findViewById(R.id.btn_go) }

    private fun setupCheckboxes() { forceCheckbox.isChecked = true
        resetCheckbox.isChecked = true
        clearCheckbox.isChecked = false }

    private fun setupApplyButton() { applyButton.setOnClickListener {
            if (isRootAccessAvailable()) execute() }
    }

    private fun applyTheme() {
        val color = getCurrentThemeColor().toColorInt()
        applyButton.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        val states = arrayOf(intArrayOf(-android.R.attr.state_checked),
            intArrayOf(android.R.attr.state_checked))
        val colors = intArrayOf(0xFF757575.toInt(), color)
        val tintList = android.content.res.ColorStateList(states, colors)
        forceCheckbox.buttonTintList = tintList
        resetCheckbox.buttonTintList = tintList
        clearCheckbox.buttonTintList = tintList
    }

    private fun loadCurrentAdvertisingId() {
        Thread {
            try {
                val currentId = getCurrentAdvertisingId()
                handler.post {
                    idTextView.text = getString(R.string.current_id_placeholder, currentId)
                }
            } catch (@Suppress("UNUSED_PARAMETER") _: Exception) {
                handler.post {
                    idTextView.text = getString(R.string.current_id_placeholder, getString(R.string.error)) } }
        }.start()
    }

    private fun getCurrentAdvertisingId(): String {
        val result = Shell.cmd("cat $adidFilePath").exec()
        if (!result.isSuccess) return getString(R.string.not_found)
        val content = result.out.joinToString("\n")
        val pattern = Regex("<string name=\"adid_key\">([^<]+)</string>")
        val match = pattern.find(content)
        return match?.groupValues?.get(1) ?: getString(R.string.not_found)
    }

    private fun execute() {
        if (!areGoogleServicesInstalled()) {
            toast(getString(R.string.no_google_services))
            return }

        Thread {
            try {
                forceStopGoogleServices()
                if (resetCheckbox.isChecked) {
                    resetAdvertisingId() }
                if (clearCheckbox.isChecked) {
                    clearGoogleServicesData() }
                restartGoogleServices()
                handler.post { toast(getString(R.string.ad_id_updated)) }
            } catch (@Suppress("UNUSED_PARAMETER") _: Exception) { }
        }.start()
    }

    private fun forceStopGoogleServices() {
        if (!forceCheckbox.isChecked) return
        Shell.cmd("am force-stop com.google.android.gsf",
            "am force-stop com.google.android.gms").exec() }

    private fun resetAdvertisingId() {
        Shell.cmd("rm -rf $gmsDataDir/app_ads_cache", "rm -rf $gmsDataDir/app_dg_cache", "rm -rf $gmsDataDir/cache").exec()
        val newId = UUID.randomUUID().toString()
        val xmlContent = createAdIdXmlContent(newId)
        Shell.cmd("echo '$xmlContent' > $adidFilePath").exec()
        Shell.cmd("chmod 660 $adidFilePath").exec()
        handler.post { idTextView.text = getString(R.string.current_id_placeholder, newId) }
    }

    private fun createAdIdXmlContent(adId: String): String {
        return """<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="enable_debug_logging" value="false" />
    <boolean name="using_cert" value="false" />
    <string name="adid_key">$adId</string>
    <string name="fake_adid_key"></string>
    <int name="adid_reset_count" value="0" />
    <boolean name="enable_limit_ad_tracking" value="false" />
</map>"""
    }

    private fun clearGoogleServicesData() {
        Shell.cmd("rm -rf $gsfDataDir/*", "chmod 777 $gsfDataDir", "rm -rf $gmsDataDir/*", "chmod 777 $gmsDataDir").exec()
    }

    private fun restartGoogleServices() {
        if (!forceCheckbox.isChecked) return
        Shell.cmd("am start -n com.google.android.gsf/.login.LoginActivity", "am start -n com.google.android.gms/.auth.account.authenticator.AuthenticatorActivity").exec()
    }

    private fun areGoogleServicesInstalled(): Boolean {
        val gmsExists = runCatching { val result = Shell.cmd("test -d $gmsDataDir && echo 1 || echo 0").exec()
            result.out.firstOrNull() == "1" }.getOrElse { false }

        val gsfExists = runCatching {
            val result = Shell.cmd("test -d $gsfDataDir && echo 1 || echo 0").exec()
            result.out.firstOrNull() == "1" }.getOrElse { false }

        return gmsExists || gsfExists
    }

    private fun isRootAccessAvailable(): Boolean {
        return (activity as? AppActivity)?.isRt() ?: false }

    private fun getCurrentThemeColor(): String {
        return (activity as? AppActivity)?.getCol() ?: "#9C27B0" }

    override fun title(): String = getString(R.string.advertising_id_settings)

}