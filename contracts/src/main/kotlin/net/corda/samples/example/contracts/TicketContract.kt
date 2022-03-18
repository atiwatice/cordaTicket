package net.corda.samples.example.contracts

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.CommandWithParties
import net.corda.core.contracts.Contract
import net.corda.core.contracts.Requirements.using
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import net.corda.samples.example.states.ConstantsValue
import net.corda.samples.example.states.TicketState
import java.util.stream.Collectors
import kotlin.streams.asStream

class TicketContract: Contract {
    companion object {
        @JvmStatic
        val ID = "net.corda.samples.example.contracts.TicketContract"
    }

    override fun verify(tx: LedgerTransaction) {
        val commandTicket: CommandWithParties<Commands> = tx.commands.requireSingleCommand<Commands>()
        val inputs:List<TicketState> = tx.inputsOfType()
        val outputs:List<TicketState> = tx.outputsOfType()
        if(commandTicket.value is Commands.Buy){
            //Constraint 1: Inputs =0
            "Any input can't be consumed when buying" using (inputs.isEmpty())
            //Constraint 2: Outputs = 1
            "Only one output can be issued" using (outputs.size == 1)

            val output = outputs[0]
            //Constraint3 issue != Spectactor
            "Issuer and Spectator can't be equals" using (!(output.issuer.equals(output.spectator) ))
//            //Constraint4 Section should match the value LOW, MID or HIGH
            "Only LOW=15, MID=30 or HIGH=50 section allowed" using (output.section.equals(ConstantsValue.LOW)
                    || output.section.equals(ConstantsValue.MID)  || output.section.equals(ConstantsValue.HIGH) )
//            //Constraint5 Signers => Issuer only, spectator is happy to receive the rewards
            "Only issuer must signed the transaction" using (commandTicket.signers
                .containsAll(listOf(output.issuer.owningKey)))


        }else if(commandTicket.value is Commands.Transfer){
            //Constraint 1: Inputs =1
            "Only 1 input" using (inputs.size==1)
            //Constraint 2: Outputs = 1
            "Only 1 output" using (outputs.size==1)

            val input = inputs[0]
            val output = outputs[0]
            "Input's Issuer should be equals to output's Issuer" using (input.issuer == output.spectator)
            "Input's Spectator and output's Spectator can't be equals" using (input.spectator != output.spectator)
            "Section has to be conserved in the transfer" using (input.section == output.section)
            "LinearId has to be conserved in the transfer" using (input.linearId == output.linearId)
            var signers: Iterable<Party> = listOf<Party>(input.spectator,output.spectator)
            "input's spectator and output's spectator have to signed" using (
                    commandTicket.signers
                        .containsAll(signers.asSequence().asStream().map{it.owningKey}.collect(Collectors.toSet()))
                    )
        }else if(commandTicket.value is Commands.Exit){
            //Constraint 1 Inputs = 1
            "Only 1 input can be exit" using (inputs.size==1)
            //Constraint 2 Outputs = 0
            "Any output should be create" using (outputs.size==0)

            val input = inputs.get(0)
            "Spectator and Issuer have to signed" using (commandTicket.signers
                .containsAll(input.participants.map { it.owningKey }))
        }

//        TODO("Not yet implemented")
    }

    /**
     * This contract only implements one command, Create.
     */
    interface Commands : CommandData {
        class Buy : Commands
        class Transfer: Commands
        class Exit: Commands
    }
}