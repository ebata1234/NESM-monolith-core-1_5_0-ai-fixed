package com.esm.nightmare.MobBehavs;

import com.esm.nightmare.GetBlockingBlock;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;

/** Horizontal block breaking toward the current player target. */
public class MobDig {

	public boolean isDoing;

	public MobDig(Entity entity) {
		if (!(entity instanceof Monster monster) || monster.swinging) {
			return;
		}

		LivingEntity target = monster.getTarget();
		if (!(target instanceof ServerPlayer player) || player.isCreative() || player.isSpectator()) {
			return;
		}

		GetBlockingBlock blocking = new GetBlockingBlock(monster);
		boolean brokeAny = false;

		// Only try positions that the collision test identified as real obstacles.
		if (blocking.isBlocking) {
			brokeAny |= MobDamageBlock.tryDamageBlock(monster, blocking.Current_Position);
		}
		if (blocking.isFootBlocked) {
			brokeAny |= MobDamageBlock.tryDamageBlock(monster, blocking.Feet_Position);
		}

		// Do not play the digging animation when the target is air, water, a
		// protected block, a blacklisted block or an unbreakable block.
		if (brokeAny) {
			monster.swing(monster.getUsedItemHand());
			isDoing = true;
		}
	}
}
