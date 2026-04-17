package com.islavikfx.spoof.menus
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.islavikfx.spoof.AppActivity


abstract class BaseMenu : Fragment() {

    protected lateinit var act: AppActivity
    abstract fun draw(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View
    abstract fun setup(view: View, state: Bundle?)
    abstract fun title(): String

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        act = requireActivity() as AppActivity }

    override fun onCreateView(inflater: LayoutInflater,
        container: ViewGroup?,
        state: Bundle?): View = draw(inflater, container, state)

    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)
        setup(view, state) }

    protected fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show() }

    protected fun isRootAvailable(): Boolean {
        return if (!act.isRt()) {
            toast(getString(com.islavikfx.spoof.R.string.no_root_access))
            false
        } else {
            true
        } }

    protected fun getThemeColor(): String = act.getCol()

}