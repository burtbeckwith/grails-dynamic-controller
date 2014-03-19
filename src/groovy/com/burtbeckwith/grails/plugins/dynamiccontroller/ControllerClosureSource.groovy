package com.burtbeckwith.grails.plugins.dynamiccontroller

import java.lang.reflect.Method

import org.codehaus.groovy.grails.commons.GrailsApplication

/**
 * A ClosureSource that retrieves from an existing controller (in grails-app/controllers or
 * provided by a plugin).
 *
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
class ControllerClosureSource extends AbstractClosureSource {

	protected final String className
	protected final GrailsApplication application
	protected final Method method

	/**
	 * Constructor.
	 * @param className  the full controller class name
	 * @param actionName  the action name
	 */
	ControllerClosureSource(String className, String actionName, Method method, GrailsApplication application) {
		super(actionName)
		this.className = className
		this.application = application
		this.method = method
	}

	@Override
	protected Closure doGetClosure() {{ ->
		method.invoke application.mainContext.getBean(className)
	}}
}
