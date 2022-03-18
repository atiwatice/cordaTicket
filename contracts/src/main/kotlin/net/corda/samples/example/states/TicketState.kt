package net.corda.samples.example.states

import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.samples.example.contracts.TicketContract
import net.corda.samples.example.schema.TicketSchemaV1
import java.util.*

@BelongsToContract(TicketContract::class)
data class TicketState(
    val issuer: Party,
    val spectator: Party,
    val section: Int,
    override val linearId: UniqueIdentifier= UniqueIdentifier()
) : LinearState, QueryableState {

    /** The public keys of the involved parties. */
    override val participants: List<AbstractParty> get() = listOf(issuer, spectator)
    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is TicketSchemaV1 -> TicketSchemaV1.PersistentTicket(
                this.issuer.name.toString(),
                this.spectator.name.toString(),
                this.section,
                this.linearId.id
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }
    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(TicketSchemaV1)
    override fun hashCode(): Int = Objects.hash(issuer, spectator, section, linearId);
}