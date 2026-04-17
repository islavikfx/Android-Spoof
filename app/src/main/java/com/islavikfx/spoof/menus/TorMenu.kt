package com.islavikfx.spoof.menus
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.islavikfx.spoof.R

class TorMenu : BaseMenu() {

    override fun draw(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        return inflater.inflate(R.layout.menu_tor, container, false)
    }

    override fun setup(view: View, state: Bundle?) {
        view.findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            parentFragmentManager.popBackStack() }
        view.findViewById<TextView>(R.id.bar_title).text = title()

        view.isClickable = true
        view.isFocusable = true
        view.setOnClickListener { }

        if (!isRootAvailable()) {
            parentFragmentManager.popBackStack()
        }
    }

    override fun title(): String = getString(R.string.connect_tor_vpn)

}

// # todo: Integration orbot library with obfs4 connection.