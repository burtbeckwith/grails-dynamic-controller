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
	protected final Method method

	/**
	 * Constructor.
	 * @param className the mixin full class name
	 * @param actionName  the action name
	 */
	ControllerMixinClosureSource(String className, String actionName, GrailsApplication application, Method method) {
		super(actionName)
		this.className = className
		this.application = application
		this.method = method
	}

	@Override
	protected Closure doGetClosure() {

		if (method != null) {
			return { -> method.invoke application.mainContext.getBean(className) }
		}

		ControllerMixinGrailsClass mixin = application.getArtefact(ControllerMixinArtefactHandler.TYPE, className)
		for (PropertyDescriptor pd : mixin.propertyDescriptors) {
			if (pd.name.equals(actionName)) {
				return mixin.newInstance()."$actionName"
			}
		}

		log.error "Closure $actionName not found for mixin $className"
		return null
	}
}
