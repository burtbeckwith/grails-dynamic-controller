import com.burtbeckwith.grails.plugins.dynamiccontroller.ControllerMixinArtefactHandler
import com.burtbeckwith.grails.plugins.dynamiccontroller.ControllerMixinArtefactHandler.ControllerMixinGrailsClass
import com.burtbeckwith.grails.plugins.dynamiccontroller.ControllerMixinArtefactHandler.DefaultControllerMixinGrailsClass
import com.burtbeckwith.grails.plugins.dynamiccontroller.DynamicControllerManager
import com.burtbeckwith.grails.plugins.dynamiccontroller.DynamicDelegateController

/**
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
class DynamicControllerGrailsPlugin {

	String version = '0.4'
	String grailsVersion = '2.0 > *'
	List watchedResources = [
		'file:./grails-app/controllerMixins/**/*ControllerMixin.groovy',
		'file:./plugins/*/grails-app/controllerMixins/**/*ControllerMixin.groovy']
	List loadBefore = ['controllers']
	List artefacts = [ControllerMixinArtefactHandler]

	String author = 'Burt Beckwith'
	String authorEmail = 'burt@burtbeckwith.com'
	String title = 'Dynamic Controller Plugin'
	String documentation = 'http://grails.org/plugin/dynamic-controller'
	String description = 'Supports controller mixins, where action closures are retrieved from various sources including existing controllers, files, database source, etc. Can also create full controllers dynamically.'

	String license = 'APACHE'
	def issueManagement = [system: 'JIRA', url: 'http://jira.grails.org/browse/GPDYNAMICCONTROLLER']
	def scm = [url: 'https://github.com/burtbeckwith/grails-dynamic-controller']

	def doWithSpring = {
		dynamicControllerManager(DynamicControllerManager)

		for (ControllerMixinGrailsClass cmgc in application.controllerMixinClasses) {
			"$cmgc.clazz.name"(cmgc.clazz) { bean ->
				bean.autoWire = 'byName'
			}
		}
	}

	def doWithApplicationContext = { ctx ->
		def dynamicControllerManager = ctx.dynamicControllerManager
		// mix in all controller mixins
		for (ControllerMixinGrailsClass cc in application.controllerMixinClasses) {
			dynamicControllerManager.mixin cc, application
		}
	}

	def doWithDynamicMethods = { ctx ->
		// these propertyMissing and methodMissing handlers will get called in methods but not closures
		for (ControllerMixinGrailsClass cc in application.controllerMixinClasses) {
			cc.clazz.metaClass.propertyMissing = { String name ->
				ctx.getBean(DynamicDelegateController.name)."$name"
			}
			cc.clazz.metaClass.methodMissing = { String name, args ->
				def controller = ctx.getBean(DynamicDelegateController.name)
				args ? controller."$name"(*args) : controller."$name"()
			}
		}
	}

	def onChange = { event ->
		application.addArtefact ControllerMixinArtefactHandler.TYPE, event.source
		application.mainContext.dynamicControllerManager.mixin new DefaultControllerMixinGrailsClass(event.source), application
	}
}
