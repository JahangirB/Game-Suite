package com.pippop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pippop.game.ships.Fleet
import com.pippop.game.ships.Ship
import com.pippop.game.ships.Shot
import com.pippop.ui.screens.FleetTracker
import com.pippop.ui.screens.ShipsSea
import com.pippop.ui.theme.AppTheme
import org.junit.Test

/**
 * Ship Battle mid-game: you have sunk the enemy's 2-square ship and hit another, and they have
 * sunk your 2-square ship. Both seas show the depth look and the fleet trackers sit above them.
 */
class ScreenshotShipsTest : ScreenshotBase() {
    // The enemy ships are hidden from you, so only the sunk destroyer at (7,1) is drawn; the hits at (2,6) and (2,7) are on an enemy cruiser still afloat.
    private val destroyer = Ship(7, 1, 2, true)
    private val myShots = listOf(
        Shot(0, 0, false), Shot(4, 3, false), Shot(9, 5, false), Shot(5, 8, false),
        Shot(7, 1, true), Shot(8, 1, true), Shot(2, 6, true), Shot(2, 7, true),
    )

    // Your fleet: five ships inside the sea, none touching. The enemy has sunk the destroyer at (8,2).
    private val myFleet = listOf(
        Ship(1, 1, 5, true), Ship(0, 4, 4, false), Ship(3, 5, 3, true), Ship(6, 8, 3, true), Ship(8, 2, 2, false),
    )
    private val theirShots = listOf(
        Shot(0, 0, false), Shot(5, 3, false), Shot(9, 9, false),
        Shot(2, 1, true), Shot(3, 1, true), Shot(8, 2, true), Shot(8, 3, true),
    )

    @Composable
    private fun ships() = Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val p = AppTheme.palette
        val mySunk = myFleet.filter { Fleet.isSunk(it, theirShots) }
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("Enemy sea", color = p.text)
            FleetTracker("Enemy", listOf(destroyer))
        }
        ShipsSea(
            ships = emptyList(), shots = myShots, sunk = listOf(destroyer), lastShot = myShots.last(),
            pending = 5 to 5, enemy = true, modifier = Modifier.padding(horizontal = 24.dp),
        )
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("Your sea", color = p.text)
            FleetTracker("Your", mySunk)
        }
        ShipsSea(ships = myFleet, shots = theirShots, sunk = mySunk, modifier = Modifier.padding(horizontal = 80.dp))
    }

    @Test fun shipsLight() = shot("ships", dark = false) { ships() }

    @Test fun shipsDark() = shot("ships", dark = true) { ships() }
}
