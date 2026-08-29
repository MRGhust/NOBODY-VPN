package com.nobodyiran.nobodyvpn

import android.Manifest
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.nobodyiran.nobodyvpn.data.Repo
import com.nobodyiran.nobodyvpn.service.NobodyVpnService
import com.nobodyiran.nobodyvpn.ui.NobodyApp
import com.nobodyiran.nobodyvpn.ui.UiBus
import com.nobodyiran.nobodyvpn.ui.theme.NobodyTheme

class MainActivity : AppCompatActivity() {

    /** Fires when the user answers the system VPN consent dialog. */
    private val vpnConsentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == RESULT_OK) {
                NobodyVpnService.start(this, NobodyVpnService.ACTION_CONNECT)
            } else {
                UiBus.show(getString(R.string.err_vpn_prepare))
            }
        }

    /** Requests VPN consent if needed, then connects. Used by Tile / notification deep-link too. */
    fun requestVpnAndConnect() {
        val consent = try {
            VpnService.prepare(this)
        } catch (_: Exception) {
            null
        }
        if (consent != null) vpnConsentLauncher.launch(consent)
        else NobodyVpnService.start(this, NobodyVpnService.ACTION_CONNECT)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repo = Repo.get(this)

        // Deep-link from the Quick Settings tile when VPN permission is still missing
        if (intent?.getBooleanExtra(EXTRA_REQUEST_CONNECT, false) == true) {
            intent.removeExtra(EXTRA_REQUEST_CONNECT)
            requestVpnAndConnect()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }

        setContent {
            val settings by repo.settings.collectAsState()

            // Language is owned by AppCompatDelegate (persisted via AppLocalesMetadataHolderService
            // with autoStoreLocales in the manifest). Do NOT sync it from DataStore here: on every
            // (re)creation the DataStore state may briefly be the default value and calling
            // setApplicationLocales() from this effect would bounce the user back to English.
            // The Settings screen applies locales directly on selection.

            NobodyTheme(themeMode = settings.themeMode, dynamicColors = settings.dynamicColors) {
                NobodyApp()
            }
        }
    }

    companion object {
        const val EXTRA_REQUEST_CONNECT = "request_connect"
    }
}
