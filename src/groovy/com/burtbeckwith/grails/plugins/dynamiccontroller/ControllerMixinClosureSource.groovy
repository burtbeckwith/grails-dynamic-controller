package com.burtbeckwith.grails.plugins.dynamiccontroller

import java.beans.PropertyDescriptor
import java.lang.reflect.Method

import org.codehaus.groovy.grails.commons.GrailsApplication

import com.burtbeckwith.grails.plugins.dynamiccontroller.ControllerMixinArtefactHandler.ControllerMixinGrailsClass

/**
 * A ClosureSource that retrieves from a controller mixin.
 *
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
class ControllerMixinClosureSource extends AbstractClosureSource {

	private final String _className
	private final GrailsApplication _application

	/**
	 * Constructor.
	 * @param className the mixin full class name
	 * @param actionName  the action name
	 */
	ControllerMixinClosureSource(String className, String actionName, GrailsApplication application) {
		super(actionName)
		_className = className
		_application = application
	}

	@Override
	protected Closure doGetClosure() {
		ControllerMixinGrailsClass mixin = _application.getArtefact(
				ControllerMixinArtefactHandler.TYPE, _className)
		for (PropertyDescriptor pd : mixin.propertyDescriptors) {
			if (pd.name.equals(actionName)) {
				return mixin.newInstance()."$actionName"
			}
		}

		// 2.0 support; will fail for methods that take parameters
		for (Method method : mixin.clazz.methods) {
			if (method.name.equals(actionName)) {
				return { -> method.invoke mixin.newInstance() }
			}
		}

		log.error "Closure $actionName not found for mixin $_className"
		return null
	}
}
