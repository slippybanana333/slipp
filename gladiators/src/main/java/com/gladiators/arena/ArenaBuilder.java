package com.gladiators.arena;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

/**
 * Builds the arena one chunk at a time. For each chunk it first diffs the design against the world and writes
 * the old blocks to the backup, then places the changed blocks a few thousand per tick.
 */
public final class ArenaBuilder implements ArenaJob {
	/** Update clients, skip neighbour/shape updates and item drops: cheap and no physics cascades. */
	public static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
	/** Rough tick cost of scanning a chunk, charged against the per-tick budget. */
	private static final int SCAN_COST = 800;

	private final ServerLevel level;
	private final ArenaDesign design;
	private final ArenaBackup backup;
	private final int blocksPerTick;
	private final Deque<ChunkPos> queue = new ArrayDeque<>();
	private final int totalChunks;

	private ChunkPos currentChunk;
	private int[] currentPos = new int[0];
	private BlockState[] currentStates = new BlockState[0];
	private int currentIndex;
	private boolean hasCurrent;

	public ArenaBuilder(ServerLevel level, ArenaDesign design, ArenaBackup backup, int blocksPerTick) {
		this.level = level;
		this.design = design;
		this.backup = backup;
		this.blocksPerTick = blocksPerTick;

		int reach = design.outerReach();
		List<ChunkPos> chunks = new ArrayList<>();
		for (int cx = (design.centerX - reach) >> 4; cx <= (design.centerX + reach) >> 4; cx++) {
			for (int cz = (design.centerZ - reach) >> 4; cz <= (design.centerZ + reach) >> 4; cz++) {
				chunks.add(new ChunkPos(cx, cz));
			}
		}
		// Work outwards from the middle so the arena visibly grows.
		chunks.sort(Comparator.comparingDouble(c -> Math.hypot(c.getMiddleBlockX() - design.centerX, c.getMiddleBlockZ() - design.centerZ)));
		queue.addAll(chunks);
		this.totalChunks = chunks.size();
	}

	@Override
	public boolean tick() throws IOException {
		int budget = blocksPerTick;
		while (budget > 0) {
			if (!hasCurrent) {
				ChunkPos next = queue.poll();
				if (next == null) {
					return true;
				}
				plan(next);
				budget -= SCAN_COST;
				continue;
			}
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			int minY = level.getMinY();
			ChunkPos chunk = currentChunk;
			while (budget > 0 && currentIndex < currentPos.length) {
				int p = currentPos[currentIndex];
				pos.set(chunk.getMinBlockX() + (p & 15), minY + (p >> 8), chunk.getMinBlockZ() + ((p >> 4) & 15));
				level.setBlock(pos, currentStates[currentIndex], FLAGS);
				currentIndex++;
				budget--;
			}
			if (currentIndex >= currentPos.length) {
				hasCurrent = false;
			}
		}
		return false;
	}

	/** Diffs one chunk against the design, saves the old blocks to disk, and queues the changes. */
	private void plan(ChunkPos chunkPos) throws IOException {
		LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
		int minY = level.getMinY();
		BlockState[] column = new BlockState[design.columnHeight()];
		IntArrayList positions = new IntArrayList();
		List<BlockState> newStates = new ArrayList<>();
		List<BlockState> oldStates = new ArrayList<>();
		List<CompoundTag> blockEntities = new ArrayList<>();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int lx = 0; lx < 16; lx++) {
			for (int lz = 0; lz < 16; lz++) {
				int wx = chunkPos.getMinBlockX() + lx;
				int wz = chunkPos.getMinBlockZ() + lz;
				if (!design.column(wx - design.centerX, wz - design.centerZ, column)) {
					continue;
				}
				for (int i = 0; i < column.length; i++) {
					int y = design.lowestY() + i;
					pos.set(wx, y, wz);
					BlockState old = chunk.getBlockState(pos);
					BlockState target = column[i];
					if (old.equals(target) || (old.isAir() && target.isAir())) {
						continue;
					}
					int packed = ((y - minY) << 8) | (lz << 4) | lx;
					positions.add(packed);
					newStates.add(target);
					oldStates.add(old);
					if (old.hasBlockEntity()) {
						BlockEntity be = chunk.getBlockEntity(pos);
						if (be != null) {
							CompoundTag tag = be.saveWithFullMetadata(level.registryAccess());
							tag.putInt("GladiatorsPos", packed);
							blockEntities.add(tag);
						}
					}
				}
			}
		}
		if (positions.isEmpty()) {
			return;
		}
		backup.write(chunkPos, minY, positions, oldStates, blockEntities); // on disk before we touch the chunk
		currentChunk = chunkPos;
		currentPos = positions.toIntArray();
		currentStates = newStates.toArray(new BlockState[0]);
		currentIndex = 0;
		hasCurrent = true;
	}

	@Override
	public int percent() {
		return totalChunks == 0 ? 100 : (int) (100L * (totalChunks - queue.size()) / totalChunks);
	}

	@Override
	public String verb() {
		return "Building arena";
	}
}
