package net.corda.samples.example.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.samples.example.contracts.TicketContract
import net.corda.samples.example.states.TicketState
import java.util.*

object TransferFlow {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(val linearId: UniqueIdentifier, val newOwner: Party) : FlowLogic<SignedTransaction>() {
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
            val assetCriteria =
                QueryCriteria.LinearStateQueryCriteria().withUuid(Collections.singletonList(linearId.id))
            val asset: List<StateAndRef<TicketState>> =
                serviceHub.vaultService.queryBy<TicketState>(TicketState::class.java, assetCriteria).states
            val inputState: StateAndRef<TicketState> = asset.get(0)
            val notary: Party = inputState.state.notary

            val inputsIssuer: Party = inputState.state.data.issuer
            val oldOwner: Party = inputState.state.data.issuer
            val inputsSection: Int = inputState.state.data.section
            val conservedLinearId: UniqueIdentifier = inputState.state.data.linearId

            val output = TicketState(inputsIssuer, this.newOwner, inputsSection, conservedLinearId)
            val commandTransfer = Command<TicketContract.Commands.Transfer>(
                TicketContract.Commands.Transfer(),
                listOf(oldOwner.owningKey, this.newOwner.owningKey)
            )
            val txBuilder: TransactionBuilder = TransactionBuilder(notary).addCommand(commandTransfer)
                .addInputState(inputState).addOutputState(output, TicketContract.ID)

            progressTracker.currentStep = VERIFY_TRANSACTION
            txBuilder.verify(serviceHub)

            progressTracker.currentStep = SIGNING_TRANSACTION
            val partySignedTx: SignedTransaction = serviceHub.signInitialTransaction(txBuilder)

            val otherOwnerSession: FlowSession;
            progressTracker.currentStep = GATHERING_SIGS
            if (ourIdentity == oldOwner) {
                otherOwnerSession = initiateFlow(newOwner)
            } else {
                otherOwnerSession = initiateFlow(oldOwner)
            }

            val fullySignedTx: SignedTransaction = subFlow(
                CollectSignaturesFlow(
                    partySignedTx,
                    listOf(otherOwnerSession),
                    GATHERING_SIGS.childProgressTracker()
                )
            )

            progressTracker.currentStep = FINALISING_TRANSACTION
            return subFlow(
                FinalityFlow(
                    fullySignedTx,
                    listOf(otherOwnerSession, initiateFlow(inputsIssuer)),
                    FINALISING_TRANSACTION.childProgressTracker()
                )
            )
//            TODO("Not yet implemented")
        }

    }

    @InitiatedBy(Initiator::class)
    class Responder(private val otherOwnerSession: FlowSession) : FlowLogic<SignedTransaction>() {
        companion object {
            object SIGNING_TRANSACTION : ProgressTracker.Step("About to sign transaction with our private key.") {
                override fun childProgressTracker() = SignTransactionFlow.Companion.tracker()
            }

            object FINALISING_TRANSACTION : ProgressTracker.Step("Waiting to record transaction.") {
                override fun childProgressTracker() = SignTransactionFlow.Companion.tracker()
            }

            fun tracker() = ProgressTracker(
                SIGNING_TRANSACTION,
                FINALISING_TRANSACTION
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

                override fun checkTransaction(halfSignedTx: SignedTransaction) {
                    requireThat {
                        val output: ContractState = halfSignedTx.tx.outputs.get(0).data
                        val ticket: TicketState = output as TicketState
                        "Only accepted ticket of section = 30" using (ticket.section == 30)

                    }
                }

            }

            val signTxFlow = SignTxFlow(otherOwnerSession, SignTransactionFlow.Companion.tracker())
            val txId = subFlow(signTxFlow).id
            progressTracker.currentStep = FINALISING_TRANSACTION
            return subFlow(ReceiveFinalityFlow(otherOwnerSession, txId))


        }

    }
}