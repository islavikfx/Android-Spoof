package com.islavikfx.spoof
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.islavikfx.spoof.menus.*
import com.islavikfx.spoof.utils.*
import com.topjohnwu.superuser.Shell


class AppActivity : AppCompatActivity() {

    private lateinit var preferences: SharedPreferences
    private var currentThemeColor = "#9C27B0"
    private var hasRootAccess = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
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
            initializeApp()
        }, 300)
    }

    
    private fun initializeApp() {
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE)
        currentThemeColor = preferences.getString(KEY, DEFAULT_COLOR) ?: DEFAULT_COLOR
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayShowTitleEnabled(false)
        checkRootAccess()

        findViewById<ImageView>(R.id.iv_set).setOnClickListener {
            openFragment(SettingsMenu()) }
        findViewById<TextView>(R.id.tv_gh).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/islavikfx".toUri())) }
        applyTheme(currentThemeColor)

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

    private fun applyTheme(color: String) {
        val colorInt = color.toColorInt()
        val menuButtons = listOf(findViewById<MaterialCardView>(R.id.btn_ad),
            findViewById(R.id.btn_aid),
            findViewById(R.id.btn_mac),
            findViewById(R.id.btn_props),
            findViewById(R.id.btn_apps))
        menuButtons.forEach { button -> button.strokeColor = colorInt
            val linearLayout = button.getChildAt(0) as? LinearLayout
            val arrowIcon = linearLayout?.getChildAt(2) as? ImageView
            arrowIcon?.setColorFilter(colorInt) }
    }

    fun hasRoot(): Boolean = hasRootAccess

    override fun onResume() {
        super.onResume()
        if (::preferences.isInitialized) {
            val savedColor = preferences.getString(KEY, DEFAULT_COLOR) ?: DEFAULT_COLOR
            if (savedColor != currentThemeColor) { currentThemeColor = savedColor
                applyTheme(savedColor) } }
    }

    companion object {
        const val PREFS = "app_settings"
        const val KEY = "theme_color"
        const val DEFAULT_COLOR = "#9C27B0"
    }
}
