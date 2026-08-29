package com.nobodyiran.nobodyvpn.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ConnState { DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING, ERROR }

/**
 * Global VPN connection state shared between the service and the UI.
 */
object VpnState {
    private val _state = MutableStateFlow(ConnState.DISCONNECTED)
    val state: StateFlow<ConnState> = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _runningRemark = MutableStateFlow("")
    val runningRemark: StateFlow<String> = _runningRemark.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    private val _upSpeed = MutableStateFlow(0L)
    val upSpeed: StateFlow<Long> = _upSpeed.asStateFlow()

    private val _downSpeed = MutableStateFlow(0L)
    val downSpeed: StateFlow<Long> = _downSpeed.asStateFlow()

    private val _sessionUp = MutableStateFlow(0L)
    val sessionUp: StateFlow<Long> = _sessionUp.asStateFlow()

    private val _sessionDown = MutableStateFlow(0L)
    val sessionDown: StateFlow<Long> = _sessionDown.asStateFlow()

    @Volatile
    var connectingSince: Long = 0L

    fun setState(s: ConnState) {
        _state.value = s
        if (s == ConnState.DISCONNECTED || s == ConnState.ERROR) {
            connectingSince = 0L
        }
        if (s == ConnState.CONNECTING) {
            connectingSince = System.currentTimeMillis()
        }
    }

    fun setError(msg: String?) {
        _error.value = msg
        if (msg != null) setState(ConnState.ERROR)
    }

    fun setRunning(remark: String) {
        _runningRemark.value = remark
    }

    fun setSpeed(up: Long, down: Long) {
        _upSpeed.value = up
        _downSpeed.value = down
    }

    fun addSession(up: Long, down: Long) {
        _sessionUp.value += up
        _sessionDown.value += down
    }

    fun setElapsed(sec: Long) {
        _elapsedSeconds.value = sec
    }

    fun resetSession() {
        _sessionUp.value = 0
        _sessionDown.value = 0
        _elapsedSeconds.value = 0
        _upSpeed.value = 0
        _downSpeed.value = 0
        _error.value = null
    }
}
