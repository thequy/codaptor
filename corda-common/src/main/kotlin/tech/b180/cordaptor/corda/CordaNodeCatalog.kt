package tech.b180.cordaptor.corda

import net.corda.core.contracts.ContractState
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import tech.b180.cordaptor.kernel.ModuleAPI
import java.net.URL
import kotlin.reflect.KClass

/**
 * Single access point for all descriptive information about CorDapps installed
 * on a particular Corda node that may be used when implementing Cordaptor API.
 *
 * Different modules implement this interface in a different way depending
 * on the nature of their interaction with the underlying node.
 */
@ModuleAPI
interface CordaNodeCatalog {

  /** Contains descriptions of all available CorDapps */
  val cordapps: Collection<CordappInfo>
}

/**
 * Marker interface allowing decorating implementation of [CordaNodeCatalog] to locate
 * the underlying implementation.
 */
@ModuleAPI
interface CordaNodeCatalogInner : CordaNodeCatalog

/**
 * General metadata about a CorDapp obtained from a node and/or available CorDapp JAR files.
 */
@ModuleAPI
data class CordappInfo(
    /** Name taken from the CorDapp metadata, which is used to construct API endpoint URLs */
    val shortName: String,
    val flows: List<CordappFlowInfo>,
    val contractStates: List<CordappContractStateInfo>,
    val jarHash: SecureHash.SHA256,
    val jarURL: URL
)

@ModuleAPI
data class CordappFlowInfo(
    val flowClass: KClass<out FlowLogic<Any>>,
    val flowResultClass: KClass<out Any>
)

@ModuleAPI
data class CordappContractStateInfo(
    val stateClass: KClass<out ContractState>
)