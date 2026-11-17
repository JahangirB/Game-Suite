package com.pippop.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.pippop.data.Avatar
import com.pippop.data.GameKind
import com.pippop.data.ProfileStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.robolectric.annotation.Config

/**
 * Game Store step 6, the invitation path: "Play" on a notification (cold start or while the app is open) for a game that is not on
 * the shelf waits for "Add and join" or "Not now"; nothing is joined before that.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class PartnerInviteTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val store = ProfileStore.get(app)
    private val maya = InviteFrom("device-maya", "Maya", Avatar.PANDA)
    private val x = GameKind.BLOCKS

    @Before fun offShelf() = store.removeFromShelf(x)
    @After fun tidy() = store.removeFromShelf(x)

    private fun vm() = BattleViewModel(app)

    @Test fun anOffShelfInviteWaitsForTheSheet() {
        val vm = vm()
        vm.acceptInvite("ABCD1234", x, maya)
        val pending = vm.state.value.pendingInvite
        assertNotNull(pending)
        assertEquals(x, pending!!.game)
        assertEquals("Maya", pending.from.name)
        // Nothing was joined or started: no code was filled in, nothing is busy.
        assertFalse(vm.state.value.joinCode == "ABCD1234")
        assertFalse(vm.state.value.busy)
        assertFalse(x in store.profile.value.shelf)
    }

    @Test fun addAndJoinPutsItOnTheShelfAndJoinsAsBefore() {
        val vm = vm()
        vm.acceptInvite("ABCD1234", x, maya)
        vm.confirmInvite()
        assertTrue(x in store.profile.value.shelf)
        assertNull(vm.state.value.pendingInvite)
        // As today: with no saved partner the code is filled in and joined (here, no server link to join on).
        assertEquals("ABCD1234", vm.state.value.joinCode)
    }

    @Test fun notNowDeclinesAndJoinsNothing() {
        val vm = vm()
        val before = vm.state.value.joinCode
        vm.acceptInvite("ABCD1234", x, maya)
        vm.declineInvite()
        assertNull(vm.state.value.pendingInvite)
        assertEquals(before, vm.state.value.joinCode)
        assertFalse(x in store.profile.value.shelf)
    }

    @Test fun aGameOnTheShelfIsJoinedAtOnceWithNoSheet() {
        val vm = vm()
        store.addToShelf(x)
        vm.acceptInvite("ABCD1234", x, maya)
        assertNull(vm.state.value.pendingInvite)
        assertEquals("ABCD1234", vm.state.value.joinCode)
    }

    @Test fun aYourTurnNotificationForAnOffShelfGameAsksToo() {
        val vm = vm()
        vm.acceptInvite("ABCD1234", x, InviteFrom("device-maya", "Maya", Avatar.PANDA, isInvite = false))
        assertNotNull(vm.state.value.pendingInvite)
        vm.declineInvite()
        assertNull(vm.state.value.pendingInvite)
    }
}
