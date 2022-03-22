package net.corda.samples.example.contracts

import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.samples.example.states.ConstantsValue
import net.corda.samples.example.states.IOUState
import net.corda.samples.example.states.TicketState
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test

class ContractBuyTests {
    private val ledgerServices = MockServices()
    private val alice = TestIdentity(CordaX500Name("Alice", "London", "GB"))
    private val calry = TestIdentity(CordaX500Name("Calry", "London", "GB"))
    private val bob = TestIdentity(CordaX500Name("Bob", "New York", "US"))
    private val linearId: UniqueIdentifier = UniqueIdentifier()

    @Test
    fun `Any input can't be consumed when buying`() {
        ledgerServices.ledger {
            transaction {
                input(TicketContract.ID, TicketState(alice.party, bob.party, ConstantsValue.MID, linearId))
                output(TicketContract.ID, TicketState(calry.party, bob.party, ConstantsValue.MID, linearId))

                command(alice.publicKey, TicketContract.Commands.Buy())
                `fails with`("Any input can't be consumed when buying")
//                `fails with`("transaction must not include inputs.")
            }
        }
    }

    @Test
    fun `Only one output can be issued`() {
        ledgerServices.ledger {
            transaction {
                output(TicketContract.ID, TicketState(alice.party, bob.party, ConstantsValue.LOW, linearId))
                output(TicketContract.ID, TicketState(calry.party, bob.party, ConstantsValue.LOW, linearId))

                command(listOf(calry.party.owningKey,bob.party.owningKey,calry.party.owningKey), TicketContract.Commands.Buy())
                `fails with`("Only one output can be issued")
            }
        }
    }

    @Test
    fun `Issuer and Spectator can't be equals`() {
        ledgerServices.ledger {
            transaction {
                output(TicketContract.ID, TicketState(alice.party, alice.party, ConstantsValue.LOW, linearId))
                command(alice.party.owningKey, TicketContract.Commands.Buy())
                `fails with`("Issuer and Spectator can't be equals")
            }
        }
    }

    @Test
    fun `Only LOW=15, MID=30 or HIGH=50 section allowed`() {
        ledgerServices.ledger {
            transaction {
                output(TicketContract.ID, TicketState(alice.party, bob.party, 5, linearId))
                command(alice.party.owningKey, TicketContract.Commands.Buy())
                `fails with`("Only LOW=15, MID=30 or HIGH=50 section allowed")
            }
        }
    }

    @Test
    fun `LOW Section`() {
        ledgerServices.ledger {
            transaction {
                output(TicketContract.ID, TicketState(alice.party, bob.party, ConstantsValue.LOW, linearId))
                command(alice.publicKey, TicketContract.Commands.Buy())
                verifies()
            }
        }
    }

    @Test
    fun `MID Section`() {
        ledgerServices.ledger {
            transaction {
                output(TicketContract.ID, TicketState(alice.party, bob.party, ConstantsValue.MID, linearId))
                command(alice.party.owningKey, TicketContract.Commands.Buy())
                verifies()
            }
        }
    }

    @Test
    fun `HIGH Section`() {
        ledgerServices.ledger {
            transaction {
                output(TicketContract.ID, TicketState(alice.party, bob.party, ConstantsValue.HIGH, linearId))
                command(alice.party.owningKey, TicketContract.Commands.Buy())
                verifies()
            }
        }
    }

    @Test
    fun `Only issuer must signed the transaction`() {
        ledgerServices.ledger {
            transaction {
                output(TicketContract.ID, TicketState(alice.party, bob.party, ConstantsValue.HIGH, linearId))
                command(bob.party.owningKey, TicketContract.Commands.Buy())
                `fails with`("Only issuer must signed the transaction")
            }
        }
    }

    @Test
    fun `Check full behavior transaction`() {
        ledgerServices.ledger {
            transaction {
                output(TicketContract.ID, TicketState(alice.party, bob.party, ConstantsValue.HIGH, linearId))
                command(alice.party.owningKey, TicketContract.Commands.Buy())
                verifies()
            }
        }
    }

}