package com.anxietywatch.wear.monitoring

import com.anxietywatch.wear.domain.MonitoringState
import com.anxietywatch.wear.domain.UserResponse

class MonitoringStateMachine(
    initialState: MonitoringState = MonitoringState.NORMAL,
) {
    var state: MonitoringState = initialState
        private set

    fun onDetection(decision: MonitoringState): MonitoringState {
        state = when (state) {
            MonitoringState.NORMAL -> when (decision) {
                MonitoringState.OBSERVING -> MonitoringState.OBSERVING
                MonitoringState.USER_VALIDATION -> MonitoringState.USER_VALIDATION
                else -> MonitoringState.NORMAL
            }
            MonitoringState.OBSERVING -> when (decision) {
                MonitoringState.USER_VALIDATION -> MonitoringState.USER_VALIDATION
                MonitoringState.NORMAL -> MonitoringState.NORMAL
                else -> MonitoringState.OBSERVING
            }
            else -> state
        }
        return state
    }

    fun onUserResponse(response: UserResponse): MonitoringState {
        state = when (response) {
            UserResponse.ACTIVITY_CONFIRMED,
            UserResponse.USER_OK,
            -> MonitoringState.COOLDOWN
            UserResponse.SUPPORT_REQUESTED -> MonitoringState.INTERVENTION
            UserResponse.NO_RESPONSE -> if (state == MonitoringState.USER_VALIDATION) {
                MonitoringState.SECOND_VALIDATION
            } else {
                MonitoringState.INTERVENTION
            }
            UserResponse.BREATHING_HELPED -> MonitoringState.RESOLVED
            UserResponse.SOS_REQUESTED -> MonitoringState.SOS_PENDING
            UserResponse.SOS_CANCELLED -> MonitoringState.RESOLVED
        }
        return state
    }

    fun confirmSos(): MonitoringState {
        state = MonitoringState.SOS_ACTIVE
        return state
    }

    fun finishResolution(): MonitoringState {
        state = MonitoringState.COOLDOWN
        return state
    }

    fun cooldownExpired(): MonitoringState {
        state = MonitoringState.NORMAL
        return state
    }

    fun reset(): MonitoringState {
        state = MonitoringState.NORMAL
        return state
    }
}
