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
import com.topjohnwu.superuser.Shell
import kotlin.random.Random


class MacMenu : BaseMenu() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var randomCheckbox: MaterialCheckBox
    private lateinit var macTextView: TextView
    private lateinit var goButton: MaterialButton

    override fun draw(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        return inflater.inflate(R.layout.menu_mac, container, false) }

    override fun setup(view: View, state: Bundle?) {
        view.findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            parentFragmentManager.popBackStack() }
        view.findViewById<TextView>(R.id.bar_title).text = title()

        randomCheckbox = view.findViewById(R.id.chk_rand)
        macTextView = view.findViewById(R.id.txt_mac)
        goButton = view.findViewById(R.id.btn_go)
        randomCheckbox.isChecked = true

        goButton.setOnClickListener {
            if (checkRootAccess()) {
                if (isWifiEnabled()) execute() else toast(getString(R.string.wifi_disabled)) }
        }

        applyTheme()
        loadCurrentMac()
    }

    private fun applyTheme() {
        val color = getCurrentThemeColor().toColorInt()
        goButton.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        randomCheckbox.buttonTintList = android.content.res.ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_checked),
                intArrayOf(android.R.attr.state_checked)),
            intArrayOf(0xFF757575.toInt(), color))
    }

    private fun loadCurrentMac() {
        Thread {
            try {
                val mac = getCurrentMac()
                handler.post {
                    macTextView.text = getString(R.string.current_mac, mac) }
            } catch (@Suppress("UNUSED_PARAMETER") _: Exception) {
                handler.post {
                    macTextView.text = getString(R.string.current_mac, getString(R.string.error)) }
            }
        }.start()
    }

    private fun getCurrentMac(): String {
        return try {
            val result = Shell.cmd("ip link show wlan0 | grep ether | awk '{print $2}'").exec()
            if (result.isSuccess && result.out.isNotEmpty()) {
                result.out[0].trim().uppercase()
            } else {
                getString(R.string.not_found)
            }
        } catch (@Suppress("UNUSED_PARAMETER") _: Exception) {
            getString(R.string.error) }
    }

    private fun isWifiEnabled(): Boolean {
        return try {
            val result = Shell.cmd("ip link show wlan0").exec()
            result.isSuccess && result.out.any { it.contains("UP") }
        } catch (@Suppress("UNUSED_PARAMETER") _: Exception) {
            false }
    }

    private fun execute() {
        Thread {
            try {
                if (randomCheckbox.isChecked) {
                    val newMac = generateRandomMac()
                    Shell.cmd("ip link set wlan0 address $newMac").exec()
                    handler.post { macTextView.text = getString(R.string.current_mac, newMac.uppercase())
                        toast(getString(R.string.mac_updated)) }
                }
            } catch (@Suppress("UNUSED_PARAMETER") _: Exception) { }
        }.start()
    }

    private fun generateRandomMac(): String {
        val hexChars = "0123456789ABCDEF"
        val builder = StringBuilder()
        for (i in 0..5) {
            if (i > 0) builder.append(':')
            builder.append(hexChars[Random.nextInt(16)])
            builder.append(hexChars[Random.nextInt(16)]) }
        val firstByte = builder[0].toString() + builder[1]
        val newFirstByte = (firstByte.toInt(16) or 0x02).toString(16).padStart(2, '0').uppercase()
        return newFirstByte + builder.substring(2)
    }

    private fun checkRootAccess(): Boolean {
        return (activity as? AppActivity)?.isRt() ?: false }

    private fun getCurrentThemeColor(): String {
        return (activity as? AppActivity)?.getCol() ?: "#9C27B0" }

    override fun title(): String = getString(R.string.mac_address_changer)

}