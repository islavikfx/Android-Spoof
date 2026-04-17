package com.islavikfx.spoof.menus
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
import com.islavikfx.spoof.R
import com.islavikfx.spoof.AppActivity
import com.topjohnwu.superuser.Shell


class SettingsMenu : BaseMenu() {

    private val handler = Handler(Looper.getMainLooper())
    private val themeColors = listOf(R.id.t_purple to "#9C27B0",
        R.id.t_red to "#FF0000",
        R.id.t_blue to "#2196F3",
        R.id.t_green to "#4CAF50",
        R.id.t_pink to "#E91E63",
        R.id.t_cyan to "#00BCD4",
        R.id.t_orange to "#FF9800",
        R.id.t_teal to "#009688")

    override fun draw(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.menu_settings, container, false)

    override fun setup(view: View, state: Bundle?) {
        setupBackButton(view)
        setupTitle(view)
        setupThemes(view)
        setupClearButton(view)
        setupRebootButton(view) }

    private fun setupBackButton(view: View) {
        view.findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            parentFragmentManager.popBackStack() }
    }

    private fun setupTitle(view: View) {
        view.findViewById<TextView>(R.id.bar_title).text = title()
    }

    private fun setupClearButton(view: View) {
        view.findViewById<MaterialButton>(R.id.btn_clear).setOnClickListener {
            if (!isRtAvailable()) return@setOnClickListener
            toast(getString(R.string.working))
            handler.postDelayed({ clearCache() }, 1000) }
    }

    private fun setupRebootButton(view: View) {
        view.findViewById<MaterialButton>(R.id.btn_reboot).setOnClickListener {
            if (!isRtAvailable()) return@setOnClickListener
            toast(getString(R.string.rebooting))
            handler.postDelayed({ rebootDevice() }, 1500) }
    }

    private fun setupThemes(view: View) {
        val currentColor = getCurrentThemeColor()
        themeColors.forEach { (id, color) ->
            val themeView = view.findViewById<View>(id)
            themeView?.setOnClickListener {
                updateThemeColor(color)
                highlightSelectedTheme(view, color) }
            themeView?.isClickable = true
            themeView?.isFocusable = true
            themeView?.background = null
        }
        highlightSelectedTheme(view, currentColor)
    }

    private fun highlightSelectedTheme(view: View, selectedColor: String) {
        themeColors.forEach { (id, color) ->
            val themeView = view.findViewById<View>(id)
            val drawable = createThemeDrawable(color, color == selectedColor)
            themeView?.background = drawable }
        updateButtonColors(view, selectedColor)
    }

    private fun createThemeDrawable(color: String, isSelected: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color.toColorInt())
            cornerRadius = 12f * resources.displayMetrics.density
            if (isSelected) {
                setStroke(4, Color.WHITE)
            } else {
                setStroke(2, "#444444".toColorInt()) }
        }
    }

    private fun updateButtonColors(view: View, color: String) {
        val colorState = android.content.res.ColorStateList.valueOf(color.toColorInt())
        view.findViewById<MaterialButton>(R.id.btn_clear)?.strokeColor = colorState
        view.findViewById<MaterialButton>(R.id.btn_reboot)?.strokeColor = colorState }

    private fun updateThemeColor(color: String) {
        (activity as? AppActivity)?.setCol(color) }

    private fun getCurrentThemeColor(): String {
        return (activity as? AppActivity)?.getCol() ?: "#9C27B0" }

    private fun isRtAvailable(): Boolean {
        return (activity as? AppActivity)?.isRt() ?: false }

    private fun clearCache() {
        Thread { Shell.cmd(
            "rm -rf /data/cache/*",
            "rm -rf /data/dalvik-cache/*",
            "rm -rf /data/log/*",
            "reboot").submit() }.start()
    }

    private fun rebootDevice() {
        Shell.cmd("reboot").submit() }

    override fun title(): String = getString(R.string.settings_title)

}