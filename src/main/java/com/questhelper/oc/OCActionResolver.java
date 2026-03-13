package com.questhelper.oc;

import apiv3.Services;
import apiv3.actors.NPCs;
import apiv3.grounditems.GroundItem;
import apiv3.grounditems.GroundItems;
import apiv3.player.LocalPlayer;
import apiv3.tileobjects.TileObjects;
import apiv3.widgets.Inventory;
import apiv3.widgets.Widgets;
import com.questhelper.QuestHelperPlugin;
import com.questhelper.requirements.item.ItemRequirement;
import com.questhelper.steps.DetailedQuestStep;
import com.questhelper.steps.DigStep;
import com.questhelper.steps.NpcStep;
import com.questhelper.steps.ObjectStep;
import com.questhelper.steps.PuzzleStep;
import com.questhelper.steps.PuzzleWrapperStep;
import com.questhelper.steps.QuestStep;
import com.questhelper.steps.WidgetStep;
import com.questhelper.steps.choice.DialogChoiceStep;
import com.questhelper.steps.choice.WidgetChoiceStep;
import com.questhelper.steps.widget.WidgetDetails;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

@Singleton
class OCActionResolver
{
	private static final List<String> NPC_OPTIONS = Arrays.asList("Talk-to", "Pay-fare", "Travel", "Board", "Trade", "Buy");
	private static final List<String> OBJECT_OPTIONS = Arrays.asList("Open", "Search", "Climb-up", "Climb-down", "Climb", "Enter", "Exit", "Cross", "Operate", "Pull", "Push", "Turn", "Read", "Fill", "Board");
	private static final List<String> SAFE_INVENTORY_OPTIONS = Arrays.asList("Read", "Open");

	private final QuestHelperPlugin plugin;

	@Inject
	OCActionResolver(QuestHelperPlugin plugin)
	{
		this.plugin = plugin;
	}

	OCResolvedAction resolveCurrentAction()
	{
		if (!Services.isLoggedIn() || plugin.getClient().getGameState() != GameState.LOGGED_IN)
		{
			return OCResolvedAction.unavailable("Next Step: Unavailable", "You must be logged in");
		}

		QuestStep step = getActiveStep();
		if (step == null)
		{
			return OCResolvedAction.unavailable("Next Step: Unavailable", "No active quest step");
		}

		if (step instanceof PuzzleStep || step instanceof PuzzleWrapperStep)
		{
			return OCResolvedAction.manual("Next Step: Manual", "Puzzle steps are manual in V1", step);
		}

		Optional<OCResolvedAction> resolved = resolveDialogChoice(step);
		if (resolved.isPresent())
		{
			return resolved.get();
		}

		resolved = resolveDialogContinue(step);
		if (resolved.isPresent())
		{
			return resolved.get();
		}

		resolved = resolveWidgetChoice(step);
		if (resolved.isPresent())
		{
			return resolved.get();
		}

		if (step instanceof DigStep)
		{
			return resolveDigStep((DigStep) step);
		}

		resolved = resolveInventoryItemOnItem(step);
		if (resolved.isPresent())
		{
			return resolved.get();
		}

		resolved = resolveInventoryInteract(step);
		if (resolved.isPresent())
		{
			return resolved.get();
		}

		if (step instanceof WidgetStep)
		{
			resolved = resolveWidgetStep((WidgetStep) step);
			if (resolved.isPresent())
			{
				return resolved.get();
			}
		}

		if (step instanceof NpcStep)
		{
			resolved = resolveNpcStep((NpcStep) step);
			if (resolved.isPresent())
			{
				return resolved.get();
			}
		}

		if (step instanceof ObjectStep)
		{
			resolved = resolveObjectStep((ObjectStep) step);
			if (resolved.isPresent())
			{
				return resolved.get();
			}
		}

		resolved = resolveGroundItemPickup(step);
		if (resolved.isPresent())
		{
			return resolved.get();
		}

		resolved = resolveWalk(step);
		if (resolved.isPresent())
		{
			return resolved.get();
		}

		return OCResolvedAction.manual(buildLabel(step, "Manual"), "Step is unsupported or ambiguous in V1", step);
	}

	private QuestStep getActiveStep()
	{
		var selectedQuest = plugin.getSelectedQuest();
		if (selectedQuest == null || selectedQuest.getCurrentStep() == null)
		{
			return null;
		}

		return selectedQuest.getCurrentStep().getActiveStep();
	}

	private Optional<OCResolvedAction> resolveDialogChoice(QuestStep step)
	{
		List<DialogChoiceStep> choices = step.getChoices().getChoices();
		if (choices.isEmpty())
		{
			return Optional.empty();
		}

		Widget dialogContainer = plugin.getClient().getWidget(219, 1);
		if (dialogContainer == null)
		{
			return Optional.empty();
		}

		List<Widget> widgets = collectChoiceWidgets(dialogContainer);
		for (DialogChoiceStep choice : choices)
		{
			if (choice.getChoice() == null)
			{
				continue;
			}

			Widget widget = widgets.stream()
				.filter(Objects::nonNull)
				.filter(w -> matchesWidgetText(w, choice.getChoice()))
				.findFirst()
				.orElse(null);
			if (widget == null)
			{
				continue;
			}

			String option = firstWidgetOption(widget).orElse("Continue");
			return Optional.of(OCResolvedAction.builder()
				.status(OCResolveStatus.READY)
				.kind(OCActionKind.DIALOG_CHOICE)
				.label("Next Step: Choose dialog")
				.detail(choice.getChoice())
				.step(step)
				.option(option)
				.widgetGroupId(Widgets.TO_GROUP(widget.getId()))
				.widgetChildId(Widgets.TO_CHILD(widget))
				.build());
		}

		boolean hasUnsupportedChoice = choices.stream().anyMatch(choice -> choice.getChoice() == null);
		if (hasUnsupportedChoice && !widgets.isEmpty())
		{
			return Optional.of(OCResolvedAction.manual("Next Step: Manual", "Dialog choice needs metadata not exposed yet", step));
		}

		return Optional.empty();
	}

	private Optional<OCResolvedAction> resolveDialogContinue(QuestStep step)
	{
		List<Widget> continueWidgets = Widgets.search().visible().withOption("Continue").list();
		if (continueWidgets.size() != 1)
		{
			continueWidgets = Widgets.search().visible().withText("Click here to continue").list();
		}

		if (continueWidgets.size() != 1)
		{
			return Optional.empty();
		}

		Widget widget = continueWidgets.get(0);
		return Optional.of(OCResolvedAction.builder()
			.status(OCResolveStatus.READY)
			.kind(OCActionKind.DIALOG_CONTINUE)
			.label("Next Step: Continue")
			.detail(normalizeText(widget.getText()))
			.step(step)
			.option(firstWidgetOption(widget).orElse("Continue"))
			.widgetGroupId(Widgets.TO_GROUP(widget.getId()))
			.widgetChildId(Widgets.TO_CHILD(widget))
			.build());
	}

	private Optional<OCResolvedAction> resolveWidgetChoice(QuestStep step)
	{
		List<WidgetChoiceStep> choices = step.getWidgetChoices().getChoices();
		if (choices.isEmpty())
		{
			return Optional.empty();
		}

		for (WidgetChoiceStep choice : choices)
		{
			Optional<Integer> groupId = OCStepMetadata.getWidgetChoiceGroupId(choice);
			Optional<Integer> childId = OCStepMetadata.getWidgetChoiceChildId(choice);
			if (groupId.isEmpty() || childId.isEmpty())
			{
				continue;
			}

			Widget parent = plugin.getClient().getWidget(groupId.get(), childId.get());
			if (parent == null)
			{
				continue;
			}

			List<Widget> widgets = collectChoiceWidgets(parent);
			Widget matched = null;
			if (choice.getChoice() != null)
			{
				matched = widgets.stream().filter(widget -> matchesWidgetText(widget, choice.getChoice())).findFirst().orElse(null);
			}
			else if (OCStepMetadata.getWidgetChoiceChoiceId(choice).isPresent())
			{
				int index = OCStepMetadata.getWidgetChoiceChoiceId(choice).get();
				if (index >= 0 && index < widgets.size())
				{
					matched = widgets.get(index);
				}
			}

			if (matched == null)
			{
				continue;
			}

			return Optional.of(OCResolvedAction.builder()
				.status(OCResolveStatus.READY)
				.kind(OCActionKind.WIDGET_CLICK)
				.label("Next Step: Click widget")
				.detail(normalizeText(matched.getText()))
				.step(step)
				.option(firstWidgetOption(matched).orElse("Continue"))
				.widgetGroupId(Widgets.TO_GROUP(matched.getId()))
				.widgetChildId(Widgets.TO_CHILD(matched))
				.build());
		}

		return Optional.of(OCResolvedAction.manual("Next Step: Manual", "Widget choice needs extra metadata", step));
	}

	private OCResolvedAction resolveDigStep(DigStep step)
	{
		WorldPoint target = step.getDefinedPoint() != null ? step.getDefinedPoint().getWorldPoint() : null;
		if (target != null && !isAtOrNear(target))
		{
			return walkAction(step, target, "Next Step: Walk to dig spot");
		}

		Widget spade = Inventory.search().withId(net.runelite.api.gameval.ItemID.SPADE).first();
		if (spade == null)
		{
			return OCResolvedAction.blocked("Next Step: Missing required item", "A spade is required", step);
		}

		return OCResolvedAction.builder()
			.status(OCResolveStatus.READY)
			.kind(OCActionKind.DIG)
			.label("Next Step: Dig here")
			.detail("Use a spade")
			.step(step)
			.option("Dig")
			.primaryIds(Collections.singletonList(net.runelite.api.gameval.ItemID.SPADE))
			.build();
	}

	private Optional<OCResolvedAction> resolveInventoryItemOnItem(QuestStep step)
	{
		if (!isExplicitUseAction(step))
		{
			return Optional.empty();
		}

		List<ItemRequirement> highlighted = getHighlightedInventoryRequirements(step);
		Set<Integer> uniqueIds = highlighted.stream()
			.flatMap(req -> req.getAllIds().stream())
			.collect(Collectors.toCollection(LinkedHashSet::new));
		if (uniqueIds.size() != 2)
		{
			return Optional.empty();
		}

		List<Integer> ids = new ArrayList<>(uniqueIds);
		Widget first = Inventory.search().withIds(Collections.singletonList(ids.get(0))).first();
		Widget second = Inventory.search().withIds(Collections.singletonList(ids.get(1))).first();
		if (first == null || second == null)
		{
			return Optional.empty();
		}

		return Optional.of(OCResolvedAction.builder()
			.status(OCResolveStatus.READY)
			.kind(OCActionKind.ITEM_ON_ITEM)
			.label("Next Step: Use item on item")
			.detail("Combine highlighted quest items")
			.step(step)
			.primaryIds(Collections.singletonList(ids.get(0)))
			.secondaryIds(Collections.singletonList(ids.get(1)))
			.build());
	}

	private Optional<OCResolvedAction> resolveInventoryInteract(QuestStep step)
	{
		List<ItemRequirement> highlighted = getHighlightedInventoryRequirements(step);
		if (highlighted.size() != 1)
		{
			return Optional.empty();
		}

		Widget widget = Inventory.search().withIds(highlighted.get(0).getAllIds()).first();
		if (widget == null)
		{
			return Optional.empty();
		}

		List<String> options = widgetOptions(widget);
		List<String> matching = SAFE_INVENTORY_OPTIONS.stream()
			.filter(options::contains)
			.collect(Collectors.toList());
		if (matching.size() != 1)
		{
			return Optional.empty();
		}

		String itemName = normalizeText(widget.getName());
		return Optional.of(OCResolvedAction.builder()
			.status(OCResolveStatus.READY)
			.kind(OCActionKind.INVENTORY_INTERACT)
			.label("Next Step: " + matching.get(0) + " " + itemName)
			.detail(matching.get(0) + " the highlighted item")
			.step(step)
			.option(matching.get(0))
			.primaryIds(highlighted.get(0).getAllIds())
			.targetName(itemName)
			.build());
	}

	private Optional<OCResolvedAction> resolveWidgetStep(WidgetStep step)
	{
		List<WidgetDetails> details = OCStepMetadata.getWidgetDetails(step);
		if (details.isEmpty())
		{
			return Optional.empty();
		}

		for (WidgetDetails detail : details)
		{
			Widget widget = Widgets.getWidget(detail.getGroupID(), detail.getChildID());
			if (widget == null || widget.isHidden())
			{
				continue;
			}
			if (detail.getChildChildID() != -1)
			{
				widget = widget.getChild(detail.getChildChildID());
			}
			if (widget == null || widget.isHidden())
			{
				continue;
			}

			Optional<String> option = firstWidgetOption(widget);
			if (option.isEmpty())
			{
				continue;
			}

			return Optional.of(OCResolvedAction.builder()
				.status(OCResolveStatus.READY)
				.kind(OCActionKind.WIDGET_CLICK)
				.label("Next Step: Click widget")
				.detail(normalizeText(widget.getText()))
				.step(step)
				.option(option.get())
				.widgetGroupId(detail.getGroupID())
				.widgetChildId(detail.getChildID())
				.widgetGrandchildId(detail.getChildChildID())
				.build());
		}

		return Optional.of(OCResolvedAction.manual("Next Step: Manual", "Widget target is not clickable right now", step));
	}

	private Optional<OCResolvedAction> resolveNpcStep(NpcStep step)
	{
		NPC npc = findNpc(step);
		if (npc == null)
		{
			return resolveWalk(step);
		}
		if (shouldWalkBeforeInteracting(step))
		{
			return resolveWalk(step);
		}

		Optional<ItemRequirement> itemRequirement = getSingleHighlightedInventoryRequirement(step);
		if (itemRequirement.isPresent() && isExplicitUseAction(step))
		{
			Widget widget = Inventory.search().withIds(itemRequirement.get().getAllIds()).first();
			if (widget == null)
			{
				return Optional.of(OCResolvedAction.blocked("Next Step: Missing required item", "Required item is not available", step));
			}

			return Optional.of(OCResolvedAction.builder()
				.status(OCResolveStatus.READY)
				.kind(OCActionKind.ITEM_ON_NPC)
				.label("Next Step: Use item -> " + normalizeText(npc.getName()))
				.detail("Use the highlighted item on the NPC")
				.step(step)
				.primaryIds(itemRequirement.get().getAllIds())
				.secondaryIds(OCStepMetadata.getNpcIds(step))
				.targetName(normalizeText(npc.getName()))
				.build());
		}

		String option = OCStepMetadata.getNpcOption(step).orElseGet(() -> preferredOption(step, NPCs.getOptions(npc), NPC_OPTIONS));
		if (option == null)
		{
			return Optional.of(OCResolvedAction.manual(buildLabel(step, "Manual"), "Unable to determine a safe NPC option", step));
		}

		return Optional.of(OCResolvedAction.builder()
			.status(OCResolveStatus.READY)
			.kind(OCActionKind.NPC_INTERACT)
			.label("Next Step: " + option + " " + normalizeText(npc.getName()))
			.detail(option + " the target NPC")
			.step(step)
			.option(option)
			.primaryIds(OCStepMetadata.getNpcIds(step))
			.targetName(normalizeText(npc.getName()))
			.build());
	}

	private Optional<OCResolvedAction> resolveObjectStep(ObjectStep step)
	{
		Optional<Integer> objectId = OCStepMetadata.getObjectId(step);
		if (objectId.isEmpty())
		{
			return Optional.of(OCResolvedAction.manual(buildLabel(step, "Manual"), "Object metadata is not exposed yet", step));
		}

		TileObject object = findObject(step, objectId.get());
		if (object == null)
		{
			return resolveWalk(step);
		}
		if (shouldWalkBeforeInteracting(step))
		{
			return resolveWalk(step);
		}

		List<Integer> ids = new ArrayList<>();
		ids.add(objectId.get());
		ids.addAll(OCStepMetadata.getAlternateObjectIds(step));

		Optional<ItemRequirement> itemRequirement = getSingleHighlightedInventoryRequirement(step);
		if (itemRequirement.isPresent() && isExplicitUseAction(step))
		{
			Widget widget = Inventory.search().withIds(itemRequirement.get().getAllIds()).first();
			if (widget == null)
			{
				return Optional.of(OCResolvedAction.blocked("Next Step: Missing required item", "Required item is not available", step));
			}

			return Optional.of(OCResolvedAction.builder()
				.status(OCResolveStatus.READY)
				.kind(OCActionKind.ITEM_ON_OBJECT)
				.label("Next Step: Use item -> " + normalizeText(TileObjects.getName(object)))
				.detail("Use the highlighted item on the target object")
				.step(step)
				.primaryIds(itemRequirement.get().getAllIds())
				.secondaryIds(ids)
				.targetName(normalizeText(TileObjects.getName(object)))
				.walkTarget(step.getDefinedPoint() != null ? step.getDefinedPoint().getWorldPoint() : null)
				.build());
		}

		String option = OCStepMetadata.getObjectOption(step).orElseGet(() -> preferredOption(step, TileObjects.getOptions(object), OBJECT_OPTIONS));
		if (option == null)
		{
			return Optional.of(OCResolvedAction.manual(buildLabel(step, "Manual"), "Unable to determine a safe object option", step));
		}

		return Optional.of(OCResolvedAction.builder()
			.status(OCResolveStatus.READY)
			.kind(OCActionKind.OBJECT_INTERACT)
			.label("Next Step: " + option + " " + normalizeText(TileObjects.getName(object)))
			.detail(option + " the target object")
			.step(step)
			.option(option)
			.primaryIds(ids)
			.targetName(normalizeText(TileObjects.getName(object)))
			.walkTarget(step.getDefinedPoint() != null ? step.getDefinedPoint().getWorldPoint() : null)
			.build());
	}

	private Optional<OCResolvedAction> resolveGroundItemPickup(QuestStep step)
	{
		if (!(step instanceof DetailedQuestStep))
		{
			return Optional.empty();
		}

		DetailedQuestStep detailedStep = (DetailedQuestStep) step;
		if (detailedStep.getDefinedPoint() == null)
		{
			return Optional.empty();
		}

		List<ItemRequirement> candidates = OCStepMetadata.getItemRequirements(step).stream()
			.filter(req -> !req.mustBeEquipped())
			.filter(req -> !req.check(plugin.getClient()))
			.collect(Collectors.toList());
		if (candidates.size() != 1)
		{
			return Optional.empty();
		}

		WorldPoint point = detailedStep.getDefinedPoint().getWorldPoint();
		GroundItem groundItem = GroundItems.search()
			.withIds(candidates.get(0).getAllIds())
			.within(point, 3)
			.closest(point);
		if (groundItem == null)
		{
			return Optional.empty();
		}
		if (shouldWalkBeforeInteracting(detailedStep))
		{
			return Optional.of(walkAction(step, point, "Next Step: Walk to ground item"));
		}

		return Optional.of(OCResolvedAction.builder()
			.status(OCResolveStatus.READY)
			.kind(OCActionKind.GROUND_ITEM_PICKUP)
			.label("Next Step: Take " + normalizeText(groundItem.getName()))
			.detail("Pick up the required ground item")
			.step(step)
			.option("Take")
			.primaryIds(candidates.get(0).getAllIds())
			.targetName(normalizeText(groundItem.getName()))
			.walkTarget(point)
			.build());
	}

	private Optional<OCResolvedAction> resolveWalk(QuestStep step)
	{
		if (!(step instanceof DetailedQuestStep))
		{
			return Optional.empty();
		}

		DetailedQuestStep detailedStep = (DetailedQuestStep) step;
		WorldPoint target = null;
		if (detailedStep.getDefinedPoint() != null)
		{
			target = detailedStep.getDefinedPoint().getWorldPoint();
		}
		if (target == null)
		{
			target = firstWorldPoint(detailedStep.getWorldLinePoints());
		}
		if (target == null)
		{
			target = firstWorldPoint(detailedStep.getLinePoints());
		}
		if (target == null)
		{
			return Optional.empty();
		}
		if (isAtOrNear(target))
		{
			return Optional.empty();
		}

		return Optional.of(walkAction(step, target, "Next Step: Walk to quest target"));
	}

	private OCResolvedAction walkAction(QuestStep step, WorldPoint target, String label)
	{
		return OCResolvedAction.builder()
			.status(OCResolveStatus.READY)
			.kind(OCActionKind.WALK)
			.label(label)
			.detail("PWEBWALK " + target.getX() + " " + target.getY() + " " + target.getPlane())
			.step(step)
			.walkTarget(target)
			.build();
	}

	private NPC findNpc(NpcStep step)
	{
		List<Integer> ids = OCStepMetadata.getNpcIds(step);
		if (!ids.isEmpty())
		{
			if (step.getDefinedPoint() != null && step.getDefinedPoint().getWorldPoint() != null)
			{
				NPC npc = NPCs.search()
					.withIds(ids)
					.within(step.getDefinedPoint().getWorldPoint(), 12)
					.closest(step.getDefinedPoint().getWorldPoint());
				if (npc != null)
				{
					return npc;
				}
			}
			NPC npc = NPCs.search().withIds(ids).closest();
			if (npc != null)
			{
				return npc;
			}
		}

		Optional<String> npcName = OCStepMetadata.getNpcName(step);
		if (npcName.isEmpty())
		{
			return null;
		}

		if (step.getDefinedPoint() != null && step.getDefinedPoint().getWorldPoint() != null)
		{
			NPC npc = NPCs.search()
				.withName(npcName.get())
				.within(step.getDefinedPoint().getWorldPoint(), 12)
				.closest(step.getDefinedPoint().getWorldPoint());
			if (npc != null)
			{
				return npc;
			}
		}
		return NPCs.search().withName(npcName.get()).closest();
	}

	private TileObject findObject(ObjectStep step, int objectId)
	{
		List<Integer> ids = new ArrayList<>();
		ids.add(objectId);
		ids.addAll(OCStepMetadata.getAlternateObjectIds(step));

		var query = TileObjects.search().withIds(ids);
		if (step.getDefinedPoint() != null && step.getDefinedPoint().getWorldPoint() != null)
		{
			query = query.within(step.getDefinedPoint().getWorldPoint(), 6);
			TileObject object = query.closest(step.getDefinedPoint().getWorldPoint());
			if (object != null)
			{
				return object;
			}
		}
		return query.closest();
	}

	private List<ItemRequirement> getHighlightedInventoryRequirements(QuestStep step)
	{
		return OCStepMetadata.getItemRequirements(step).stream()
			.filter(req -> !req.mustBeEquipped())
			.filter(req -> req.shouldHighlightInInventory(plugin.getClient()))
			.sorted(Comparator.comparing(ItemRequirement::getId))
			.collect(Collectors.toList());
	}

	private Optional<ItemRequirement> getSingleHighlightedInventoryRequirement(QuestStep step)
	{
		List<ItemRequirement> highlighted = getHighlightedInventoryRequirements(step);
		if (highlighted.size() == 1)
		{
			return Optional.of(highlighted.get(0));
		}

		Optional<Integer> iconItemId = OCStepMetadata.getIconItemId(step);
		if (highlighted.isEmpty() && iconItemId.isPresent() && Inventory.contains(iconItemId.get()))
		{
			return Optional.of(new ItemRequirement("Highlighted item", iconItemId.get()));
		}

		return Optional.empty();
	}

	private List<Widget> collectChoiceWidgets(Widget parent)
	{
		ArrayList<Widget> widgets = new ArrayList<>();
		if (parent.getChildren() != null)
		{
			widgets.addAll(Arrays.asList(parent.getChildren()));
		}
		if (widgets.isEmpty() && parent.getNestedChildren() != null)
		{
			widgets.addAll(Arrays.asList(parent.getNestedChildren()));
		}
		return widgets.stream().filter(Objects::nonNull).collect(Collectors.toList());
	}

	private boolean matchesWidgetText(Widget widget, String expected)
	{
		String text = normalizeText(widget.getText());
		if (text.startsWith("[") && text.contains("] "))
		{
			text = text.substring(text.indexOf("] ") + 2);
		}
		return text.equals(expected);
	}

	private Optional<String> firstWidgetOption(Widget widget)
	{
		return widgetOptions(widget).stream().findFirst();
	}

	private List<String> widgetOptions(Widget widget)
	{
		if (widget == null || widget.getActions() == null)
		{
			return Collections.emptyList();
		}

		return Arrays.stream(widget.getActions())
			.filter(Objects::nonNull)
			.collect(Collectors.toList());
	}

	private String preferredOption(QuestStep step, String[] rawOptions, List<String> preferredOptions)
	{
		if (rawOptions == null)
		{
			return null;
		}

		List<String> options = Arrays.stream(rawOptions)
			.filter(Objects::nonNull)
			.filter(preferredOptions::contains)
			.distinct()
			.collect(Collectors.toList());
		if (options.isEmpty())
		{
			return null;
		}

		String stepText = allStepText(step);
		String bestOption = null;
		int bestScore = 0;
		boolean tied = false;
		for (String option : options)
		{
			int score = optionScore(stepText, option);
			if (score > bestScore)
			{
				bestOption = option;
				bestScore = score;
				tied = false;
			}
			else if (score > 0 && score == bestScore)
			{
				tied = true;
			}
		}

		if (bestScore > 0 && !tied)
		{
			return bestOption;
		}

		return options.size() == 1 ? options.get(0) : null;
	}

	private boolean isAtOrNear(WorldPoint target)
	{
		WorldPoint current = LocalPlayer.getWorldPoint();
		return current != null && current.distanceTo(target) <= 1 && current.getPlane() == target.getPlane();
	}

	private boolean shouldWalkBeforeInteracting(DetailedQuestStep step)
	{
		WorldPoint target = step.getWorldPoint();
		if (target == null)
		{
			return false;
		}

		WorldPoint current = LocalPlayer.getWorldPoint();
		if (current == null || current.getPlane() != target.getPlane())
		{
			return true;
		}

		return current.distanceTo(target) > 2;
	}

	private WorldPoint firstWorldPoint(List<WorldPoint> points)
	{
		if (points == null || points.isEmpty())
		{
			return null;
		}

		return points.get(0);
	}

	private String buildLabel(QuestStep step, String fallback)
	{
		String text = firstStepText(step);
		return text.isEmpty() ? "Next Step: " + fallback : "Next Step: " + text;
	}

	private String firstStepText(QuestStep step)
	{
		if (step.getText() == null || step.getText().isEmpty())
		{
			return "";
		}
		return normalizeText(step.getText().get(0));
	}

	private String normalizeText(String text)
	{
		if (text == null)
		{
			return "";
		}
		return Text.removeTags(text).replace('\n', ' ').trim();
	}

	private boolean isExplicitUseAction(QuestStep step)
	{
		if (OCStepMetadata.isExplicitUseStep(step))
		{
			return true;
		}

		String text = allStepText(step);
		return text.startsWith("use ") && text.contains(" on ");
	}

	private String allStepText(QuestStep step)
	{
		if (step.getText() == null || step.getText().isEmpty())
		{
			return "";
		}

		return step.getText().stream()
			.map(this::normalizeText)
			.collect(Collectors.joining(" "))
			.toLowerCase(Locale.ENGLISH);
	}

	private int optionScore(String stepText, String option)
	{
		String normalizedOption = option.toLowerCase(Locale.ENGLISH).replace('-', ' ');
		if (stepText.equals(normalizedOption) || stepText.startsWith(normalizedOption + " "))
		{
			return 5;
		}

		if (stepText.contains(normalizedOption))
		{
			return 4;
		}

		switch (option)
		{
			case "Talk-to":
				return startsWithAny(stepText, "talk to ", "speak to ") ? 4 : 0;
			case "Pay-fare":
				return stepText.startsWith("pay fare") || stepText.startsWith("pay the fare") ? 4 : 0;
			case "Climb-up":
				return stepText.startsWith("climb up") ? 4 : 0;
			case "Climb-down":
				return stepText.startsWith("climb down") ? 4 : 0;
			default:
				return 0;
		}
	}

	private boolean startsWithAny(String value, String... prefixes)
	{
		for (String prefix : prefixes)
		{
			if (value.startsWith(prefix))
			{
				return true;
			}
		}

		return false;
	}
}
