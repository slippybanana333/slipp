package com.gladiators.arena;

import java.io.IOException;

/** A long-running world edit that is advanced a little every server tick. */
public interface ArenaJob {
	/** Does one tick's worth of work. Returns true once everything is done. */
	boolean tick() throws IOException;

	/** Progress as 0-100, for chat updates. */
	int percent();

	String verb();
}
