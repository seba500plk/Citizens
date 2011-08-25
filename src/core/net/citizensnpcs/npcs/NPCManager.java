package net.citizensnpcs.npcs;

import java.util.List;
import java.util.Map;

import net.citizensnpcs.Citizens;
import net.citizensnpcs.PermissionManager;
import net.citizensnpcs.SettingsManager;
import net.citizensnpcs.api.events.NPCCreateEvent;
import net.citizensnpcs.api.events.NPCCreateEvent.NPCCreateReason;
import net.citizensnpcs.api.events.NPCRemoveEvent.NPCRemoveReason;
import net.citizensnpcs.properties.PropertyManager;
import net.citizensnpcs.resources.npclib.HumanNPC;
import net.citizensnpcs.resources.npclib.NPCList;
import net.citizensnpcs.resources.npclib.NPCSpawner;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.google.common.collect.MapMaker;

public class NPCManager {
	public static final Map<Integer, String> GlobalUIDs = new MapMaker()
			.makeMap();
	private static NPCList list = new NPCList();

	public static HumanNPC get(int UID) {
		return list.get(UID);
	}

	public static HumanNPC get(Entity entity) {
		return list.getNPC(entity);
	}

	/**
	 * Gets the list of NPCs.
	 * 
	 * @return list of NPCs on a server
	 */
	public static NPCList getList() {
		return list;
	}

	/**
	 * Gets the global list of UIDs.
	 * 
	 * @return list of NPC UIDs
	 */
	public Map<Integer, String> getUIDs() {
		return GlobalUIDs;
	}

	/**
	 * Checks if a given entity is an npc.
	 * 
	 * @param entity
	 *            Bukkit Entity
	 * @return true if the entity is an NPC
	 */
	public static boolean isNPC(Entity entity) {
		return list.getNPC(entity) != null;
	}

	// Rotates an NPC.
	public static void facePlayer(HumanNPC npc, Player player) {
		Location loc = npc.getLocation(), pl = player.getLocation();
		double xDiff = pl.getX() - loc.getX();
		double yDiff = pl.getY() - loc.getY();
		double zDiff = pl.getZ() - loc.getZ();
		double DistanceXZ = Math.sqrt(xDiff * xDiff + zDiff * zDiff);
		double DistanceY = Math.sqrt(DistanceXZ * DistanceXZ + yDiff * yDiff);
		double yaw = (Math.acos(xDiff / DistanceXZ) * 180 / Math.PI);
		double pitch = (Math.acos(yDiff / DistanceY) * 180 / Math.PI) - 90;
		if (zDiff < 0.0) {
			yaw = yaw + (Math.abs(180 - yaw) * 2);
		}
		npc.teleport(loc.getX(), loc.getY(), loc.getZ(), (float) yaw - 90,
				(float) pitch);
		if (npc.getOwner().equals(player.getName())) {
			loc = npc.getNPCData().getLocation();
			loc.setPitch(npc.getLocation().getPitch());
			loc.setYaw(npc.getLocation().getYaw());
			npc.getNPCData().setLocation(loc);
		}
	}

	// Despawns an NPC.
	public static void despawn(int UID, NPCRemoveReason reason) {
		GlobalUIDs.remove(UID);
		NPCSpawner.despawnNPC(list.remove(UID), reason);
	}

	// Despawns all NPCs.
	public static void despawnAll(NPCRemoveReason reason) {
		for (Integer i : GlobalUIDs.keySet()) {
			despawn(i, reason);
		}
	}

	// Removes an NPC.
	public static void remove(int UID, NPCRemoveReason reason) {
		PropertyManager.remove(get(UID));
		despawn(UID, reason);
	}

	// Removes all NPCs.
	public static void removeAll(NPCRemoveReason reason) {
		for (Integer i : GlobalUIDs.keySet()) {
			remove(i, reason);
		}
	}

	// Removes an NPC, but not from the properties.
	public static void removeForRespawn(int UID) {
		PropertyManager.save(list.get(UID));
		despawn(UID, NPCRemoveReason.UNLOAD);
	}

	// Registers a UID in the global list.
	private static void registerUID(int UID, String name) {
		GlobalUIDs.put(UID, name);
	}

	// Checks if a player has an npc selected.
	public static boolean validateSelected(Player p) {
		return NPCDataManager.selectedNPCs.get(p.getName()) != null
				&& !NPCDataManager.selectedNPCs.get(p.getName()).toString()
						.isEmpty();
	}

	// Checks if the player has selected the given npc.
	public static boolean validateSelected(Player p, int UID) {
		return validateSelected(p)
				&& NPCDataManager.selectedNPCs.get(p.getName()) == UID;
	}

	// Checks if a player owns a given npc.
	public static boolean validateOwnership(Player player, int UID,
			boolean checkAdmin) {
		return (checkAdmin && PermissionManager.generic(player,
				"citizens.admin"))
				|| get(UID).getOwner().equals(player.getName());
	}

	// Renames an npc.
	public static void rename(int UID, String changeTo, String owner) {
		HumanNPC npc = get(UID);
		npc.getNPCData().setName(changeTo);
		removeForRespawn(UID);
		register(UID, owner, NPCCreateReason.RESPAWN);
	}

	// Sets the colour of an npc's name.
	public static void setColour(int UID, String owner) {
		removeForRespawn(UID);
		register(UID, owner, NPCCreateReason.RESPAWN);
	}

	public static void safeDespawn(HumanNPC npc) {
		NPCSpawner.despawnNPC(npc, NPCRemoveReason.UNLOAD);
	}

	// Spawns a new NPC and registers it.
	public static void register(int UID, String owner, NPCCreateReason reason) {
		Location loc = PropertyManager.getBasic().getLocation(UID);

		ChatColor colour = PropertyManager.getBasic().getColour(UID);
		String name = PropertyManager.getBasic().getName(UID);
		name = ChatColor.stripColor(name);
		if (SettingsManager.getBoolean("ConvertSlashes")) {
			name = name.replace(Citizens.separatorChar, " ");
		}
		String npcName = name;
		if (colour != ChatColor.WHITE) {
			npcName = colour + name;
		}
		HumanNPC npc = NPCSpawner.spawnNPC(UID, npcName, loc);

		NPCCreateEvent event = new NPCCreateEvent(npc, reason, loc);
		Bukkit.getServer().getPluginManager().callEvent(event);

		List<Integer> items = PropertyManager.getBasic().getItems(UID);

		npc.setNPCData(new NPCData(npcName, UID, loc, colour, items,
				NPCDataManager.NPCTexts.get(UID), PropertyManager.getBasic()
						.isLookWhenClose(UID), PropertyManager.getBasic()
						.isTalkWhenClose(UID), owner));
		PropertyManager.getBasic().saveOwner(UID, owner);
		PropertyManager.load(npc);

		registerUID(UID, npcName);
		list.put(UID, npc);
		PropertyManager.save(npc);

		npc.getPlayer().setSleepingIgnored(true); // Fix beds.
	}

	// Registers a new NPC.
	public static int register(String name, Location loc, String owner,
			NPCCreateReason reason) {
		int UID = PropertyManager.getBasic().getNewNpcID();
		PropertyManager.getBasic().saveLocation(loc, UID);
		PropertyManager.getBasic().saveLookWhenClose(UID,
				SettingsManager.getBoolean("DefaultLookAt"));
		PropertyManager.getBasic().saveTalkWhenClose(UID,
				SettingsManager.getBoolean("DefaultTalkClose"));
		PropertyManager.getBasic().saveName(UID, name);
		register(UID, owner, reason);
		return UID;
	}
}