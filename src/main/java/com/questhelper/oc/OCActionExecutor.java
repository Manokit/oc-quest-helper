package com.questhelper.oc;

import apiv3.actors.NPCs;
import apiv3.grounditems.GroundItem;
import apiv3.grounditems.GroundItems;
import apiv3.tileobjects.TileObjects;
import apiv3.widgets.Inventory;
import apiv3.widgets.Widgets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.NPC;
import net.runelite.api.TileObject;
import net.runelite.api.widgets.Widget;

@Singleton
class OCActionExecutor
{
	private final MovementBackend movementBackend;

	@Inject
	OCActionExecutor(MovementBackend movementBackend)
	{
		this.movementBackend = movementBackend;
	}

	OCExecutionResult execute(OCResolvedAction action)
	{
		if (!action.isExecutable())
		{
			return OCExecutionResult.notExecutable(action);
		}

		switch (action.getKind())
		{
			case WALK:
				if (action.getWalkTarget() == null)
				{
					return OCExecutionResult.failed(action, "No walk target available");
				}
				movementBackend.startWalk(action.getWalkTarget());
				return OCExecutionResult.dispatched(action);
			case NPC_INTERACT:
				return interactNpc(action);
			case ITEM_ON_NPC:
				return useItemOnNpc(action);
			case OBJECT_INTERACT:
				return interactObject(action);
			case ITEM_ON_OBJECT:
				return useItemOnObject(action);
			case ITEM_ON_ITEM:
				return useItemOnItem(action);
			case INVENTORY_INTERACT:
			case DIG:
				return interactInventory(action);
			case DIALOG_CONTINUE:
			case DIALOG_CHOICE:
			case WIDGET_CLICK:
				return clickWidget(action);
			case GROUND_ITEM_PICKUP:
				return pickupGroundItem(action);
			default:
				return OCExecutionResult.failed(action, "Unsupported execution kind: " + action.getKind());
		}
	}

	private OCExecutionResult interactNpc(OCResolvedAction action)
	{
		Optional<NPC> npc = findNpc(action);
		if (npc.isEmpty())
		{
			return OCExecutionResult.failed(action, "Unable to find NPC target");
		}

		NPCs.invoke(npc.get(), action.getOption());
		return OCExecutionResult.dispatched(action);
	}

	private OCExecutionResult useItemOnNpc(OCResolvedAction action)
	{
		Optional<NPC> npc = findNpc(action.getSecondaryIds(), action.getTargetName());
		Optional<Widget> item = findInventoryWidget(action.getPrimaryIds());
		if (npc.isEmpty() || item.isEmpty())
		{
			return OCExecutionResult.failed(action, "Unable to find item or NPC target");
		}

		NPCs.createUseOn(item.get(), npc.get()).invoke();
		return OCExecutionResult.dispatched(action);
	}

	private OCExecutionResult interactObject(OCResolvedAction action)
	{
		Optional<TileObject> object = findObject(action);
		if (object.isEmpty())
		{
			return OCExecutionResult.failed(action, "Unable to find object target");
		}

		TileObjects.invoke(object.get(), action.getOption());
		return OCExecutionResult.dispatched(action);
	}

	private OCExecutionResult useItemOnObject(OCResolvedAction action)
	{
		Optional<TileObject> object = findObject(action.getSecondaryIds(), action);
		Optional<Widget> item = findInventoryWidget(action.getPrimaryIds());
		if (object.isEmpty() || item.isEmpty())
		{
			return OCExecutionResult.failed(action, "Unable to find item or object target");
		}

		TileObjects.useWidgetOn(item.get(), object.get());
		return OCExecutionResult.dispatched(action);
	}

	private OCExecutionResult useItemOnItem(OCResolvedAction action)
	{
		Optional<Widget> first = findInventoryWidget(action.getPrimaryIds());
		Optional<Widget> second = findInventoryWidget(action.getSecondaryIds());
		if (first.isEmpty() || second.isEmpty())
		{
			return OCExecutionResult.failed(action, "Unable to find both inventory items");
		}

		Inventory.useItemOnItem(first.get(), second.get());
		return OCExecutionResult.dispatched(action);
	}

	private OCExecutionResult interactInventory(OCResolvedAction action)
	{
		Optional<Widget> widget = findInventoryWidget(action.getPrimaryIds());
		if (widget.isEmpty())
		{
			return OCExecutionResult.failed(action, "Unable to find inventory item");
		}

		Widgets.invoke(widget.get(), action.getOption());
		return OCExecutionResult.dispatched(action);
	}

	private OCExecutionResult clickWidget(OCResolvedAction action)
	{
		Optional<Widget> widget = findWidget(action);
		if (widget.isEmpty())
		{
			return OCExecutionResult.failed(action, "Unable to find widget target");
		}

		Widgets.invoke(widget.get(), action.getOption());
		return OCExecutionResult.dispatched(action);
	}

	private OCExecutionResult pickupGroundItem(OCResolvedAction action)
	{
		Optional<GroundItem> item = findGroundItem(action);
		if (item.isEmpty())
		{
			return OCExecutionResult.failed(action, "Unable to find ground item");
		}

		GroundItems.invoke(item.get(), action.getOption());
		return OCExecutionResult.dispatched(action);
	}

	private Optional<NPC> findNpc(OCResolvedAction action)
	{
		return findNpc(action.getPrimaryIds(), action.getTargetName());
	}

	private Optional<NPC> findNpc(List<Integer> npcIds, String targetName)
	{
		if (npcIds != null && !npcIds.isEmpty())
		{
			NPC npc = NPCs.search().withIds(npcIds).closest();
			if (npc != null)
			{
				return Optional.of(npc);
			}
		}

		if (targetName != null)
		{
			NPC npc = NPCs.search().withName(targetName).closest();
			if (npc != null)
			{
				return Optional.of(npc);
			}
		}

		return Optional.empty();
	}

	private Optional<TileObject> findObject(OCResolvedAction action)
	{
		return findObject(action.getPrimaryIds(), action);
	}

	private Optional<TileObject> findObject(List<Integer> objectIds, OCResolvedAction action)
	{
		if (objectIds == null || objectIds.isEmpty())
		{
			return Optional.empty();
		}

		TileObjectQueryBuilder queryBuilder = TileObjectQueryBuilder.forAction(action, objectIds);
		return Optional.ofNullable(queryBuilder.find());
	}

	private Optional<Widget> findInventoryWidget(List<Integer> itemIds)
	{
		if (itemIds == null || itemIds.isEmpty())
		{
			return Optional.empty();
		}

		return Optional.ofNullable(Inventory.search().withIds(itemIds).first());
	}

	private Optional<Widget> findWidget(OCResolvedAction action)
	{
		if (action.getWidgetGroupId() == null || action.getWidgetChildId() == null)
		{
			return Optional.empty();
		}

		Widget widget = Widgets.getWidget(action.getWidgetGroupId(), action.getWidgetChildId());
		if (widget == null)
		{
			return Optional.empty();
		}

		if (action.getWidgetGrandchildId() != null && action.getWidgetGrandchildId() >= 0)
		{
			widget = widget.getChild(action.getWidgetGrandchildId());
		}

		return Optional.ofNullable(widget);
	}

	private Optional<GroundItem> findGroundItem(OCResolvedAction action)
	{
		if (action.getWalkTarget() != null && action.getPrimaryIds() != null && !action.getPrimaryIds().isEmpty())
		{
			GroundItem groundItem = GroundItems.search()
				.withIds(action.getPrimaryIds())
				.within(action.getWalkTarget(), 3)
				.closest(action.getWalkTarget());
			if (groundItem != null)
			{
				return Optional.of(groundItem);
			}
		}

		if (action.getPrimaryIds() != null && !action.getPrimaryIds().isEmpty())
		{
			return Optional.ofNullable(GroundItems.search().withIds(action.getPrimaryIds()).closest());
		}

		return Optional.empty();
	}

	private static class TileObjectQueryBuilder
	{
		private final OCResolvedAction action;
		private final List<Integer> objectIds;

		private TileObjectQueryBuilder(OCResolvedAction action, List<Integer> objectIds)
		{
			this.action = Objects.requireNonNull(action);
			this.objectIds = Objects.requireNonNull(objectIds);
		}

		static TileObjectQueryBuilder forAction(OCResolvedAction action, List<Integer> objectIds)
		{
			return new TileObjectQueryBuilder(action, objectIds);
		}

		TileObject find()
		{
			var query = TileObjects.search().withIds(objectIds);
			if (action.getWalkTarget() != null)
			{
				query = query.within(action.getWalkTarget(), 6);
				TileObject object = query.closest(action.getWalkTarget());
				if (object != null)
				{
					return object;
				}
			}

			return query.closest();
		}
	}
}
