package com.questhelper.oc;

import com.questhelper.requirements.Requirement;
import com.questhelper.requirements.item.ItemRequirement;
import com.questhelper.steps.DetailedQuestStep;
import com.questhelper.steps.NpcStep;
import com.questhelper.steps.ObjectStep;
import com.questhelper.steps.QuestStep;
import com.questhelper.steps.WidgetStep;
import com.questhelper.steps.choice.WidgetChoiceStep;
import com.questhelper.steps.widget.WidgetDetails;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

final class OCStepMetadata
{
	private OCStepMetadata()
	{
	}

	static List<Integer> getNpcIds(QuestStep step)
	{
		if (step instanceof NpcStep)
		{
			return ((NpcStep) step).getAllNpcIds();
		}

		return Collections.emptyList();
	}

	static Optional<String> getNpcName(QuestStep step)
	{
		if (!(step instanceof NpcStep))
		{
			return Optional.empty();
		}

		NpcStep npcStep = (NpcStep) step;
		return firstNonBlank(npcStep.getNpcName(), npcStep.getTargetNpcName(), npcStep.getNpcNameHint());
	}

	static Optional<Integer> getObjectId(ObjectStep step)
	{
		return positive(step.getObjectId());
	}

	static List<Integer> getAlternateObjectIds(ObjectStep step)
	{
		return step.getAlternateObjectIds();
	}

	static Optional<String> getObjectOption(ObjectStep step)
	{
		if (step instanceof OCObjectOptionHint)
		{
			return firstNonBlank(((OCObjectOptionHint) step).getOcObjectOption());
		}

		return Optional.empty();
	}

	static Optional<String> getNpcOption(NpcStep step)
	{
		if (step instanceof OCNpcOptionHint)
		{
			return firstNonBlank(((OCNpcOptionHint) step).getOcNpcOption());
		}

		return Optional.empty();
	}

	static Optional<Integer> getIconItemId(QuestStep step)
	{
		int iconItemId = step.getIconItemId();
		if (iconItemId > 0)
		{
			return Optional.of(iconItemId);
		}

		return positive(step.getIconItemIdHint());
	}

	static List<WidgetDetails> getWidgetDetails(WidgetStep step)
	{
		return step.getWidgetDetails();
	}

	static Optional<Integer> getWidgetChoiceGroupId(WidgetChoiceStep step)
	{
		return positive(step.getGroupId());
	}

	static Optional<Integer> getWidgetChoiceChildId(WidgetChoiceStep step)
	{
		return positive(step.getChildId());
	}

	static Optional<Integer> getWidgetChoiceChoiceId(WidgetChoiceStep step)
	{
		return step.getChoiceById() >= 0 ? Optional.of(step.getChoiceById()) : Optional.empty();
	}

	static List<ItemRequirement> getItemRequirements(QuestStep step)
	{
		if (!(step instanceof DetailedQuestStep))
		{
			return Collections.emptyList();
		}

		ArrayList<ItemRequirement> items = new ArrayList<>();
		for (Requirement requirement : ((DetailedQuestStep) step).getRequirements())
		{
			if (requirement instanceof ItemRequirement)
			{
				items.add((ItemRequirement) requirement);
			}
		}
		return items;
	}

	static boolean isExplicitUseStep(QuestStep step)
	{
		return step instanceof OCExplicitUseStep;
	}

	private static Optional<Integer> positive(int value)
	{
		return value > 0 ? Optional.of(value) : Optional.empty();
	}

	private static Optional<String> firstNonBlank(String... values)
	{
		for (String value : values)
		{
			if (value != null && !value.isBlank())
			{
				return Optional.of(value);
			}
		}

		return Optional.empty();
	}
}
