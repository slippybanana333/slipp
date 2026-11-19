package com.gladiators.event;

import java.util.UUID;

/** Someone who was pulled into the current event. */
public final class Participant {
	public enum Role {
		FIGHTER, SPECTATOR
	}

	public final UUID id;
	public final String name;
	public Role role;
	/** True once a fighter has died or left; they are no longer in the fight. */
	public boolean eliminated;

	public Participant(UUID id, String name, Role role) {
		this.id = id;
		this.name = name;
		this.role = role;
	}

	public boolean aliveFighter() {
		return role == Role.FIGHTER && !eliminated;
	}
}
