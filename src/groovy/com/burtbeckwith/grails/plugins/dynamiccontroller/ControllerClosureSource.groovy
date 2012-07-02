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

	private final String _className
	private final GrailsApplication _application

	/**
	 * Constructor.
	 * @param className  the full controller class name
	 * @param actionName  the action name
	 */
	ControllerClosureSource(String className, String actionName, GrailsApplication application) {
		super(actionName)
		_className = className
		_application = application
	}

	@Override
	protected Closure doGetClosure() {
		GrailsControllerClass controllerClass = _application.getArtefact(ControllerArtefactHandler.TYPE, _className)
		for (PropertyDescriptor pd : controllerClass.propertyDescriptors) {
			if (pd.name.equals(actionName)) {
				def controller = _application.mainContext.getBean(_className)
				return controller."$actionName"
			}
		}

		// 2.0 support; will fail for methods that take parameters
		for (Method method : controllerClass.clazz.methods) {
			if (method.name.equals(actionName)) {
				return { ->
					def controller = _application.mainContext.getBean(_className)
					method.invoke controller
				}
			}
		}

		log.error "Closure/Method $actionName not found for controller $_className"
		return null
	}
}
