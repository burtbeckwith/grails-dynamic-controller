package com.burtbeckwith.grails.plugins.dynamiccontroller;

import groovy.lang.Closure;

/**
 * Retrieves a closure from a controller, mixin, database, etc.
 *
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
public interface ClosureSource {

	/**
	 * Retrieves the closure.
	 * @return the closure
	 */
	@SuppressWarnings("rawtypes")
	Closure getClosure();
}
