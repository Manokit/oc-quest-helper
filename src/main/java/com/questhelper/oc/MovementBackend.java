package com.questhelper.oc;

import com.google.inject.ImplementedBy;
import net.runelite.api.coords.WorldPoint;

@ImplementedBy(PwebWalkBackend.class)
public interface MovementBackend
{
	void startWalk(WorldPoint worldPoint);

	void cancelWalk();

	String getStatus();
}
