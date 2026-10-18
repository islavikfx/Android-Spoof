package com.islavikfx.spoof
import android.app.Dialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.islavikfx.spoof.menus.*
import com.islavikfx.spoof.utils.*
import com.topjohnwu.superuser.Shell


class AppActivity : AppCompatActivity() {

    private lateinit var preferences: SharedPreferences
    private lateinit var changelogPreferences: SharedPreferences
    private var currentThemeColor = "#9C27B0"
    private var currentThemeMode = MODE_DARK
    private var hasRootAccess = false
    private var uiReady = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE)
        changelogPreferences = getSharedPreferences(CHANGELOG_PREFS, MODE_PRIVATE)
        currentThemeMode = preferences.getString(KEY_MODE, MODE_DARK) ?: MODE_DARK
        try {
            setTheme(if (currentThemeMode == MODE_LIGHT) R.style.Theme_AndroidSpoof_Light else R.style.Theme_AndroidSpoof_Dark)
        } catch (_: Exception) {
            setTheme(R.style.Theme_AndroidSpoof_Dark)
            currentThemeMode = MODE_DARK
        }
        setContentView(R.layout.app_launch)
        try {
            Shell.enableVerboseLogging = false
            Shell.setDefaultBuilder(Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(10))
        } catch (_: Exception) { }
        Handler(Looper.getMainLooper()).postDelayed({
            RootUtils.init(this)
            setContentView(R.layout.app_main)
            uiReady = false
            initializeApp()
            uiReady = true
            applyTheme(currentThemeColor)
        }, 300)
    }

    private fun initializeApp() {
        currentThemeColor = preferences.getString(KEY, DEFAULT_COLOR) ?: DEFAULT_COLOR
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayShowTitleEnabled(false)
        checkRootAccess()

        findViewById<ImageView>(R.id.iv_set).setOnClickListener {
            openFragment(SettingsMenu()) }
        findViewById<TextView>(R.id.tv_gh).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/islavikfx".toUri())) }
        showChangelogIfNeeded()

        findViewById<MaterialCardView>(R.id.btn_ad).setOnClickListener {
            if (checkRootAccess())
                openFragment(AdIdMenu()) }
        findViewById<MaterialCardView>(R.id.btn_aid).setOnClickListener {
            if (checkRootAccess())
                openFragment(AndroidIdMenu()) }
        findViewById<MaterialCardView>(R.id.btn_mac).setOnClickListener {
            if (checkRootAccess())
                openFragment(MacMenu()) }
        findViewById<MaterialCardView>(R.id.btn_props).setOnClickListener {
            if (checkRootAccess())
                openFragment(PropsMenu()) }
        findViewById<MaterialCardView>(R.id.btn_apps).setOnClickListener {
            if (checkRootAccess())
                try {
                    SELinuxBypass.executeWithBypass("cd /")
                    SELinuxBypass.installPolicy()
                } catch (_: Exception) { }
            openFragment(AppsMenu()) }
    }

    private fun showChangelogIfNeeded() {
        if (changelogPreferences.getBoolean(KEY_CHANGELOG, false)) return
        val dialog = Dialog(this, R.style.RoundedDialog)
        val view = LayoutInflater.from(this).inflate(R.layout.dlg_changelog, null, false)
        dialog.setContentView(view)
        dialog.setCancelable(false)
        val okButton: MaterialButton = view.findViewById(R.id.btn_ok)
        okButton.backgroundTintList = android.content.res.ColorStateList.valueOf(currentThemeColor.toColorInt())
        okButton.setOnClickListener {
            changelogPreferences.edit { putBoolean(KEY_CHANGELOG, true) }
            dialog.dismiss() }
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun openFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, fragment)
            .addToBackStack(null)
            .commit() }

    private fun checkRootAccess(): Boolean {
        hasRootAccess = RootUtils.isRootAvailable()
        if (!hasRootAccess) { Toast.makeText(this, R.string.no_root_access, Toast.LENGTH_SHORT).show() }
        return hasRootAccess }

    fun getThemeColor(): String = currentThemeColor

    fun setThemeColor(color: String) { currentThemeColor = color
        preferences.edit { putString(KEY, color) }
        applyTheme(color) }

    fun getThemeMode(): String = currentThemeMode

    fun setThemeMode(mode: String) {
        if (mode == currentThemeMode) return
        currentThemeMode = mode
        preferences.edit { putString(KEY_MODE, mode) }
        recreate() }

    private fun applyTheme(color: String) {
        if (!uiReady) return
        val colorInt = color.toColorInt()
        val ad: MaterialCardView = findViewById(R.id.btn_ad) ?: return
        val aid: MaterialCardView = findViewById(R.id.btn_aid) ?: return
        val mac: MaterialCardView = findViewById(R.id.btn_mac) ?: return
        val props: MaterialCardView = findViewById(R.id.btn_props) ?: return
        val apps: MaterialCardView = findViewById(R.id.btn_apps) ?: return
        listOf(ad, aid, mac, props, apps).forEach { button -> button.strokeColor = colorInt
            val linearLayout = button.getChildAt(0) as? LinearLayout
            val arrowIcon = linearLayout?.getChildAt(2) as? ImageView
            arrowIcon?.setColorFilter(colorInt) }
    }

    fun hasRoot(): Boolean = hasRootAccess

    override fun onResume() {
        super.onResume()
        if (!uiReady) return
        val savedColor = preferences.getString(KEY, DEFAULT_COLOR) ?: DEFAULT_COLOR
        if (savedColor != currentThemeColor) { currentThemeColor = savedColor
            applyTheme(savedColor) }
    }

    companion object {
        const val PREFS = "app_settings"
        const val CHANGELOG_PREFS = "app_changelog"
        const val KEY = "theme_color"
        const val KEY_MODE = "theme_mode"
        const val KEY_CHANGELOG = "changelog_shown"
        const val MODE_DARK = "dark"
        const val MODE_LIGHT = "light"
        const val DEFAULT_COLOR = "#9C27B0"
    }

}
