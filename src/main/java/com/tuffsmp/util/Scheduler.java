package com.tuffsmp.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Tiny "run this in N ticks" helper. Not persisted: pending tasks vanish on restart, which is fine for visual effects. */
public final class Scheduler {
	private static final Map<Long, List<Runnable>> QUEUE = new HashMap<>();
	private static long now = 0;

	private Scheduler() {}

	public static void later(int ticks, Runnable r) {
		QUEUE.computeIfAbsent(now + Math.max(1, ticks), k -> new ArrayList<>()).add(r);
	}

	public static void tick() {
		now++;
		List<Runnable> due = QUEUE.remove(now);
		if (due == null) return;
		for (Runnable r : due) {
			try {
				r.run();
			} catch (RuntimeException e) {
				com.tuffsmp.TuffSmp.LOGGER.error("Scheduled task failed", e);
			}
		}
	}

	public static void clear() {
		QUEUE.clear();
	}
}
