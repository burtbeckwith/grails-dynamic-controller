package com.burtbeckwith.grails.plugins.dynamiccontroller

import grails.util.Environment
import groovy.sql.Sql

import javax.sql.DataSource

import org.codehaus.groovy.grails.commons.GrailsApplication

/**
 * A ClosureSource that retrieves from a database. Subclass and override
 * <code>loadFromDatabase()</code> to use different SQL.
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
class DatabaseClosureSource extends AbstractClosureSource {

	protected final String controllerClassName
	protected final DataSource dataSource

	/**
	 * Constructor.
	 * @param controllerClassName the full class name of the destination controller
	 * @param actionName the action name
	 * @param dataSource the datasource to load from
	 */
	DatabaseClosureSource(String controllerClassName, String actionName, DataSource dataSource) {
		super(actionName)
		this.controllerClassName = controllerClassName
		this.dataSource = dataSource
	}

	@Override
	protected Closure doGetClosure() {
		Sql sql = new Sql(dataSource)
		String code = loadFromDatabase(sql)
		def config = new ConfigSlurper(Environment.current.name).parse(code)
		config[actionName]
	}

	/**
	 * Retrieve the closure code.
	 * @param sql a Sql instance
	 * @return the code
	 */
	protected String loadFromDatabase(Sql sql) {
		def row = sql.firstRow('SELECT closure FROM closures WHERE action=? AND controller=?',
		                       [actionName, controllerClassName])
		row[0]
	}

	/**
	 * Utility method to find all instances in the database and register them. Typically
	 * called from BootStrap.
	 *
	 * @param dataSource the dataSource bean
	 * @param application the GrailsApplication
	 */
	static void registerAll(dataSource, GrailsApplication application) {
		def sql = new Sql(dataSource)
		def dynamicControllerManager = application.mainContext.dynamicControllerManager

		def controllers = []
		sql.eachRow('select distinct controller from closures', { controllers << it.controller })
		for (controller in controllers) {
			def closures = [:]
			sql.eachRow 'select action from closures where controller=?', [controller], {
				closures[it.action] = new DatabaseClosureSource(controller, it.action, dataSource)
			}
			dynamicControllerManager.registerClosures controller, closures, null, application
		}
	}
}
