package com.questhelper.oc;

import apiv3.Services;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.coords.WorldPoint;

@Singleton
public class PwebWalkBackend implements MovementBackend
{
	@Override
	public void startWalk(WorldPoint worldPoint)
	{
		if (worldPoint == null)
		{
			return;
		}

		Services.getClientThread().invokeLater(() ->
			Services.sendMessage("OC Quest Helper",
				"PWEBWALK " + worldPoint.getX() + " " + worldPoint.getY() + " " + worldPoint.getPlane(),
				ChatMessageType.GAMEMESSAGE));
	}

	@Override
	public void cancelWalk()
	{
		Services.getClientThread().invokeLater(() ->
			Services.sendMessage("OC Quest Helper", "PWEBWALK", ChatMessageType.GAMEMESSAGE));
	}

	@Override
	public String getStatus()
	{
		return "PWEBWALK";
	}
}
