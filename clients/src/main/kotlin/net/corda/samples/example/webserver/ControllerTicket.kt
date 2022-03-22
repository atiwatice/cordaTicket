package net.corda.samples.example.webserver

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.client.jackson.JacksonSupport.createNonRpcMapper
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.utilities.getOrThrow
import net.corda.samples.example.flows.BuyFlow.Initiator
import net.corda.samples.example.states.IOUState
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest
import net.corda.client.jackson.JacksonSupport
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter

//val SERVICE_NAMES = listOf("Notary", "Network Map Service")

/**
 * Define your API endpoints here.
 */
@RestController
@RequestMapping("/ticket/") // The paths for HTTP requests are relative to this base path.
class ControllerTicket(rpc: NodeRPCConnection) {


    companion object {
        private val logger = LoggerFactory.getLogger(RestController::class.java)
    }
    @Bean
    open fun mappingJackson2HttpMessageConverter(@Autowired rpcConnection: NodeRPCConnection): MappingJackson2HttpMessageConverter {
        val mapper = JacksonSupport.createDefaultMapper(rpcConnection.proxy)
        val converter = MappingJackson2HttpMessageConverter()
        converter.objectMapper = mapper
        return converter
    }


    private val myLegalName = rpc.proxy.nodeInfo().legalIdentities.first().name
    private val proxy = rpc.proxy

    /**
     * Initiates a flow to agree an IOU between two parties.
     *
     * Once the flow finishes it will have written the IOU to ledger. Both the lender and the borrower will be able to
     * see it when calling /spring/api/ious on their respective nodes.
     *
     * This end-point takes a Party name parameter as part of the path. If the serving node can't find the other party
     * in its network map cache, it will return an HTTP bad request.
     *
     * The flow is invoked asynchronously. It returns a future when the flow's call() method returns.
     */

    @PostMapping(value = ["create-ticket"], produces = [MediaType.TEXT_PLAIN_VALUE], headers = ["Content-Type=application/x-www-form-urlencoded"])
    fun createTicket(request: HttpServletRequest): ResponseEntity<String> {
        val spectator = request.getParameter("spectator")
        val section = request.getParameter("section").toInt()

        if(spectator == null){
            return ResponseEntity.badRequest().body("Query parameter 'spectator' must not be null.\n")
        }
        if (section <= 0 ) {
            return ResponseEntity.badRequest().body("Query parameter 'section' must be non-negative.\n")
        }
        val partyX500Name = CordaX500Name.parse(spectator)
        val otherParty = proxy.wellKnownPartyFromX500Name(partyX500Name) ?: return ResponseEntity.badRequest().body("Spectator named $spectator cannot be found.\n")

        return try {
            val signedTx = proxy.startTrackedFlow(::Initiator, otherParty, section).returnValue.getOrThrow()
            ResponseEntity.status(HttpStatus.CREATED).body("Transaction id ${signedTx.id} committed to ledger.\n")

        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            ResponseEntity.badRequest().body(ex.message!!)
        }
    }

    /**
     * Displays all IOU states that only this node has been involved in.
     */
    @GetMapping(value = ["my-ious"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getMyIOUs(): ResponseEntity<List<StateAndRef<IOUState>>> {
        val myious = proxy.vaultQueryBy<IOUState>().states.filter { it.state.data.lender.equals(proxy.nodeInfo().legalIdentities.first()) }
        return ResponseEntity.ok(myious)
    }
}
