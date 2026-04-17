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
import com.islavikfx.spoof.utils.RootUtils
import com.topjohnwu.superuser.Shell


class AppActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPreferences
    private var cur = "#9C27B0"
    private var rt = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.app_launch)
        Shell.enableVerboseLogging = false
        Shell.setDefaultBuilder(Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(10))
        Handler(Looper.getMainLooper()).postDelayed({
            setContentView(R.layout.app_main)
            init() }, 300)
    }

    private fun init() {
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        cur = prefs.getString(KEY, DEF) ?: DEF
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayShowTitleEnabled(false)
        chkRt()

        findViewById<ImageView>(R.id.iv_set).setOnClickListener {
            show(SettingsMenu()) }

        findViewById<TextView>(R.id.tv_gh).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/islavikfx".toUri())) }

        theme(cur)

        findViewById<MaterialCardView>(R.id.btn_ad).setOnClickListener {
            if (chkRt()) show(AdIdMenu()) }

        findViewById<MaterialCardView>(R.id.btn_aid).setOnClickListener {
            if (chkRt()) show(AndroidIdMenu()) }

        findViewById<MaterialCardView>(R.id.btn_mac).setOnClickListener {
            if (chkRt()) show(MacMenu()) }

        findViewById<MaterialCardView>(R.id.btn_props).setOnClickListener {
            if (chkRt()) show(PropsMenu()) }

        findViewById<MaterialCardView>(R.id.btn_apps).setOnClickListener {
            if (chkRt()) show(AppsMenu()) }

        findViewById<MaterialCardView>(R.id.btn_tor).setOnClickListener {
            if (chkRt()) show(TorMenu()) }
    }

    private fun show(f: Fragment) {
        supportFragmentManager.beginTransaction().replace(android.R.id.content, f)
            .addToBackStack(null)
            .commit() }

    private fun chkRt(): Boolean {
        rt = RootUtils.isRootAvailable()
        return if (!rt) {
            Toast.makeText(this, R.string.no_root_access, Toast.LENGTH_SHORT).show()
            false } else {
            true }
    }

    fun getCol(): String = cur

    fun setCol(c: String) { cur = c
        prefs.edit { putString(KEY, c) }
        theme(c) }

    private fun theme(c: String) {
        val ci = c.toColorInt()
        val buttons = listOf(findViewById(R.id.btn_ad),
            findViewById(R.id.btn_aid),
            findViewById(R.id.btn_mac),
            findViewById(R.id.btn_props),
            findViewById(R.id.btn_apps),
            findViewById<MaterialCardView>(R.id.btn_tor))
        buttons.forEach { b ->
            b.strokeColor = ci
            val linear = b.getChildAt(0) as? LinearLayout
            val arrow = linear?.getChildAt(2) as? ImageView
            arrow?.setColorFilter(ci) }
    }

    fun isRt(): Boolean = rt

    override fun onResume() {
        super.onResume()
        if (::prefs.isInitialized) {
            val sc = prefs.getString(KEY, DEF) ?: DEF
            if (sc != cur) { cur = sc
                theme(sc) }
        }
    }

    companion object {
        const val PREFS = "app_settings"
        const val KEY = "theme_color"
        const val DEF = "#9C27B0"
    }

}