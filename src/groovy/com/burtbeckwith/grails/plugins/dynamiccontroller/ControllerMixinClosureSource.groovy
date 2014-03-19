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

	protected final String className
	protected final GrailsApplication application

	/**
	 * Constructor.
	 * @param className the mixin full class name
	 * @param actionName  the action name
	 */
	ControllerMixinClosureSource(String className, String actionName, GrailsApplication application) {
		super(actionName)
		this.className = className
		this.application = application
	}

	@Override
	protected Closure doGetClosure() {
		ControllerMixinGrailsClass mixin = application.getArtefact(
				ControllerMixinArtefactHandler.TYPE, className)
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

		log.error "Closure $actionName not found for mixin $className"
		return null
	}
}
