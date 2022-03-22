package net.corda.samples.example

import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.node.services.queryBy
import net.corda.core.utilities.getOrThrow
import net.corda.samples.example.flows.BuyFlow
import net.corda.samples.example.flows.ExampleFlow
import net.corda.samples.example.states.ConstantsValue
import net.corda.samples.example.states.IOUState
import net.corda.samples.example.states.TicketState
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TicketFlowTests {
    private lateinit var network: MockNetwork
    private lateinit var a: StartedMockNode
    private lateinit var b: StartedMockNode

    @Before
    fun setup() {
        network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
                TestCordapp.findCordapp("net.corda.samples.example.contracts"),
                TestCordapp.findCordapp("net.corda.samples.example.flows")
        )))
        a = network.createPartyNode()
        b = network.createPartyNode()
        // For real nodes this happens automatically, but we have to manually register the flow for tests.
        listOf(a, b).forEach { it.registerInitiatedFlow(BuyFlow.Responder::class.java) }
        network.runNetwork()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    @Test
    fun `flow rejects invalid section(accept LOW, MID, HIGH)`() {
        val flow = BuyFlow.Initiator(b.info.singleIdentity(), 10)
        val future = a.startFlow(flow)
        network.runNetwork()

        // The IOUContract specifies that IOUs cannot have negative values.
        assertFailsWith<TransactionVerificationException> { future.getOrThrow() }
    }

    @Test
    fun `SignedTransaction returned by the flow is signed by the initiator`() {
        val flow = BuyFlow.Initiator(b.info.singleIdentity(), ConstantsValue.MID)
        val future = a.startFlow(flow)
        network.runNetwork()

        val signedTx = future.getOrThrow()
        signedTx.verifySignaturesExcept(b.info.singleIdentity().owningKey)
    }

    @Test
    fun `SignedTransaction returned by the flow is signed by the acceptor`() {
        val flow = BuyFlow.Initiator(b.info.singleIdentity(), ConstantsValue.MID)
        val future = a.startFlow(flow)
        network.runNetwork()

        val signedTx = future.getOrThrow()
        signedTx.verifySignaturesExcept(a.info.singleIdentity().owningKey)
    }

    @Test
    fun `flow records a transaction in both parties' transaction storages`() {
        val flow = BuyFlow.Initiator(b.info.singleIdentity(), ConstantsValue.MID)
        val future = a.startFlow(flow)
        network.runNetwork()
        val signedTx = future.getOrThrow()

        // We check the recorded transaction in both transaction storages.
        for (node in listOf(a, b)) {
            assertEquals(signedTx, node.services.validatedTransactions.getTransaction(signedTx.id))
        }
    }

    @Test
    fun `recorded transaction has no inputs and a single output, the input Ticket`() {
        val flow = BuyFlow.Initiator(b.info.singleIdentity(), ConstantsValue.MID)
        val future = a.startFlow(flow)
        network.runNetwork()
        val signedTx = future.getOrThrow()

        // We check the recorded transaction in both vaults.
        for (node in listOf(a, b)) {
            val recordedTx = node.services.validatedTransactions.getTransaction(signedTx.id)
            val txOutputs = recordedTx!!.tx.outputs
            assert(txOutputs.size == 1)

            val recordedState = txOutputs[0].data as TicketState
            assertEquals(recordedState.section, ConstantsValue.MID)
            assertEquals(recordedState.issuer, a.info.singleIdentity())
            assertEquals(recordedState.spectator, b.info.singleIdentity())
        }
    }

    @Test
    fun `flow records the correct Ticket in both parties' vaults`() {
        val ticketValue = ConstantsValue.MID
        val flow = BuyFlow.Initiator(b.info.singleIdentity(), ConstantsValue.MID)
        val future = a.startFlow(flow)
        network.runNetwork()
        future.getOrThrow()

        // We check the recorded IOU in both vaults.
        for (node in listOf(a, b)) {
            node.transaction {
                val tickets = node.services.vaultService.queryBy<TicketState>().states
                assertEquals(1, tickets.size)
                val recordedState = tickets.single().state.data
                assertEquals(recordedState.section, ticketValue)
                assertEquals(recordedState.issuer, a.info.singleIdentity())
                assertEquals(recordedState.spectator, b.info.singleIdentity())
            }
        }
    }
}