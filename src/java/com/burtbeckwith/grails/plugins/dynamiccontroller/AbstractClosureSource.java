package com.burtbeckwith.grails.plugins.dynamiccontroller;

import grails.util.Environment;
import groovy.lang.Closure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for ClosureSource implementations. Caches the resolved closure
 * (except in dev environment) and delegates to the concrete impl class to
 * lazily retrieve the closure on first access in <code>doGetClosure()</code>.
 *
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
public abstract class AbstractClosureSource implements ClosureSource {

	protected final String actionName;
	@SuppressWarnings("rawtypes")
	protected Closure closure;
	protected final Logger log = LoggerFactory.getLogger(getClass());

	protected AbstractClosureSource(String actionName) {
		this.actionName = actionName;
	}

	/**
	 * {@inheritDoc}
	 * @see com.burtbeckwith.grails.plugins.dynamiccontroller.ClosureSource#getClosure()
	 */
	@SuppressWarnings("rawtypes")
	@Override
	public synchronized Closure getClosure() {
		if (!Environment.isDevelopmentMode() && closure != null) {
			return closure;
		}

		closure = doGetClosure();
		return closure;
	}

	@SuppressWarnings("rawtypes")
	protected abstract Closure doGetClosure();

	protected String getActionName() {
		return actionName;
	}

	protected Logger getLog() {
		return log;
	}
}
