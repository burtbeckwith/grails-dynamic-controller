package com.burtbeckwith.grails.plugins.dynamiccontroller

import java.beans.PropertyDescriptor
import java.lang.reflect.Method

import org.codehaus.groovy.grails.commons.ControllerArtefactHandler
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.commons.GrailsControllerClass

/**
 * A ClosureSource that retrieves from an existing controller (in grails-app/controllers or
 * provided by a plugin).
 *
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
class ControllerClosureSource extends AbstractClosureSource {

	protected final String className
	protected final GrailsApplication application

	/**
	 * Constructor.
	 * @param className  the full controller class name
	 * @param actionName  the action name
	 */
	ControllerClosureSource(String className, String actionName, GrailsApplication application) {
		super(actionName)
		this.className = className
		this.application = application
	}

	@Override
	protected Closure doGetClosure() {
		GrailsControllerClass controllerClass = application.getArtefact(ControllerArtefactHandler.TYPE, className)
		for (PropertyDescriptor pd : controllerClass.propertyDescriptors) {
			if (pd.name.equals(actionName)) {
				def controller = application.mainContext.getBean(className)
				return controller."$actionName"
			}
		}

		// 2.0 support; will fail for methods that take parameters
		for (Method method : controllerClass.clazz.methods) {
			if (method.name.equals(actionName)) {
				return { ->
					def controller = application.mainContext.getBean(className)
					method.invoke controller
				}
			}
		}

		log.error "Closure/Method $actionName not found for controller $className"
		return null
	}
}
