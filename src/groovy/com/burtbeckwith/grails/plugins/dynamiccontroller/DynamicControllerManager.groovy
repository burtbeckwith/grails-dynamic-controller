package com.burtbeckwith.grails.plugins.dynamiccontroller

import grails.util.Environment

import java.beans.PropertyDescriptor
import java.lang.reflect.Method
import java.lang.reflect.Modifier

import org.apache.log4j.Logger
import org.codehaus.groovy.grails.commons.ControllerArtefactHandler
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.commons.GrailsClass
import org.codehaus.groovy.grails.commons.GrailsControllerClass
import org.codehaus.groovy.grails.plugins.metadata.GrailsPlugin
import org.springframework.beans.factory.support.AbstractBeanDefinition
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.core.io.Resource
import org.springframework.util.ClassUtils

import com.burtbeckwith.grails.plugins.dynamiccontroller.ControllerMixinArtefactHandler.ControllerMixinGrailsClass

/**
 * Handles all of the logic for managing controller mixins and dynamic actions.
 *
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
class DynamicControllerManager {

	private static Map<String, Map<String, ?>> _closures = [:]
	private static final LOG = Logger.getLogger(this)

	/**
	 * Register controller closures.
	 * @param controllerClassName  the class name of the destination controller
	 * @param controllerClassClosures  the action names and closures
	 * @param plugin  optional name and version if registered from a plugin
	 */
	static void registerClosures(String controllerClassName, Map<String, ?> controllerClassClosures,
			plugin, GrailsApplication application) {

		GrailsControllerClass controllerClass = lookupControllerClass(controllerClassName, application)
		if (!controllerClass) {
			LOG.info "Can't find controller with name $controllerClassName, creating dynamic controller"
			controllerClass = createDynamicController(controllerClassName, plugin, application)
		}

		// register the urls /controllername/actionname and /controllername/actionname/**
		for (action in controllerClassClosures.keySet()) {
			controllerClass.registerMapping action
			LOG.info "registered action $action for $controllerClassName"
		}

		getClassClosures(controllerClassName).putAll controllerClassClosures

		controllerClass.clazz.metaClass.getProperty = { String name ->
			if ('controllerName' == name) {
				// fake out as the containing controller
				return lookupControllerClass(controllerClassName, application).logicalPropertyName
			}

			lookupProperty name, controllerClassName, delegate, application
		}

		controllerClass.clazz.metaClass.methodMissing = { String name, args ->
			// fake out as the containing controller for chain or redirect
			// where action is specified but controller isn't
			if (['chain', 'redirect'].contains(name) && args.length == 1 && args[0] instanceof Map &&
					args[0].action && !args[0].controller) {
				args[0].controller = lookupControllerClass(controllerClassName, application).logicalPropertyName
			}

			def controller = application.mainContext.getBean(DynamicDelegateController.name)
			if (!controller.respondsTo(name)) {
				throw new MissingMethodException(name, delegate.getClass(), args)
			}

			args ? controller."$name"(*args) : controller."$name"()
		}
	}

	/**
	 * Register controller closures from a Resource.
	 * @param controllerClassName  the class name of the destination controller
	 * @param resource  the resource
	 * @param plugin  optional name and version if registered from a plugin
	 */
	static void registerClosures(String controllerClassName, Resource resource, plugin, GrailsApplication application) {
		def config = new ConfigSlurper(Environment.current.name).parse(resource.getURL())

		Map<String, ?> closureSources = [:]
		for (actionName in config.keySet()) {
			closureSources[actionName] = new ResourceClosureSource(resource, actionName)
		}
		registerClosures controllerClassName, closureSources, plugin, application
	}

	private static lookupProperty(String name, String controllerClassName, controller, GrailsApplication application) {
		// look first for a closure with that name, assuming it's an action; not cached here
		// like you would with standard propertyMissing since AbstractClosureSource manages that
		def closure = getClassClosures(controllerClassName)[name]
		if (closure) {
			if (closure instanceof ClosureSource) {
				closure = closure.getClosure()
			}
			else {
				closure = closure.clone()
			}

			// set the closure's delegate to the controller it was mixed into
			closure.delegate = controller
			return closure
		}

		// fallback to an existing property or metaclass property
		def mp = controller.metaClass.getMetaProperty(name)
		if (mp) {
			return mp.getProperty(controller)
		}

		// try the metaclass methods added to all controllers
		application.mainContext.getBean(DynamicDelegateController.name)."$name"
	}

	/**
	 * Mix in closures from a controller.
	 * @param sourceControllerClassName  the source class name
	 * @param destControllerClazz  the destination controller class
	 */
	static void mixin(String sourceControllerClassName, String destControllerClassName, GrailsApplication application) {
		def sourceControllerClass = application.getArtefact('Controller', sourceControllerClassName)
		if (!sourceControllerClass) {
			LOG.error "Controller $sourceControllerClassName not found, cannot mix in"
			return
		}

		mixin sourceControllerClass, destControllerClassName, application, { controllerName, actionName ->
			new ControllerClosureSource(controllerName, actionName, application)
		}
	}

	/**
	 * Mix in closures from a ControllerMixinGrailsClass.
	 * @param cc  the source
	 */
	static void mixin(ControllerMixinGrailsClass cc, GrailsApplication application) {
		List destControllerNames

		// see if there's a static 'controller' property first
		try { destControllerNames = [cc.clazz.controller] }
		catch (ignored) {}

		def configName = application.config.grails.plugins.dynamicController.mixins[cc.clazz.name]
		if (configName instanceof CharSequence) {
			destControllerNames = [configName]
		}
		else if (configName instanceof List) {
			destControllerNames = configName
		}

		if (!destControllerNames) {
			LOG.error "No destination controller specified in the 'controller' property or " +
				"'grails.plugins.dynamicController.mixins' config attribute for ${cc.name}ControllerMixin, ignoring"
			return
		}

		for (destControllerName in destControllerNames) {
			mixin cc, destControllerName.toString(), application, { controllerName, actionName ->
				new ControllerMixinClosureSource(controllerName, actionName, application)
			}
		}
	}

	/**
	 * Mix in closures from a controller or ControllerMixinGrailsClass.
	 *
	 * @param sourceControllerClass  the source
	 * @param destControllerClassName  the destination controller class name
	 * @param createSource  a closure that creates a ClosureSource
	 */
	static void mixin(GrailsClass sourceControllerClass, String destControllerClassName,
	                  GrailsApplication application, createSource) {

		Map<String, ?> controllerClassClosures = [:]

		// loop through all properties to find 'def foo = {...' or 'Closure foo = {...'
		def instance = sourceControllerClass.newInstance()
		for (PropertyDescriptor propertyDescriptor : sourceControllerClass.propertyDescriptors) {
			Method readMethod = propertyDescriptor.readMethod
			if (readMethod && !Modifier.isStatic(readMethod.modifiers)) {
				Class<?> propertyType = propertyDescriptor.propertyType
				if (propertyType == Object || propertyType == Closure) {
					String closureName = propertyDescriptor.name
					if (readMethod.parameterTypes.length == 0) {
						def action = readMethod.invoke(instance)
						if (action instanceof Closure) {
							controllerClassClosures[closureName] = createSource(
									sourceControllerClass.fullName, closureName)
						}
					}
				}
			}
		}

		for (Method method : sourceControllerClass.clazz.methods) {
			if (!Modifier.isStatic(method.modifiers)) {
				controllerClassClosures[method.name] = createSource(
					sourceControllerClass.fullName, method.name)
			}
		}

		def plugin = [:]
		GrailsPlugin annotation = sourceControllerClass.clazz.getAnnotation(GrailsPlugin)
		if (annotation) {
			plugin.name = annotation.name()
			plugin.version = annotation.version()
		}

		registerClosures destControllerClassName, controllerClassClosures, plugin, application
	}

	/**
	 * Compiles and registers a plugin to contain action closures.
	 * @param className  the full class name to use
	 * @param plugin  optional name and version if registered from a plugin
	 * @return  the controller class
	 */
	static GrailsControllerClass createDynamicController(String className, plugin, GrailsApplication application) {

		String packageDef = ''
		int index = className.lastIndexOf('.')
		if (index > -1) {
			packageDef = "package ${className.substring(0, index)}"
			className = className.substring(index + 1)
		}

		String annotation = plugin ? "@GrailsPlugin(name=\"$plugin.name\", version=\"$plugin.version\")" : ''
		String classDefinition = """
		$packageDef

		import org.codehaus.groovy.grails.plugins.metadata.GrailsPlugin

		$annotation
		class $className {}
		"""
		LOG.debug "defining new controller as\n$classDefinition"
		def clazz = new GroovyClassLoader(application.classLoader).parseClass(classDefinition)

		// register it as if it was a class under grails-app/controllers
		GrailsControllerClass controllerClass = application.addArtefact(
				ControllerArtefactHandler.TYPE, clazz)

		// 2.0+
		if (ClassUtils.getMethodIfAvailable(controllerClass.getClass(), 'initialize')) {
			controllerClass.initialize()
		}

		// register the Spring bean
		application.mainContext.registerBeanDefinition clazz.name,
			new GenericBeanDefinition(beanClass: clazz,
					scope: AbstractBeanDefinition.SCOPE_PROTOTYPE,
					autowireMode:AbstractBeanDefinition.AUTOWIRE_BY_NAME)

		controllerClass
	}

	static Collection<String> getDynamicActions(String controllerClassName) {
		getClassClosures(controllerClassName).keySet()
	}

	private static Map getClassClosures(String controllerClassName) {
		def closures = _closures[controllerClassName]
		if (closures == null) {
			closures = [:]
			_closures[controllerClassName] = closures
		}
		closures
	}

	private static GrailsControllerClass lookupControllerClass(controllerClassName, GrailsApplication application) {
		application.getControllerClass(controllerClassName)
	}
}
