package tech.b180.cordaptor.rest

import io.undertow.Undertow
import io.undertow.server.HttpHandler
import io.undertow.server.handlers.PathHandler
import io.undertow.server.handlers.SSLHeaderHandler
import org.koin.core.get
import org.koin.core.inject
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import tech.b180.cordaptor.kernel.*
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import javax.net.ssl.*


/**
 * Describes a single handler for a particular path prefix used to route API requests.
 */
@ModuleAPI(since = "0.1")
interface ContextMappedHandler : HttpHandler {
  val mappingParameters: Parameters

  /** Specific set of parameters for the handler */
  data class Parameters(
      val path: String,
      val exactPathOnly: Boolean
  )
}

/**
 * Implementations contribute some aspect of configuration to the server.
 */
@ModuleAPI(since = "0.1")
interface UndertowConfigContributor {
  fun contribute(builder: Undertow.Builder)
}

/**
 * Contract for the logic that knows how to configure [SSLContext] for Undertow HTTPS listener,
 * as well as what SSL-specific handlers to add to Undertow handler's chain to
 * handler SSL-specific request parameters like client certificate and ciphers.
 */
@ModuleAPI(since = "0.1")
interface SSLConfigurator {

  fun createSSLContext(): SSLContext

  fun createSSLHandler(innerHandler: HttpHandler): HttpHandler
}

/**
 * Default implementation of [SSLConfigurator]
 */
open class DefaultSSLConfigurator(private val settings: WebServerSettings) : SSLConfigurator, CordaptorComponent {

  companion object {
    private val logger = loggerFor<UndertowHandlerContributor>()
  }

  override fun createSSLContext(): SSLContext {
    val sslContext = instantiateSSLContext()
    sslContext.init(instantiateKeyManagers(loadKeyStore()), instantiateTrustManagers(loadTrustStore()), null)
    return sslContext
  }

  open fun instantiateSSLContext(): SSLContext {
    return settings.secureTransportSettings.let {
      if (it.sslContextProvider != null) {
        logger.info("Using SSLContext ${it.sslContextName} with from ${it.sslContextProvider} provider")
        SSLContext.getInstance(it.sslContextName, it.sslContextProvider)
      } else {
        logger.info("Using SSLContext ${it.sslContextName} with from default provider")
        SSLContext.getInstance(it.sslContextName)
      }
    }
  }

  open fun loadKeyStore(): KeyStore {
    return settings.secureTransportSettings.let { s ->
      logger.debug("Loading {} keystore from {}", s.keyStoreType, s.keyStoreLocation)
      loadKeystoreFrom(s.keyStoreType, s.keyStoreLocation!!, s.keyStorePassword!!)
    }
  }

  open fun loadTrustStore(): KeyStore {
    return settings.secureTransportSettings.let { s ->
      logger.debug("Loading {} truststore from {}", s.trustStoreType, s.trustStoreLocation)
      loadKeystoreFrom(s.trustStoreType, s.trustStoreLocation!!, s.trustStorePassword!!)
    }
  }

  private fun loadKeystoreFrom(type: String, file: File, password: StringSecret): KeyStore {
    val keystore = KeyStore.getInstance(type)
    FileInputStream(file).use { stream ->
      useSecret(password) { pwd ->
        keystore.load(stream, pwd)
      }
    }
    return keystore
  }

  open fun instantiateKeyManagers(keyStore: KeyStore): Array<KeyManager> {
    val keyManagerFactory = KeyManagerFactory.getInstance(
        settings.secureTransportSettings.keyManagerFactoryAlgo)

    useSecret(settings.secureTransportSettings.keyStorePassword!!) {
      keyManagerFactory.init(keyStore, it)
    }

    return keyManagerFactory.keyManagers
  }

  open fun instantiateTrustManagers(keyStore: KeyStore): Array<TrustManager> {
    val trustManagerFactory = TrustManagerFactory.getInstance(
        settings.secureTransportSettings.trustManagerFactoryAlgo)

    trustManagerFactory.init(keyStore)

    return trustManagerFactory.trustManagers
  }

  override fun createSSLHandler(innerHandler: HttpHandler): HttpHandler {
    return SSLHeaderHandler(innerHandler)
  }
}

/**
 * Contains logic for configuring listeners for HTTP connections, optionally with SSL enabled.
 */
class UndertowListenerContributor(
    private val settings: WebServerSettings,
    private val sslConfigurator: SSLConfigurator
) : UndertowConfigContributor {

  override fun contribute(builder: Undertow.Builder) {
    val address = settings.bindAddress
    if (settings.isSecure) {
      builder.addHttpsListener(address.port, address.hostname, sslConfigurator.createSSLContext())
    } else {
      builder.addHttpListener(address.port, address.hostname)
    }
  }
}

/**
 * Contains logic for configuring path handlers for HTTP requests,
 * as well as an overarching security handler.
 */
class UndertowHandlerContributor(
    private val webServerSettings: WebServerSettings,
    private val securitySettings: SecuritySettings,
    private val sslConfigurator: SSLConfigurator
) : UndertowConfigContributor, CordaptorComponent {

  companion object {
    private val logger = loggerFor<UndertowHandlerContributor>()
  }

  override fun contribute(builder: Undertow.Builder) {

    val factoryName = securitySettings.securityHandlerName
    logger.debug("Using security configuration: {}", factoryName)

    val factory = if (factoryName != SECURITY_CONFIGURATION_NONE) {
      get<SecurityHandlerFactory>(named(factoryName))
    } else {
      null
    }

    // gather all handlers to construct a path mapping
    val mappedHandlers : List<ContextMappedHandler> =
        // all handlers that are defined as Koin components directly
        getAll<ContextMappedHandler>() +
            // all handlers created via a factory that are Koin components
            getAll<EndpointProvider>().flatMap { provider ->
              provider.operationEndpoints.map {
                get<OperationEndpointHandler<*, *>> { parametersOf(it) }
              } +
                  provider.queryEndpoints.map {
                    get<QueryEndpointHandler<*>> { parametersOf(it) }
                  }
            } +
            // all query and operation endpoints defined as Koin components directly
            getAll<QueryEndpoint<*>>().map { get<QueryEndpointHandler<*>> { parametersOf(it) } } +
            getAll<OperationEndpoint<*, *>>().map { get<OperationEndpointHandler<*, *>> { parametersOf(it) } }

    val pathHandler = PathHandler()

    for (handler in mappedHandlers) {
      val (path, exactPathOnly) = handler.mappingParameters
      if (exactPathOnly) {
        logger.debug("Mapping handler {} to exact path {}", handler, path)
        pathHandler.addExactPath(path, handler)
      } else {
        logger.debug("Mapping handler {} to path prefix {}", handler, path)
        pathHandler.addPrefixPath(path, handler)
      }
    }

    var rootHandler: HttpHandler = pathHandler
    if (factory != null) {
      logger.debug("API endpoints security configuration factory {}", factory)
      rootHandler = factory.createSecurityHandler(pathHandler)
    } else {
      logger.warn("API endpoints are not protected by any security configuration")
    }

    if (webServerSettings.isSecure) {
      rootHandler = sslConfigurator.createSSLHandler(rootHandler)
    }

    builder.setHandler(rootHandler)
  }
}

/**
 * Contains logic that configures Undertow server itself using container settings.
 * At the moment it only allows to initialize basic settings.
 */
class UndertowSettingsContributor(
    private val settings: WebServerSettings
) : UndertowConfigContributor, CordaptorComponent {

  override fun contribute(builder: Undertow.Builder) {
    builder.setIoThreads(settings.ioThreads)
    builder.setWorkerThreads(settings.workerThreads)
  }
}

/**
 * Wrapper managing the lifecycle of the Undertow webserver in line with the lifecycle of the container.
 * It delegates most of the work to instances of [UndertowConfigContributor] defined via Koin.
 */
class WebServer : LifecycleAware, CordaptorComponent {
  companion object {
    private val logger = loggerFor<WebServer>()
  }

  lateinit var server: Undertow

  private val control: LifecycleControl by inject()

  override fun onInitialize() {
    val builder = Undertow.builder()

    val contributors = getAll<UndertowConfigContributor>()
    for (contributor in contributors) {
      logger.debug("Invoking configuration contributor {}", contributor)
      contributor.contribute(builder)
    }

    server = builder.build()
    server.start()

    logger.info("Web server started")
    control.serverStarted()
  }

  override fun onShutdown() {
    server.stop()
  }
}
