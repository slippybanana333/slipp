package com.gladiators.arena;

import com.gladiators.GladiatorsMod;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Stores the blocks the build replaced, one file per chunk, written to disk BEFORE that chunk is modified.
 * That keeps {@code /gladiators arena remove} working even if the server dies in the middle of a build.
 */
public final class ArenaBackup {
	private final Path dir;

	public ArenaBackup(Path arenaDir) {
		this.dir = arenaDir.resolve("backup");
	}

	/** Everything needed to put one chunk back. {@code pos} entries are packed (y - minY) << 8 | z << 4 | x. */
	public record ChunkData(int minY, int[] pos, BlockState[] states, List<CompoundTag> blockEntities) {}

	public void write(ChunkPos chunk, int minY, IntArrayList pos, List<BlockState> oldStates, List<CompoundTag> blockEntities) throws IOException {
		Map<BlockState, Integer> paletteIndex = new LinkedHashMap<>();
		int[] idx = new int[pos.size()];
		for (int i = 0; i < idx.length; i++) {
			idx[i] = paletteIndex.computeIfAbsent(oldStates.get(i), s -> paletteIndex.size());
		}
		ListTag palette = new ListTag();
		for (BlockState state : paletteIndex.keySet()) {
			palette.add(BlockState.CODEC.encodeStart(NbtOps.INSTANCE, state).getOrThrow());
		}
		CompoundTag tag = new CompoundTag();
		tag.putInt("MinY", minY);
		tag.put("Palette", palette);
		tag.putIntArray("Pos", pos.toIntArray());
		tag.putIntArray("Idx", idx);
		ListTag bes = new ListTag();
		bes.addAll(blockEntities);
		tag.put("BlockEntities", bes);

		Files.createDirectories(dir);
		Path tmp = dir.resolve(name(chunk) + ".tmp");
		NbtIo.writeCompressed(tag, tmp);
		Files.move(tmp, file(chunk), StandardCopyOption.REPLACE_EXISTING);
	}

	public ChunkData read(ChunkPos chunk) throws IOException {
		CompoundTag tag = NbtIo.readCompressed(file(chunk), NbtAccounter.unlimitedHeap());
		List<BlockState> palette = new ArrayList<>();
		for (Tag raw : tag.getListOrEmpty("Palette")) {
			palette.add(BlockState.CODEC.parse(NbtOps.INSTANCE, raw).result().orElse(Blocks.AIR.defaultBlockState()));
		}
		int[] pos = tag.getIntArray("Pos").orElse(new int[0]);
		int[] idx = tag.getIntArray("Idx").orElse(new int[0]);
		BlockState[] states = new BlockState[pos.length];
		for (int i = 0; i < states.length; i++) {
			states[i] = palette.get(idx[i]);
		}
		List<CompoundTag> bes = new ArrayList<>();
		for (Tag raw : tag.getListOrEmpty("BlockEntities")) {
			if (raw instanceof CompoundTag c) {
				bes.add(c);
			}
		}
		return new ChunkData(tag.getIntOr("MinY", 0), pos, states, bes);
	}

	public void delete(ChunkPos chunk) {
		try {
			Files.deleteIfExists(file(chunk));
		} catch (IOException e) {
			GladiatorsMod.LOGGER.error("Could not delete backup {}", file(chunk), e);
		}
	}

	/** Chunks that still have a backup file. */
	public List<ChunkPos> chunks() {
		List<ChunkPos> out = new ArrayList<>();
		if (!Files.isDirectory(dir)) {
			return out;
		}
		try (Stream<Path> files = Files.list(dir)) {
			files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".dat")).forEach(n -> {
				String[] parts = n.substring(0, n.length() - 4).split("_");
				try {
					out.add(new ChunkPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
				} catch (RuntimeException ignored) {
					// not one of ours
				}
			});
		} catch (IOException e) {
			GladiatorsMod.LOGGER.error("Could not list {}", dir, e);
		}
		out.sort(Comparator.<ChunkPos>comparingInt(c -> c.x).thenComparingInt(c -> c.z));
		return out;
	}

	public void deleteAll() {
		if (!Files.isDirectory(dir)) {
			return;
		}
		try (Stream<Path> files = Files.list(dir)) {
			for (Path p : (Iterable<Path>) files::iterator) {
				Files.deleteIfExists(p);
			}
			Files.deleteIfExists(dir);
		} catch (IOException e) {
			GladiatorsMod.LOGGER.error("Could not clean {}", dir, e);
		}
	}

	private Path file(ChunkPos chunk) {
		return dir.resolve(name(chunk) + ".dat");
	}

	private static String name(ChunkPos chunk) {
		return chunk.x + "_" + chunk.z;
	}
}
