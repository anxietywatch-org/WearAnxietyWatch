package com.anxietywatch.wear.monitoring

import com.anxietywatch.wear.domain.MonitoringState
import com.anxietywatch.wear.domain.UserResponse
import org.junit.Assert.assertEquals
import org.junit.Test

class MonitoringStateMachineTest {
    @Test
    fun `support request enters intervention but never confirms SOS automatically`() {
        val machine = MonitoringStateMachine()
        machine.onDetection(MonitoringState.USER_VALIDATION)

        assertEquals(MonitoringState.INTERVENTION, machine.onUserResponse(UserResponse.SUPPORT_REQUESTED))
        assertEquals(MonitoringState.INTERVENTION, machine.state)
    }

    @Test
    fun `manual SOS requires an explicit second confirmation`() {
        val machine = MonitoringStateMachine()

        assertEquals(MonitoringState.SOS_PENDING, machine.onUserResponse(UserResponse.SOS_REQUESTED))
        assertEquals(MonitoringState.SOS_ACTIVE, machine.confirmSos())
        assertEquals(MonitoringState.RESOLVED, machine.onUserResponse(UserResponse.SOS_CANCELLED))
    }
}
