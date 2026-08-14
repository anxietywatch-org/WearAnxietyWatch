package com.anxietywatch.mobile.fog

import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class FogSecureStoreTest {
    private lateinit var store: FogSecureStore

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("fog_secure_v1", 0).edit().clear().commit()
        context.getSharedPreferences("fog_identity", 0).edit().clear().commit()
        store = FogSecureStore(context)
    }

    @Test fun tokenRoundTripAndClear() {
        assertNull(store.getToken())
        store.setToken("jwt")
        assertEquals("jwt", store.getToken())
        store.clearAuth()
        assertNull(store.getToken())
    }

    @Test fun identityRoundTripAndSequence() {
        store.setIdentity("user", "device", "session")
        assertEquals(1L, store.nextSequence())
        val value = JSONObject(store.getIdentity())
        assertEquals("user", value.getString("userId"))
        assertEquals("device", value.getString("deviceId"))
        assertEquals("session", value.getString("sessionId"))
        assertEquals(1L, value.getLong("sequence"))
    }
}
