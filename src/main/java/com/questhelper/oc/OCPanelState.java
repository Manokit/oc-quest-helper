package com.questhelper.oc;

import lombok.Value;

@Value
public class OCPanelState
{
	OCResolveStatus status;
	String label;
	String detail;
	boolean executable;

	public String getDisplayText()
	{
		if (label == null)
		{
			return null;
		}

		return label.startsWith("Next Step: ") ? label.substring("Next Step: ".length()) : label;
	}

	public String getDetailText()
	{
		return detail;
	}

	public boolean isResolved()
	{
		return executable;
	}

	public static OCPanelState fromAction(OCResolvedAction action)
	{
		return new OCPanelState(action.getStatus(), action.getLabel(), action.getDetail(), action.isExecutable());
	}
}
