package com.gladiators.arena;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;

/** Puts back the blocks saved in the backup, chunk by chunk, a few thousand per tick. */
public final class ArenaRemover implements ArenaJob {
	private final ServerLevel level;
	private final ArenaBackup backup;
	private final int blocksPerTick;
	private final Deque<ChunkPos> queue;
	private final int totalChunks;

	private ChunkPos currentChunk;
	private ArenaBackup.ChunkData current;
	private int index;

	public ArenaRemover(ServerLevel level, ArenaBackup backup, int blocksPerTick) {
		this.level = level;
		this.backup = backup;
		this.blocksPerTick = blocksPerTick;
		this.queue = new ArrayDeque<>(backup.chunks());
		this.totalChunks = queue.size();
	}

	@Override
	public boolean tick() throws IOException {
		int budget = blocksPerTick;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		while (budget > 0) {
			if (current == null) {
				currentChunk = queue.poll();
				if (currentChunk == null) {
					return true;
				}
				current = backup.read(currentChunk);
				index = 0;
				level.getChunk(currentChunk.x, currentChunk.z); // make sure it is loaded
			}
			while (budget > 0 && index < current.pos().length) {
				int p = current.pos()[index];
				pos.set(currentChunk.getMinBlockX() + (p & 15), current.minY() + (p >> 8), currentChunk.getMinBlockZ() + ((p >> 4) & 15));
				level.setBlock(pos, current.states()[index], ArenaBuilder.FLAGS);
				index++;
				budget--;
			}
			if (index >= current.pos().length) {
				restoreBlockEntities();
				backup.delete(currentChunk);
				current = null;
			}
		}
		return false;
	}

	private void restoreBlockEntities() {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (CompoundTag tag : current.blockEntities()) {
			int p = tag.getIntOr("GladiatorsPos", -1);
			if (p < 0) {
				continue;
			}
			pos.set(currentChunk.getMinBlockX() + (p & 15), current.minY() + (p >> 8), currentChunk.getMinBlockZ() + ((p >> 4) & 15));
			BlockState state = level.getBlockState(pos);
			BlockEntity be = BlockEntity.loadStatic(pos.immutable(), state, tag, level.registryAccess());
			if (be != null) {
				level.setBlockEntity(be);
			}
		}
	}

	@Override
	public int percent() {
		return totalChunks == 0 ? 100 : (int) (100L * (totalChunks - queue.size()) / totalChunks);
	}

	@Override
	public String verb() {
		return "Removing arena";
	}
}
