package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.OfflineCompanionEngine
import com.example.model.PhoneActionType
import com.example.model.PlanTier
import com.example.model.SathiLanguage
import com.example.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name is Sathi`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Sathi", appName)
    }

    @Test
    fun `offline engine returns creator rule and offline actions`() {
        val engine = OfflineCompanionEngine()

        val creatorReply = engine.respondOffline("তোমাকে কে বানিয়েছে?", SathiLanguage.BN, "Bayzid")
        assertTrue(creatorReply.replyText.contains("মোহাম্মদ বাইজিদ হাসান"))

        val searchReply = engine.respondOffline("আজকের খবর সার্চ করো", SathiLanguage.BN, "Bayzid")
        assertEquals(PhoneActionType.NONE, searchReply.actionType)
        assertTrue(searchReply.replyText.contains("অফলাইন"))

        val flashOn = engine.respondOffline("Turn flashlight on", SathiLanguage.EN, "Bayzid")
        assertEquals(PhoneActionType.FLASHLIGHT_ON, flashOn.actionType)

        val callReply = engine.respondOffline("Call 01700000000", SathiLanguage.EN, "Bayzid")
        assertEquals(PhoneActionType.CALL_CONFIRM, callReply.actionType)
    }

    @Test
    fun `expired premium reverts to free tier`() {
        val expired = UserProfile(
            userId = "u1",
            userName = "Test",
            plan = "premium",
            planExpiresAt = System.currentTimeMillis() - 10_000L
        )
        assertEquals(PlanTier.FREE, expired.effectivePlan())
        assertFalse(expired.hasPremiumAccess())

        val owner = UserProfile(userId = "u2", userName = "Bayzid", plan = "owner", planExpiresAt = 0L)
        assertEquals(PlanTier.OWNER, owner.effectivePlan())
        assertTrue(owner.hasPremiumAccess())
    }
}
