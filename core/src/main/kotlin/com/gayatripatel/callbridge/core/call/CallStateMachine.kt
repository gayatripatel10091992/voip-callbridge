package com.gayatripatel.callbridge.core.call

/** The network a VoIP call is currently carried over. */
enum class CallNetwork { WIFI, MOBILE_DATA }

enum class EndReason { COMPLETED, DECLINED, MISSED, DROPPED, FAILED }

sealed interface CallState {
    data object Idle : CallState
    data class Dialing(val number: String, val network: CallNetwork) : CallState
    data class Ringing(val number: String) : CallState
    data class Active(
        val number: String,
        val startedAtMillis: Long,
        val network: CallNetwork,
        val muted: Boolean = false,
        val onHold: Boolean = false,
        val speaker: Boolean = false,
        /** How many times the call moved between Wi-Fi and mobile data without dropping. */
        val handovers: Int = 0,
    ) : CallState

    /** Network lost mid-call; audio is paused while we try to re-register on another network. */
    data class Reconnecting(val call: Active, val sinceMillis: Long) : CallState
    data class Ended(val number: String, val reason: EndReason, val durationMillis: Long) : CallState
}

sealed interface CallEvent {
    data class Dial(val number: String, val network: CallNetwork) : CallEvent
    data class Incoming(val number: String) : CallEvent
    data class Answer(val nowMillis: Long, val network: CallNetwork) : CallEvent
    data class RemoteAnswered(val nowMillis: Long) : CallEvent
    data class Hangup(val nowMillis: Long) : CallEvent
    data class RemoteHangup(val nowMillis: Long) : CallEvent
    data object Decline : CallEvent
    data object ToggleMute : CallEvent
    data object ToggleHold : CallEvent
    data object ToggleSpeaker : CallEvent

    /** `network == null` means connectivity was lost. */
    data class NetworkChanged(val network: CallNetwork?, val nowMillis: Long) : CallEvent
    data class Tick(val nowMillis: Long) : CallEvent
}

/**
 * Pure reducer for a single call. Keeping it free of Android types means every
 * edge case (handover, reconnect timeout, hangup while reconnecting) is unit-tested on the JVM.
 * Events that make no sense in the current state are ignored rather than crashing.
 */
object CallStateMachine {

    const val RECONNECT_TIMEOUT_MILLIS = 10_000L

    fun reduce(state: CallState, event: CallEvent): CallState = when (state) {
        CallState.Idle -> when (event) {
            is CallEvent.Dial -> CallState.Dialing(event.number, event.network)
            is CallEvent.Incoming -> CallState.Ringing(event.number)
            else -> state
        }

        is CallState.Dialing -> when (event) {
            is CallEvent.RemoteAnswered -> CallState.Active(state.number, event.nowMillis, state.network)
            is CallEvent.Hangup -> CallState.Ended(state.number, EndReason.COMPLETED, 0)
            is CallEvent.RemoteHangup -> CallState.Ended(state.number, EndReason.DECLINED, 0)
            is CallEvent.NetworkChanged ->
                if (event.network == null) CallState.Ended(state.number, EndReason.FAILED, 0)
                else state.copy(network = event.network)
            else -> state
        }

        is CallState.Ringing -> when (event) {
            is CallEvent.Answer -> CallState.Active(state.number, event.nowMillis, event.network)
            CallEvent.Decline -> CallState.Ended(state.number, EndReason.DECLINED, 0)
            is CallEvent.RemoteHangup -> CallState.Ended(state.number, EndReason.MISSED, 0)
            else -> state
        }

        is CallState.Active -> when (event) {
            CallEvent.ToggleMute -> state.copy(muted = !state.muted)
            CallEvent.ToggleHold -> state.copy(onHold = !state.onHold)
            CallEvent.ToggleSpeaker -> state.copy(speaker = !state.speaker)
            is CallEvent.Hangup, is CallEvent.RemoteHangup ->
                CallState.Ended(state.number, EndReason.COMPLETED, nowOf(event) - state.startedAtMillis)
            is CallEvent.NetworkChanged -> when (event.network) {
                null -> CallState.Reconnecting(state, event.nowMillis)
                state.network -> state
                else -> state.copy(network = event.network, handovers = state.handovers + 1)
            }
            else -> state
        }

        is CallState.Reconnecting -> when (event) {
            is CallEvent.NetworkChanged ->
                if (event.network == null) state
                else state.call.copy(network = event.network, handovers = state.call.handovers + 1)
            is CallEvent.Tick ->
                if (event.nowMillis - state.sinceMillis >= RECONNECT_TIMEOUT_MILLIS) {
                    CallState.Ended(state.call.number, EndReason.DROPPED, event.nowMillis - state.call.startedAtMillis)
                } else state
            is CallEvent.Hangup, is CallEvent.RemoteHangup ->
                CallState.Ended(state.call.number, EndReason.COMPLETED, nowOf(event) - state.call.startedAtMillis)
            else -> state
        }

        is CallState.Ended -> when (event) {
            is CallEvent.Dial -> CallState.Dialing(event.number, event.network)
            is CallEvent.Incoming -> CallState.Ringing(event.number)
            else -> state
        }
    }

    private fun nowOf(event: CallEvent): Long = when (event) {
        is CallEvent.Hangup -> event.nowMillis
        is CallEvent.RemoteHangup -> event.nowMillis
        else -> error("no timestamp on $event")
    }
}
