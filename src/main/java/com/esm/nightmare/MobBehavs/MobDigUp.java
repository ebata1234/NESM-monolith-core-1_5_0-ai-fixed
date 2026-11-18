package com.esm.nightmare.MobBehavs;

import java.lang.Math;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/** Breaks actual overhead blocks when a player is above the mob. */
public class MobDigUp {

	private static final double DIG_DISTANCE = 16.0D;

	public boolean isDoing;

	public MobDigUp(Mob mob) {
		if (mob.swinging) {
			return;
		}

		Entity target = mob.getTarget();
		if (!(target instanceof ServerPlayer player) || player.isCreative() || player.isSpectator()) {
			return;
		}
		if (!isCloseEnough(mob, target) || target.blockPosition().getY() <= mob.blockPosition().getY()) {
			return;
		}

		BlockPos mobPos = mob.blockPosition();
		boolean brokeAny = MobDamageBlock.tryDamageBlock(mob, mobPos.above());
		brokeAny |= MobDamageBlock.tryDamageBlock(mob, mobPos.above(2));

		if (brokeAny) {
			mob.swing(mob.getUsedItemHand());
			isDoing = true;
		}
	}

	private boolean isCloseEnough(Mob mob, Entity target) {
		double dx = Math.abs(target.blockPosition().getX() - mob.blockPosition().getX());
		double dz = Math.abs(target.blockPosition().getZ() - mob.blockPosition().getZ());
		return dx < DIG_DISTANCE && dz < DIG_DISTANCE;
	}
}
