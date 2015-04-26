package com.wasteofplastic.askyblock.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.wasteofplastic.askyblock.ASkyBlock;

public class DelayListener implements Listener {

	private ASkyBlock plugin;

	public DelayListener(ASkyBlock aSkyBlock) {
		this.plugin = aSkyBlock;
	}

	@EventHandler
	public void onPlayerLeave(PlayerQuitEvent event) {
		String player = event.getPlayer().getName();
		if (plugin.ids.containsKey(player)) {
			plugin.getServer().getScheduler().cancelTask(plugin.ids.get(player));
			plugin.ids.remove(player);
		}
		if (plugin.warmups.containsKey(player)) {
			plugin.warmups.remove(player);
		}
	}

	@EventHandler
	public void onPlayerMove(PlayerMoveEvent event) {
		Player player = event.getPlayer();
		if (plugin.warmups.containsKey(player.getName())) {
			if (!event.getFrom().getBlock().equals(event.getTo().getBlock())) {
				plugin.stop(player.getName());
				player.sendMessage("Warmup cancelled because you moved!");
			}
		}
	}

	@EventHandler
	public void onEntityDamage(EntityDamageByEntityEvent event) {
		if (event.getEntity() instanceof Player) {
			Player player = (Player) event.getEntity();
			if (plugin.warmups.containsKey(player.getName())) {
				plugin.stop(player.getName());
				player.sendMessage("Warmup cancelled because you got damanged!");
			}
		}
	}
}
