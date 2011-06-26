package com.temp.NPCTypes.Questers.Rewards;

import org.bukkit.entity.Player;

import com.temp.Economy.EconomyHandler;
import com.temp.Economy.ServerEconomyInterface;
import com.temp.NPCTypes.Questers.Reward;
import com.temp.NPCTypes.Questers.Quests.QuestManager.RewardType;

public class EconpluginReward implements Reward {
	private final double reward;
	private final boolean take;

	public EconpluginReward(double reward, boolean take) {
		this.reward = reward;
		this.take = take;
	}

	@Override
	public void grant(Player player) {
		if (EconomyHandler.useEcoPlugin()) {
			if (this.take)
				ServerEconomyInterface.subtract(player.getName(), reward);
			else
				ServerEconomyInterface.add(player.getName(), reward);
		}

	}

	@Override
	public RewardType getType() {
		return RewardType.MONEY;
	}

	@Override
	public Object getReward() {
		return reward;
	}

	@Override
	public boolean isTake() {
		return take;
	}
}