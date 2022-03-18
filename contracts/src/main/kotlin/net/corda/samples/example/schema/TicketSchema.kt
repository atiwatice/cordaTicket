package net.corda.samples.example.schema

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table
//4.6 changes
import org.hibernate.annotations.Type


/**
 * The family of schemas for IOUState.
 */
object TicketSchema

/**
 * An TicketState schema.
 */
object TicketSchemaV1 : MappedSchema(
        schemaFamily = TicketSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentTicket::class.java)) {

//    override val migrationResource: String?
//        get() = "ticket.changelog-master";

    @Entity
    @Table(name = "ticket_states_table")
    class PersistentTicket(
            @Column(name = "issuer")
            var issuerName: String,

            @Column(name = "spectator")
            var spectatorName: String,

            @Column(name = "section")
            var section: Int,

            @Column(name = "linear_id")
            @Type(type = "uuid-char")
            var linearId: UUID
    ) : PersistentState() {
        // Default constructor required by hibernate.
        constructor(): this("", "", 0, UUID.randomUUID())
    }
}