package com.burtbeckwith.grails.plugins.dynamiccontroller

import grails.util.Environment

import org.springframework.core.io.Resource

/**
 * A ClosureSource that retrieves from a Resource.
 *
 * The format is similar to controllers, a ConfigSlurper-format file with named closures, e.g.
 *
 * action1 = {
 * ...
 * }
 *
 * action2 = {
 * ...
 * }
 *
 * In addition the closures can be environment-specific like any config file.
 *
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
class ResourceClosureSource extends AbstractClosureSource {

	protected final Resource resource

	/**
	 * Constructor.
	 * @param resource the resource
	 * @param actionName the action name
	 */
	ResourceClosureSource(Resource resource, String actionName) {
		super(actionName)
		this.resource = resource
	}

	@Override
	protected Closure doGetClosure() {
		def config = new ConfigSlurper(Environment.current.name).parse(resource.getURL())
		config[actionName]
	}
}
