package tech.b180.cordaptor.rest

import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.handler.AbstractHandler
import tech.b180.cordaptor.rest.ContextMappedHandler
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * Jetty context handler responsible for generating an OpenAPI definition file
 * based on the catalog of the underlying node.
 */
class ApiDefinitionHandler(override val contextPath: String) : ContextMappedHandler, AbstractHandler() {
  override fun handle(target: String?, baseRequest: Request?, request: HttpServletRequest?, response: HttpServletResponse?) {
    TODO("Not yet implemented")
  }
}
