package com.islavikfx.spoof.menus
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.islavikfx.spoof.AppActivity


abstract class BaseMenu : Fragment() {

    protected lateinit var activity: AppActivity

    abstract fun draw(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View
    abstract fun setup(view: View, state: Bundle?)
    abstract fun title(): String

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        activity = requireActivity() as AppActivity
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        return draw(inflater, container, state)
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)
        setup(view, state)
    }

    protected fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    protected fun isRootAvailable(): Boolean {
        if (!activity.hasRoot()) {
            showToast(getString(com.islavikfx.spoof.R.string.no_root_access))
            return false
        }
        return true
    }

    protected fun getThemeColor(): String = activity.getThemeColor()
}