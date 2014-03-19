package com.burtbeckwith.grails.plugins.dynamiccontroller

import grails.util.Environment
import grails.web.Action

import java.beans.PropertyDescriptor
import java.lang.reflect.Method
import java.lang.reflect.Modifier

import org.codehaus.groovy.grails.commons.ControllerArtefactHandler
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.commons.GrailsClass
import org.codehaus.groovy.grails.commons.GrailsControllerClass
import org.codehaus.groovy.grails.plugins.metadata.GrailsPlugin
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.support.AbstractBeanDefinition
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.core.io.Resource

import com.burtbeckwith.grails.plugins.dynamiccontroller.ControllerMixinArtefactHandler.ControllerMixinGrailsClass

/**
 * Handles all of the logic for managing controller mixins and dynamic actions.
 *
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
class DynamicControllerManager {

	protected final Map<String, Map<String, ?>> closures = [:]
	protected final Logger log = LoggerFactory.getLogger(getClass())

	/**
	 * Register controller closures.
	 * @param controllerClassName the class name of the destination controller
	 * @param controllerClassClosureSources the action names and ClosureSources
	 * @param plugin optional name and version if registered from a plugin
	 */
	void registerClosures(String controllerClassName, Map<String, ClosureSource> controllerClassClosureSources,
	                      plugin, GrailsApplication application) {

		GrailsControllerClass controllerClass = lookupControllerClass(controllerClassName, application)
		if (!controllerClass) {
			log.info "Can't find controller with name $controllerClassName, creating dynamic controller"
			controllerClass = createDynamicController(controllerClassName, plugin, application)
		}

		// register the urls /controllername/actionname and /controllername/actionname/**
		for (String action in controllerClassClosureSources.keySet()) {
			controllerClass.registerMapping action
			log.info "registered action $action for $controllerClassName"
		}

		getClassClosures(controllerClassName).putAll controllerClassClosureSources

		controllerClass.clazz.metaClass.getProperty = { String name ->
			if ('controllerName' == name) {
				// fake out as the containing controller
				return lookupControllerClass(controllerClassName, application).logicalPropertyName
			}

			lookupProperty name, controllerClassName, delegate, application
		}

		//register the closures so they can be retreived by : metaProperty = controller.getMetaClass().getMetaProperty(actionName);
		// in MixedGrailsControllerHelper
		controllerClassClosureSources.each { String action, ClosureSource cs ->
			controllerClass.clazz.metaClass."get${action.capitalize()}" << { ->
				Closure newClosure = cs.closure.clone()
				newClosure.delegate = delegate
				newClosure
			}
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
	 * @param controllerClassName the class name of the destination controller
	 * @param resource the resource
	 * @param plugin optional name and version if registered from a plugin
	 */
	void registerClosures(String controllerClassName, Resource resource, plugin, GrailsApplication application) {
		def config = new ConfigSlurper(Environment.current.name).parse(resource.getURL())

		Map<String, ?> closureSources = [:]
		for (actionName in config.keySet()) {
			closureSources[actionName] = new ResourceClosureSource(resource, actionName)
		}
		registerClosures controllerClassName, closureSources, plugin, application
	}

	protected lookupProperty(String name, String controllerClassName, controller, GrailsApplication application) {
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
	 * @param sourceControllerClassName the source class name
	 * @param destControllerClazz the destination controller class
	 */
	void mixin(String sourceControllerClassName, String destControllerClassName, GrailsApplication application) {
		def sourceControllerClass = lookupControllerClass(sourceControllerClassName, application)
		if (!sourceControllerClass) {
			log.error "Controller $sourceControllerClassName not found, cannot mix in"
			return
		}

		mixin sourceControllerClass, destControllerClassName, application, { String controllerName, String actionName, Method method ->
			new ControllerClosureSource(controllerName, actionName, method, application)
		}
	}

	/**
	 * Mix in closures from a ControllerMixinGrailsClass.
	 * @param cc the source
	 */
	void mixin(ControllerMixinGrailsClass cc, GrailsApplication application) {
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
			log.error "No destination controller specified in the 'controller' property or " +
				"'grails.plugins.dynamicController.mixins' config attribute for ${cc.name}ControllerMixin, ignoring"
			return
		}

		for (destControllerName in destControllerNames) {
			mixin cc, destControllerName.toString(), application, { String controllerName, String actionName, Method method ->
				new ControllerMixinClosureSource(controllerName, actionName, application, method)
			}
		}
	}

	/**
	 * Mix in closures from a controller or ControllerMixinGrailsClass.
	 *
	 * @param sourceControllerClass the source
	 * @param destControllerClassName the destination controller class name
	 * @param createSource a closure that creates a ClosureSource
	 */
	void mixin(GrailsClass sourceControllerClass, String destControllerClassName,
	           GrailsApplication application, Closure createSource) {

		Map<String, ClosureSource> controllerClassClosureSources = [:]

		boolean mixin = sourceControllerClass instanceof ControllerMixinArtefactHandler.ControllerMixinGrailsClass

		// loop through all properties to find 'def foo = {...' or 'Closure foo = {...'
		// this is only needed for ControllerMixins since Grails "converts" closures to methods
		List<String> propertyMethodNames = []
		if (mixin) {
			def instance = sourceControllerClass.newInstance()
			for (PropertyDescriptor propertyDescriptor : sourceControllerClass.propertyDescriptors) {
				Method readMethod = propertyDescriptor.readMethod
				if (readMethod && !Modifier.isStatic(readMethod.modifiers)) {
					if (propertyDescriptor.writeMethod) {
						propertyMethodNames << readMethod.name
						propertyMethodNames << propertyDescriptor.writeMethod.name
					}
					Class<?> propertyType = propertyDescriptor.propertyType
					if (propertyType == Object || propertyType == Closure) {
						String closureName = propertyDescriptor.name
						if (readMethod.parameterTypes.length == 0) {
							def action = readMethod.invoke(instance)
							if (action instanceof Closure) {
								controllerClassClosureSources[closureName] = createSource(
									sourceControllerClass.fullName, closureName, null)
							}
						}
					}
				}
			}
		}

		for (Method method : sourceControllerClass.clazz.methods) {
			if (mixin && isActionMethod(method, propertyMethodNames)) {
				controllerClassClosureSources[method.name] = createSource(
					sourceControllerClass.fullName, method.name, method)
			}
			else {
				if (method.getAnnotation(Action)) {
					def source
					if (createSource.maximumNumberOfParameters == 2) {
						source = createSource(sourceControllerClass.fullName, method.name)
					}
					else {
						source = createSource(sourceControllerClass.fullName, method.name, method)
					}
					controllerClassClosureSources[method.name] = source
				}
			}
		}

		def plugin = [:]
		GrailsPlugin annotation = sourceControllerClass.clazz.getAnnotation(GrailsPlugin)
		if (annotation) {
			plugin.name = annotation.name()
			plugin.version = annotation.version()
		}

		registerClosures destControllerClassName, controllerClassClosureSources, plugin, application
	}

	/**
	 * Compiles and registers a plugin to contain action closures.
	 * @param className the full class name to use
	 * @param plugin optional name and version if registered from a plugin
	 * @return the controller class
	 */
	GrailsControllerClass createDynamicController(String className, plugin, GrailsApplication application) {

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
		log.debug "defining new controller as\n$classDefinition"
		def clazz = new GroovyClassLoader(application.classLoader).parseClass(classDefinition)

		// register it as if it was a class under grails-app/controllers
		GrailsControllerClass controllerClass = application.addArtefact(ControllerArtefactHandler.TYPE, clazz)

		controllerClass.initialize()

		// register the Spring bean
		application.mainContext.registerBeanDefinition clazz.name,
			new GenericBeanDefinition(beanClass: clazz,
					scope: AbstractBeanDefinition.SCOPE_PROTOTYPE,
					autowireMode:AbstractBeanDefinition.AUTOWIRE_BY_NAME)

		controllerClass
	}

	Collection<String> getDynamicActions(String controllerClassName) {
		getClassClosures(controllerClassName).keySet()
	}

	protected Map getClassClosures(String controllerClassName) {
		def controllerClosures = closures[controllerClassName]
		if (controllerClosures == null) {
			controllerClosures = [:]
			closures[controllerClassName] = controllerClosures
		}
		controllerClosures
	}

	protected GrailsControllerClass lookupControllerClass(controllerClassName, GrailsApplication application) {
		application.getControllerClass(controllerClassName)
	}

	protected boolean isActionMethod(Method method, List<String> propertyMethodNames) {
		if (Modifier.isStatic(method.modifiers)) {
			return false
		}

		if (propertyMethodNames.contains(method.name)) {
			return false
		}

		if (method.declaringClass == Object) {
			return false
		}

		if (method.name.contains('$') || method.name == 'invokeMethod' || method.name == 'setProperty' || method.name == 'getProperty') {
			return false
		}

		true
	}
}
