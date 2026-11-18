package com.esm.nightmare.MobBehavs;

import com.esm.nightmare.ESMConfig;
import com.esm.nightmare.siege.SiegeProtection;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Common gate for every NESM mob-driven block break.
 * Explosions do not use this class and therefore remain unaffected.
 */
public class MobDamageBlock {

	/**
	 * Kept for source compatibility with older NESM call sites. New code should
	 * use tryDamageBlock(), which reports whether a real block was destroyed.
	 */
	@Deprecated
	public MobDamageBlock(Entity entity, Block ignoredBlock, BlockPos pos) {
		tryDamageBlock(entity, pos);
	}

	/** Returns true only when a breakable block was actually destroyed. */
	public static boolean tryDamageBlock(Entity entity, BlockPos pos) {
		if (entity == null || pos == null) {
			return false;
		}

		Level level = entity.level();
		if (level.isClientSide || !level.isInWorldBounds(pos) || !level.hasChunkAt(pos)) {
			return false;
		}
		if (!canBreakBlock(entity, level, pos)) {
			return false;
		}

		// Same vanilla destruction path used by the original NESM: drops,
		// particles and block-break sound are all retained.
		return level.destroyBlock(pos, true);
	}

	/** Shared, side-effect-free validation used before swinging or destroying. */
	public static boolean canBreakBlock(Entity entity, Level level, BlockPos pos) {
		if (entity == null || level == null || pos == null || level.isClientSide) {
			return false;
		}
		if (!level.isInWorldBounds(pos) || !level.hasChunkAt(pos)) {
			return false;
		}

		// Never modify a block in a Monolith Core protected chunk.
		if (SiegeProtection.isProtected(level, pos)) {
			return false;
		}

		BlockState state = level.getBlockState(pos);
		if (state.isAir() || !state.getFluidState().isEmpty()) {
			return false;
		}

		// Negative destroy speed denotes an unbreakable block (e.g. bedrock,
		// barriers and command blocks under normal survival rules).
		if (state.getDestroySpeed(level, pos) < 0.0F) {
			return false;
		}

		return !isBlacklisted(state.getBlock());
	}

	private static boolean isBlacklisted(Block block) {
		ResourceLocation key = ForgeRegistries.BLOCKS.getKey(block);
		if (key == null) {
			return false;
		}

		String registryId = key.toString().toLowerCase(java.util.Locale.ROOT);
		String path = key.getPath().toLowerCase(java.util.Locale.ROOT);
		String configured = ESMConfig.BlocksEntitiesCannotDigThrough.get();
		if (configured == null || configured.isBlank()) {
			return false;
		}

		// Keep the existing config's substring semantics, but compare against
		// stable registry identifiers rather than translated display names.
		for (String rawToken : configured.split(",")) {
			String token = rawToken.trim().toLowerCase(java.util.Locale.ROOT);
			if (!token.isEmpty() && (registryId.contains(token) || path.contains(token))) {
				return true;
			}
		}
		return false;
	}
}
