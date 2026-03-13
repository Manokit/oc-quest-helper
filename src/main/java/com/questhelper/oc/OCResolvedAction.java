package com.questhelper.oc;

import com.questhelper.steps.QuestStep;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

@Value
@Builder(toBuilder = true)
public class OCResolvedAction
{
	OCResolveStatus status;
	OCActionKind kind;
	String label;
	String detail;
	QuestStep step;
	WorldPoint walkTarget;
	String option;
	String targetName;
	List<Integer> primaryIds;
	List<Integer> secondaryIds;
	Integer widgetGroupId;
	Integer widgetChildId;
	Integer widgetGrandchildId;
	String widgetText;

	public boolean isExecutable()
	{
		return status == OCResolveStatus.READY;
	}

	public static OCResolvedAction unavailable(String label, String detail)
	{
		return builder()
			.status(OCResolveStatus.UNAVAILABLE)
			.kind(OCActionKind.UNAVAILABLE)
			.label(label)
			.detail(detail)
			.primaryIds(Collections.emptyList())
			.secondaryIds(Collections.emptyList())
			.build();
	}

	public static OCResolvedAction manual(String label, String detail, QuestStep step)
	{
		return builder()
			.status(OCResolveStatus.MANUAL_ONLY)
			.kind(OCActionKind.MANUAL_ONLY)
			.label(label)
			.detail(detail)
			.step(step)
			.primaryIds(Collections.emptyList())
			.secondaryIds(Collections.emptyList())
			.build();
	}

	public static OCResolvedAction blocked(String label, String detail, QuestStep step)
	{
		return builder()
			.status(OCResolveStatus.BLOCKED)
			.kind(OCActionKind.MANUAL_ONLY)
			.label(label)
			.detail(detail)
			.step(step)
			.primaryIds(Collections.emptyList())
			.secondaryIds(Collections.emptyList())
			.build();
	}

	public static List<Integer> listOf(Integer... ids)
	{
		ArrayList<Integer> values = new ArrayList<>();
		if (ids == null)
		{
			return values;
		}

		for (Integer id : ids)
		{
			if (id != null)
			{
				values.add(id);
			}
		}
		return values;
	}
}
