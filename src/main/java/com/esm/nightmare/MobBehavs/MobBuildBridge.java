package com.esm.nightmare.MobBehavs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import com.esm.nightmare.siege.SiegeProtection;

/**
 * Builds a horizontal cobblestone bridge over air or fluids when the player is
 * at least four horizontal blocks away and is at the mob's height or higher.
 */
public class MobBuildBridge {

	private static final double BUILD_UP_HORIZONTAL_RANGE = 4.0D;
	private static final Block BLOCK_TYPE = Blocks.COBBLESTONE;

	public boolean isDoing;

	public MobBuildBridge(Mob mob) {
		if (mob == null || mob.level().isClientSide) {
			return;
		}

		Entity target = mob.getTarget();
		if (!(target instanceof ServerPlayer player) || player.isCreative() || player.isSpectator()) {
			return;
		}

		Level level = mob.level();
		BlockPos mobPos = mob.blockPosition();

		// Never let a mob leave a protected area by using a block-building action.
		if (SiegeProtection.isProtected(level, mobPos)) {
			return;
		}

		double dx = target.getX() - mob.getX();
		double dz = target.getZ() - mob.getZ();
		double horizontalDistanceSqr = dx * dx + dz * dz;
		// MobBuildUp uses a 4-block X/Z square, so use the same range metric
		// here to keep the two construction behaviours mutually exclusive.
		if (Math.max(Math.abs(dx), Math.abs(dz)) < BUILD_UP_HORIZONTAL_RANGE) {
			return; // MobBuildUp owns the close-range vertical approach.
		}

		// Only bridge horizontally toward targets at roughly the same height or above.
		if (target.blockPosition().getY() < mobPos.getY()) {
			return;
		}

		if (horizontalDistanceSqr < 1.0E-6D) {
			return;
		}

		// Choose the cardinal block direction from the actual horizontal target vector.
		// Movement itself also uses this horizontal vector, never the entity's pitch.
		Direction direction = directionFromVector(dx, dz);
		mob.lookAt(target, 30.0F, 30.0F);

		BlockPos nextBodyPos = mobPos.relative(direction);
		BlockPos nextHeadPos = nextBodyPos.above();
		BlockPos bridgeBlockPos = nextBodyPos.below();
		if (!level.isInWorldBounds(nextBodyPos) || !level.isInWorldBounds(nextHeadPos)
				|| !level.isInWorldBounds(bridgeBlockPos) || !level.hasChunkAt(nextBodyPos)
				|| !level.hasChunkAt(nextHeadPos) || !level.hasChunkAt(bridgeBlockPos)) {
			return;
		}

		// A mob must not be nudged toward a protected chunk. The destination and
		// the block to be placed are in the same X/Z chunk, but check both explicitly.
		if (SiegeProtection.isProtected(level, nextBodyPos)
				|| SiegeProtection.isProtected(level, bridgeBlockPos)) {
			return;
		}

		BlockState bodyState = level.getBlockState(nextBodyPos);
		BlockState headState = level.getBlockState(nextHeadPos);
		if (!isPassable(level, nextBodyPos, bodyState) || !isPassable(level, nextHeadPos, headState)) {
			return;
		}

		BlockState floorState = level.getBlockState(bridgeBlockPos);
		boolean hasFloorSupport = isSafeSupport(level, bridgeBlockPos, floorState);
		boolean mobIsInWater = mob.isInWaterOrBubble()
				|| !level.getBlockState(mobPos).getFluidState().isEmpty();

		// If water occupies the next body cell but a solid riverbed is directly
		// underneath, make a raised cobblestone step in the water cell itself.
		// The mob then jumps one block up and out of the current instead of pushing
		// repeatedly against the water flow at floor level.
		boolean raisedWaterStep = mobIsInWater && !bodyState.getFluidState().isEmpty()
				&& hasFloorSupport;
		if (raisedWaterStep) {
			BlockPos raisedHeadPos = nextHeadPos.above();
			if (!level.isInWorldBounds(raisedHeadPos)
					|| !isPassable(level, raisedHeadPos, level.getBlockState(raisedHeadPos))) {
				return;
			}
		}

		if (hasFloorSupport && !raisedWaterStep) {
			if (mobIsInWater) {
				// There is already a solid floor ahead. Do not place/overwrite a block,
				// but actively hop forward to get out of shallow flowing water.
				stepOutOfWater(mob, direction, nextBodyPos);
				isDoing = true;
			}
			return;
		}

		BlockPos placementPos = raisedWaterStep ? nextBodyPos : bridgeBlockPos;
		if (SiegeProtection.isProtected(level, placementPos)) {
			return;
		}

		// Place into air, flowing/source water, or another replaceable block.
		// Crucially, movement happens only after placement reports success.
		MobPlaceBlock placement = new MobPlaceBlock(mob, BLOCK_TYPE, placementPos);
		if (!placement.wasPlaced()) {
			return;
		}

		mob.swing(mob.getUsedItemHand());
		stepOntoNewBridge(mob, direction, nextBodyPos, bodyState, raisedWaterStep, mobIsInWater);
		isDoing = true;
	}

	private static Direction directionFromVector(double dx, double dz) {
		if (Math.abs(dx) >= Math.abs(dz)) {
			return dx >= 0.0D ? Direction.EAST : Direction.WEST;
		}
		return dz >= 0.0D ? Direction.SOUTH : Direction.NORTH;
	}

	private static boolean isPassable(Level level, BlockPos pos, BlockState state) {
		return state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty()
				|| state.getCollisionShape(level, pos).isEmpty();
	}

	private static boolean isSafeSupport(Level level, BlockPos pos, BlockState state) {
		return state.getFluidState().isEmpty() && !state.getCollisionShape(level, pos).isEmpty()
				&& state.isFaceSturdy(level, pos, Direction.UP);
	}

	private static void stepOntoNewBridge(Mob mob, Direction direction, BlockPos nextBodyPos,
			BlockState originalBodyState, boolean raisedWaterStep, boolean mobIsInWater) {
		double targetY = mob.getY() + (raisedWaterStep ? 1.0D : 0.0D);
		double yBoost = raisedWaterStep ? 0.42D
				: ((mobIsInWater || !originalBodyState.getFluidState().isEmpty()) ? 0.22D : 0.0D);
		stepForward(mob, direction, nextBodyPos, targetY, yBoost);
	}

	private static void stepOutOfWater(Mob mob, Direction direction, BlockPos nextBodyPos) {
		stepForward(mob, direction, nextBodyPos, mob.getY(), 0.22D);
	}

	private static void stepForward(Mob mob, Direction direction, BlockPos nextBodyPos,
			double targetY, double yBoost) {
		// Stop a stale failed path from immediately steering the mob away from the
		// newly created bridge, then give both MoveControl and physical motion a nudge.
		mob.getNavigation().stop();
		mob.getMoveControl().setWantedPosition(nextBodyPos.getX() + 0.5D, targetY,
			nextBodyPos.getZ() + 0.5D, 1.15D);

		Vec3 motion = mob.getDeltaMovement();
		double push = 0.12D;
		mob.setDeltaMovement(motion.x * 0.35D + direction.getStepX() * push,
				Math.max(motion.y, yBoost),
				motion.z * 0.35D + direction.getStepZ() * push);
	}
}
