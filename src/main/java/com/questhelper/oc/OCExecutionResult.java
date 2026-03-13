package com.questhelper.oc;

import lombok.Value;

@Value
public class OCExecutionResult
{
	OCResolvedAction action;
	boolean dispatched;
	String message;

	public static OCExecutionResult notExecutable(OCResolvedAction action)
	{
		return new OCExecutionResult(action, false, action.getDetail());
	}

	public static OCExecutionResult dispatched(OCResolvedAction action)
	{
		return new OCExecutionResult(action, true, action.getLabel());
	}

	public static OCExecutionResult failed(OCResolvedAction action, String message)
	{
		return new OCExecutionResult(action, false, message);
	}
}
