package com.questhelper.oc;

import com.questhelper.QuestHelperPlugin;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.annotation.Nullable;

@Singleton
public class OCQuestHelperService
{
	private static final int AUTO_ADVANCE_TIMEOUT_TICKS = 100;
	private static final OCResolvedAction DEFAULT_ACTION =
		OCResolvedAction.unavailable("Next Step: Unavailable", "Select an active quest");

	private final QuestHelperPlugin plugin;
	private final OCActionResolver resolver;
	private final OCActionExecutor executor;
	private volatile OCResolvedAction cachedAction = DEFAULT_ACTION;
	private volatile OCPanelState cachedPanelState = OCPanelState.fromAction(DEFAULT_ACTION);
	private volatile boolean autoAdvanceAfterWalk;
	private volatile int autoAdvanceDeadlineTick = -1;

	@Inject
	OCQuestHelperService(QuestHelperPlugin plugin, OCActionResolver resolver, OCActionExecutor executor)
	{
		this.plugin = plugin;
		this.resolver = resolver;
		this.executor = executor;
	}

	public OCResolvedAction getCurrentAction()
	{
		return cachedAction;
	}

	public OCPanelState getCurrentPanelState()
	{
		return cachedPanelState;
	}

	public OCPanelState getPanelState()
	{
		return getCurrentPanelState();
	}

	public void refreshPanelState(@Nullable Runnable afterRefresh)
	{
		if (plugin.getClient().isClientThread())
		{
			refreshCachedState();
			if (afterRefresh != null)
			{
				afterRefresh.run();
			}
			return;
		}

		plugin.getClientThread().invokeLater(() ->
		{
			refreshCachedState();
			if (afterRefresh != null)
			{
				afterRefresh.run();
			}
		});
	}

	public OCExecutionResult executeCurrentAction()
	{
		if (plugin.getClient().isClientThread())
		{
			OCResolvedAction action = resolver.resolveCurrentAction();
			cacheResolvedAction(action);
			if (!action.isExecutable())
			{
				clearAutoAdvance();
				return OCExecutionResult.notExecutable(action);
			}
			armAutoAdvanceIfNeeded(action);
			return executor.execute(action);
		}

		OCResolvedAction action = cachedAction;
		plugin.getClientThread().invokeLater(() ->
		{
			OCResolvedAction resolvedAction = resolver.resolveCurrentAction();
			cacheResolvedAction(resolvedAction);
			if (resolvedAction.isExecutable())
			{
				armAutoAdvanceIfNeeded(resolvedAction);
				executor.execute(resolvedAction);
			}
			else
			{
				clearAutoAdvance();
			}
		});
		return action.isExecutable() ? OCExecutionResult.dispatched(action) : OCExecutionResult.notExecutable(action);
	}

	public void onGameTick()
	{
		refreshCachedState();
		if (!autoAdvanceAfterWalk)
		{
			return;
		}

		if (plugin.getClient().getTickCount() > autoAdvanceDeadlineTick)
		{
			clearAutoAdvance();
			return;
		}

		OCResolvedAction action = cachedAction;
		if (!action.isExecutable())
		{
			if (action.getKind() != OCActionKind.WALK)
			{
				clearAutoAdvance();
			}
			return;
		}

		if (action.getKind() == OCActionKind.WALK)
		{
			return;
		}

		clearAutoAdvance();
		executor.execute(action);
		refreshCachedState();
	}

	private void refreshCachedState()
	{
		cacheResolvedAction(resolver.resolveCurrentAction());
	}

	private void armAutoAdvanceIfNeeded(OCResolvedAction action)
	{
		if (action.getKind() == OCActionKind.WALK)
		{
			autoAdvanceAfterWalk = true;
			autoAdvanceDeadlineTick = plugin.getClient().getTickCount() + AUTO_ADVANCE_TIMEOUT_TICKS;
			return;
		}

		clearAutoAdvance();
	}

	private void clearAutoAdvance()
	{
		autoAdvanceAfterWalk = false;
		autoAdvanceDeadlineTick = -1;
	}

	private void cacheResolvedAction(OCResolvedAction action)
	{
		cachedAction = action == null ? DEFAULT_ACTION : action;
		cachedPanelState = OCPanelState.fromAction(cachedAction);
	}
}
