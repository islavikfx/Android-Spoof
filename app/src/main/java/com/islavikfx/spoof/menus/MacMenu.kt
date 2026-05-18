package com.islavikfx.spoof.menus
import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
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
import com.topjohnwu.superuser.Shell
import kotlin.random.Random


class MacMenu : BaseMenu() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var randomCheckbox: MaterialCheckBox
    private lateinit var macTextView: TextView
    private lateinit var applyButton: MaterialButton
    private lateinit var connectionStatusTextView: TextView

    override fun draw(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        return inflater.inflate(R.layout.menu_mac, container, false)
    }

    override fun setup(view: View, state: Bundle?) {
        setupBackButton(view)
        setupTitle(view)
        initializeViews(view)
        setupCheckbox()
        setupApplyButton()
        applyThemeColor()
        updateConnectionStatus()
    }

    private fun setupBackButton(view: View) {
        view.findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupTitle(view: View) {
        view.findViewById<TextView>(R.id.bar_title).text = title()
    }

    private fun initializeViews(view: View) {
        randomCheckbox = view.findViewById(R.id.chk_rand)
        macTextView = view.findViewById(R.id.txt_mac)
        applyButton = view.findViewById(R.id.btn_go)
        connectionStatusTextView = view.findViewById(R.id.txt_connection_status)
    }

    private fun setupCheckbox() {
        randomCheckbox.isChecked = true
    }

    private fun setupApplyButton() {
        applyButton.setOnClickListener {
            if (!activity.hasRoot()) {
                showToast(getString(R.string.no_root_access))
                return@setOnClickListener
            }
            if (!randomCheckbox.isChecked) {
                return@setOnClickListener
            }
            if (!isInternetConnected()) {
                showToast(getString(R.string.not_connected_x))
                return@setOnClickListener
            }
            if (!isWifiInterfaceAvailable()) {
                showToast(getString(R.string.wifi_disabled))
                return@setOnClickListener
            }
            execute()
        }
    }

    private fun applyThemeColor() {
        val color = getThemeColor().toColorInt()
        applyButton.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        val states = arrayOf(
            intArrayOf(-android.R.attr.state_checked),
            intArrayOf(android.R.attr.state_checked)
        )
        val colors = intArrayOf(0xFF757575.toInt(), color)
        val tintList = android.content.res.ColorStateList(states, colors)
        randomCheckbox.buttonTintList = tintList
    }

    private fun isWifiInterfaceAvailable(): Boolean {
        return try {
            val result = Shell.cmd("test -f /sys/class/net/wlan0/address && echo 1 || echo 0").exec()
            result.isSuccess && result.out.firstOrNull() == "1"
        } catch (_: Exception) {
            false
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun isInternetConnected(): Boolean {
        val connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                }
                else -> false
            }
        } else {
            @Suppress("DEPRECATION")
            val activeNetworkInfo = connectivityManager.activeNetworkInfo
            @Suppress("DEPRECATION")
            return activeNetworkInfo?.isConnected == true && activeNetworkInfo.type == ConnectivityManager.TYPE_WIFI
        }
    }

    private fun updateConnectionStatus() {
        Thread {
            val isWifiOn = isWifiAvailable()
            val isConnected = isInternetConnected()
            val statusText = when {
                !isWifiOn -> "Disconnected (WLAN)."
                !isConnected -> "Not Connected."
                else -> "Connected."
            }
            handler.post {
                connectionStatusTextView.text = statusText
                if (statusText == "Connected.") {
                    connectionStatusTextView.setTextColor(0xFF4CAF50.toInt())
                    loadCurrentMacAddress()
                } else {
                    connectionStatusTextView.setTextColor(0xFFF44336.toInt())
                    macTextView.text = getString(R.string.current_mac, getString(R.string.not_connected))
                }
            }
        }.start()
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun isWifiAvailable(): Boolean {
        val connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
            @Suppress("DEPRECATION")
            networkInfo?.isAvailable ?: false
        }
    }

    private fun loadCurrentMacAddress() {
        Thread {
            try {
                val mac = getCurrentMacAddress()
                handler.post {
                    macTextView.text = getString(R.string.current_mac, mac)
                }
            } catch (_: Exception) {
                handler.post {
                    macTextView.text = getString(R.string.current_mac, getString(R.string.error))
                }
            }
        }.start()
    }

    private fun getCurrentMacAddress(): String {
        return try {
            val result = Shell.cmd("cat /sys/class/net/wlan0/address 2>/dev/null").exec()
            if (result.isSuccess && result.out.isNotEmpty()) {
                val mac = result.out[0].trim().uppercase()
                if (mac.length >= 17) mac else getString(R.string.not_connected)
            } else {
                getString(R.string.not_connected)
            }
        } catch (_: Exception) {
            getString(R.string.error)
        }
    }

    private fun execute() {
        Thread {
            try {
                val newMac = generateRandomMac()
                val result = Shell.cmd("ip link set wlan0 address $newMac").exec()
                handler.post {
                    if (result.isSuccess) {
                        macTextView.text = getString(R.string.current_mac, newMac.uppercase())
                        showToast(getString(R.string.mac_updated))
                    } else {
                        showToast(getString(R.string.error))
                    }
                    updateConnectionStatus()
                }
            } catch (_: Exception) {
                handler.post {
                    showToast(getString(R.string.error))
                }
            }
        }.start()
    }

    private fun generateRandomMac(): String {
        val hexChars = "0123456789ABCDEF"
        val builder = StringBuilder()
        for (i in 0..5) {
            if (i > 0) builder.append(':')
            builder.append(hexChars[Random.nextInt(16)])
            builder.append(hexChars[Random.nextInt(16)])
        }
        val firstByte = builder[0].toString() + builder[1]
        val newFirstByte = (firstByte.toInt(16) or 0x02).toString(16).padStart(2, '0').uppercase()
        return newFirstByte + builder.substring(2)
    }

    override fun title(): String = getString(R.string.mac_address_settings)
}