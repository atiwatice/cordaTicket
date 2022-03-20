package net.corda.samples.example.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.samples.example.contracts.TicketContract
import net.corda.samples.example.states.TicketState
import java.util.stream.Collectors

object ExitFlow {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(val linearId: UniqueIdentifier) : FlowLogic<SignedTransaction>() {

        companion object {
            object GENERATE_TRANSACTION : ProgressTracker.Step("Generating transaction based on parameters")
            object VERIFY_TRANSACTION : ProgressTracker.Step("Verifying contract constraints")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key")
            object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION :
                ProgressTracker.Step("Obtaining notary signature and recording transaction") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                GENERATE_TRANSACTION,
                VERIFY_TRANSACTION,
                SIGNING_TRANSACTION,
                GATHERING_SIGS,
                FINALISING_TRANSACTION
            )

        }

        override val progressTracker = tracker()
        @Suspendable
        override fun call(): SignedTransaction {
            progressTracker.currentStep = GENERATE_TRANSACTION
            val assetCriteria = QueryCriteria.LinearStateQueryCriteria(
                null,
                listOf(linearId),
                Vault.StateStatus.UNCONSUMED, null
            )
            val asset: List<StateAndRef<TicketState>> =
                serviceHub.vaultService.queryBy<TicketState>(TicketState::class.java, assetCriteria).states
            val input: StateAndRef<TicketState> = asset.get(0)

            val notary: Party = input.state.notary
            val inputState: TicketState = input.state.data
            val keyTicket = inputState.participants.stream().map { it.owningKey }.collect(Collectors.toList())
            val commandExit = Command(TicketContract.Commands.Exit(), keyTicket)
            val txBuilder: TransactionBuilder = TransactionBuilder(notary).addCommand(commandExit).addInputState(input)

            progressTracker.currentStep = VERIFY_TRANSACTION
            txBuilder.verify(serviceHub)

            progressTracker.currentStep = SIGNING_TRANSACTION
            val partySignedTx: SignedTransaction = serviceHub.signInitialTransaction(txBuilder)

            val otherOwnerSession: FlowSession
            if (ourIdentity == inputState.issuer) {
                otherOwnerSession = initiateFlow(inputState.spectator)
            } else {
                otherOwnerSession = initiateFlow(inputState.issuer)
            }

            val fullySignedTx: SignedTransaction = subFlow(
                CollectSignaturesFlow(
                    partySignedTx,
                    listOf(otherOwnerSession),
                    CollectSignaturesFlow.Companion.tracker()
                )
            )

            progressTracker.currentStep = FINALISING_TRANSACTION
            return subFlow(FinalityFlow(fullySignedTx, listOf(otherOwnerSession)))

//            TODO("Not yet implemented")
        }

    }

    @InitiatedBy(Initiator::class)
    class Responder(private val otherOwnerSession: FlowSession) : FlowLogic<SignedTransaction>() {

        companion object {
            object SIGNING_TRANSACTION : ProgressTracker.Step("About to sign transaction with our provate key.") {
                override fun childProgressTracker() = SignTransactionFlow.Companion.tracker()
            }

            object FINALISING_TRANSACTION : ProgressTracker.Step("Waiting to record transaction.") {
                override fun childProgressTracker() = SignTransactionFlow.Companion.tracker()
            }

            fun tracker() = ProgressTracker(
                TransferFlow.Responder.Companion.SIGNING_TRANSACTION,
                TransferFlow.Responder.Companion.FINALISING_TRANSACTION
            )
        }


        override val progressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            progressTracker.currentStep = SIGNING_TRANSACTION
            class SignTxFlow : SignTransactionFlow {
                constructor(otherPartyFlow: FlowSession, progressTracker: ProgressTracker) : super(
                    otherPartyFlow,
                    progressTracker
                )

                override fun checkTransaction(stx: SignedTransaction) {
                    TODO("Not yet implemented")
                }

            }

            val signTxFlow: SignTxFlow = SignTxFlow(otherOwnerSession, SignTransactionFlow.Companion.tracker())
            val txId: SecureHash = subFlow(signTxFlow).id
            progressTracker.currentStep = FINALISING_TRANSACTION
            return subFlow(ReceiveFinalityFlow(otherOwnerSession, txId))
        }
    }
}