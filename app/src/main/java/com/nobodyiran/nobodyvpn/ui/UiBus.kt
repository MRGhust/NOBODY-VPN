package com.nobodyiran.nobodyvpn.ui

import kotlinx.coroutines.flow.MutableStateFlow

/** Simple UI event bus for snackbars. */
object UiBus {
    val snackbar = MutableStateFlow<String?>(null)

    fun show(msg: String) {
        snackbar.value = msg
    }
}
