package com.burtbeckwith.grails.plugins.dynamiccontroller;

import grails.util.Environment;
import groovy.lang.Closure;

import org.apache.log4j.Logger;

/**
 * Base class for ClosureSource implementations. Caches the resolved closure
 * (except in dev environment) and delegates to the concrete impl class to
 * lazily retrieve the closure on first access in <code>doGetClosure()</code>.
 *
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
public abstract class AbstractClosureSource implements ClosureSource {

	private final String _actionName;
	@SuppressWarnings("rawtypes")
	private Closure _closure;
	private final Logger _log = Logger.getLogger(getClass());

	protected AbstractClosureSource(String actionName) {
		_actionName = actionName;
	}

	/**
	 * {@inheritDoc}
	 * @see com.burtbeckwith.grails.plugins.dynamiccontroller.ClosureSource#getClosure()
	 */
	@SuppressWarnings("rawtypes")
	@Override
	public synchronized Closure getClosure() {
		if (!Environment.isDevelopmentMode() && _closure != null) {
			return _closure;
		}

		_closure = doGetClosure();
		return _closure;
	}

	@SuppressWarnings("rawtypes")
	protected abstract Closure doGetClosure();

	protected String getActionName() {
		return _actionName;
	}

	protected Logger getLog() {
		return _log;
	}
}
