package net.corda.samples.example.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.samples.example.contracts.TicketContract
import net.corda.samples.example.states.TicketState

object BuyFlow {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(
        val spectator: Party,
        val section: Int
    ) : FlowLogic<SignedTransaction>() {
        companion object {
            object GENERATE_TRANSACTION : ProgressTracker.Step("Generating transaction based on parameters")
            object VERIFY_TRANSACTION : ProgressTracker.Step("Verifying contract constraints")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction wiht our private key")
            object FINALISING_TRANSACTION :
                ProgressTracker.Step("Obtaining notary signature and recording transaction") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                GENERATE_TRANSACTION,
                VERIFY_TRANSACTION,
                SIGNING_TRANSACTION,
                FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()
        @Suspendable
        override fun call(): SignedTransaction {
            //Our Identity
            val issuer: Party = ourIdentity
            //We pick the first one of the list
            val notary: Party = serviceHub.networkMapCache.notaryIdentities.single()

            //Create the transaction
            progressTracker.currentStep = GENERATE_TRANSACTION
            val output = TicketState(issuer, this.spectator, this.section, UniqueIdentifier())
            val commandBuy = Command(TicketContract.Commands.Buy(), issuer.owningKey)
            val txBuilder = TransactionBuilder(notary).addCommand(commandBuy).addOutputState(output, TicketContract.ID)

            progressTracker.currentStep = VERIFY_TRANSACTION
            txBuilder.verify(serviceHub)

            progressTracker.currentStep = SIGNING_TRANSACTION
            val fullySignedTx = serviceHub.signInitialTransaction(txBuilder)

            progressTracker.currentStep = FINALISING_TRANSACTION
            val spectatorSession: FlowSession = initiateFlow(this.spectator)
            val notarised: SignedTransaction = subFlow(FinalityFlow(fullySignedTx, listOf(spectatorSession)))
            serviceHub.recordTransactions(StatesToRecord.ALL_VISIBLE,listOf(notarised))
            return notarised
//            TODO("Not yet implemented")

        }

    }

    @InitiatedBy(Initiator::class)
    class Responder(private val initiatorSession: FlowSession) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            return subFlow(ReceiveFinalityFlow(this.initiatorSession))
        }
    }
}