/*******************************************************************************
 * This file is part of ASkyBlock.
 *
 *     ASkyBlock is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     ASkyBlock is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with ASkyBlock.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package com.wasteofplastic.askyblock.listeners;

import java.util.List;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.wasteofplastic.askyblock.ASkyBlock;
import com.wasteofplastic.askyblock.CoopPlay;
import com.wasteofplastic.askyblock.Island;
import com.wasteofplastic.askyblock.LevelCalc;
import com.wasteofplastic.askyblock.Messages;
import com.wasteofplastic.askyblock.PlayerCache;
import com.wasteofplastic.askyblock.Scoreboards;
import com.wasteofplastic.askyblock.Settings;

public class JoinLeaveEvents implements Listener {
    private ASkyBlock plugin;
    private PlayerCache players;

    public JoinLeaveEvents(ASkyBlock aSkyBlock) {
	this.plugin = aSkyBlock;
	this.players = plugin.getPlayers();
    }

    /**
     * @param event
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(final PlayerJoinEvent event) {
	// Check updates
	if (event.getPlayer().isOp() && plugin.getUpdateCheck() != null) {
	    plugin.checkUpdatesNotify((Player)event.getPlayer());
	}
	final UUID playerUUID = event.getPlayer().getUniqueId();
	if (players == null) {
	    plugin.getLogger().severe("players is NULL");
	}
	// Load any messages for the player
	// plugin.getLogger().info("DEBUG: Checking messages for " +
	// event.getPlayer().getName());
	final List<String> messages = Messages.getMessages(playerUUID);
	if (messages != null) {
	    // plugin.getLogger().info("DEBUG: Messages waiting!");
	    plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
		@Override
		public void run() {
		    event.getPlayer().sendMessage(ChatColor.AQUA + plugin.myLocale(event.getPlayer().getUniqueId()).newsHeadline);
		    int i = 1;
		    for (String message : messages) {
			event.getPlayer().sendMessage(i++ + ": " + message);
		    }
		    // Clear the messages
		    Messages.clearMessages(playerUUID);
		}
	    }, 40L);
	} // else {
	// plugin.getLogger().info("no messages");
	// }

	// If this player is not an island player just skip all this
	if (!players.hasIsland(playerUUID) && !players.inTeam(playerUUID)) {
	    return;
	}
	UUID leader = null;
	Location loc = null;
	/*
	 * This should not be needed
	 */
	if (players.inTeam(playerUUID) && players.getTeamIslandLocation(playerUUID) == null) {
	    // plugin.getLogger().info("DEBUG: reseting team island");
	    leader = players.getTeamLeader(playerUUID);
	    players.setTeamIslandLocation(playerUUID, players.getIslandLocation(leader));
	}
	// Add island to grid if it is not in there already
	// Add owners to the island grid list as they log in
	// Rationalize any out-of-sync issues, which may occur if the server wasn't shutdown properly, etc.
	// Leader or solo
	if (players.hasIsland(playerUUID)) {
	    loc = players.getIslandLocation(playerUUID);
	    leader = playerUUID;
	} else if (players.inTeam(playerUUID)) {
	    // Team player
	    loc = players.getTeamIslandLocation(playerUUID);
	    leader = players.getTeamLeader(playerUUID);
	    if (leader == null) {
		plugin.getLogger().severe("Player "+ event.getPlayer().getName() + " is in a team but leader's UUID is missing.");
	    }
	}
	// If the player has an island location of some kind
	if (loc != null && leader != null) {
	    // Check if the island location is on the grid
	    Island island = plugin.getGrid().getIslandAt(loc);
	    if (island == null) {
		//plugin.getLogger().info("DEBUG: getIslandLoc is null!");
		// Island isn't in the grid, so add it
		// See if this owner is tagged as having an island elsewhere
		Island islandByOwner = plugin.getGrid().getIsland(leader);
		if (islandByOwner == null) {
		    // No previous ownership, so just create the new island in the grid
		    plugin.getGrid().addIsland(loc.getBlockX(), loc.getBlockZ(), leader);
		} else {
		    // We have a mismatch - correct in favor of the player info
		    //plugin.getLogger().info("DEBUG: getIslandLoc is null but there is a player listing");
		    plugin.getLogger().warning(event.getPlayer().getName() + " login: mismatch - player.yml and islands.yml are out of sync. Fixing...");
		    plugin.getGrid().deleteIsland(islandByOwner.getCenter());
		    plugin.getGrid().addIsland(loc.getBlockX(), loc.getBlockZ(), leader);
		}
	    } else {
		// Island at this location exists
		//plugin.getLogger().info("DEBUG: getIslandLoc is not null - island exists");
		// See if this owner is tagged as having an island elsewhere
		Island islandByOwner = plugin.getGrid().getIsland(leader);
		if (islandByOwner == null) {
		    plugin.getLogger().warning(event.getPlayer().getName() + " login: has island, but islands.yml says it is unowned, correcting...");
		    // No previous ownership, so just assign ownership
		    plugin.getGrid().setIslandOwner(island, leader);
		} else {
		    if (!islandByOwner.equals(island)) {
			//plugin.getLogger().info("DEBUG: mismatch");
			plugin.getLogger().warning(event.getPlayer().getName() + " login: mismatch - islands.yml and player.yml are out of sync. Fixing...");
			// We have a mismatch - correct in favor of the player info
			plugin.getGrid().deleteIsland(islandByOwner.getCenter());
			plugin.getGrid().setIslandOwner(island, leader);
		    } else {
			//plugin.getLogger().info("DEBUG: everything looks good");
		    }
		}
	    }
	}
	// Run the level command if it's free to do so
	if (Settings.loginLevel) {
	    if (!plugin.isCalculatingLevel()) {
		// This flag is true if the command can be used
		plugin.setCalculatingLevel(true);
		LevelCalc levelCalc = new LevelCalc(plugin, playerUUID, event.getPlayer(), true);
		levelCalc.runTaskTimer(plugin, 0L, 10L);
	    }
	}

	// Set the player's name (it may have changed)
	players.setPlayerName(playerUUID, event.getPlayer().getName());
	players.save(playerUUID);
	if (Settings.logInRemoveMobs) {
	    plugin.getGrid().removeMobs(event.getPlayer().getLocation());
	}
	// plugin.getLogger().info("Cached " + event.getPlayer().getName());

	// Set the TEAMNAME and TEAMSUFFIX variable if required
	if (Settings.setTeamName) {
	    Scoreboards.getInstance().setLevel(event.getPlayer().getUniqueId());
	}
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(final PlayerQuitEvent event) {
	// Remove from coop list
	CoopPlay.getInstance().clearMyCoops(event.getPlayer());
	CoopPlay.getInstance().clearMyInvitedCoops(event.getPlayer());
	// CoopPlay.getInstance().returnAllInventories(event.getPlayer());
	// plugin.setMessage(event.getPlayer().getUniqueId(),
	// "Hello! This is a test. You logged out");
	players.removeOnlinePlayer(event.getPlayer().getUniqueId());
    }
}