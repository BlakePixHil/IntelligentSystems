package edu.swin.hets.controller.gateway

import jade.content.lang.sl.SLCodec
import jade.content.onto.basic.Action
import jade.content.onto.basic.Result
import jade.core.AID
import jade.core.ContainerID
import jade.domain.FIPANames
import jade.domain.JADEAgentManagement.JADEManagementOntology
import jade.domain.JADEAgentManagement.QueryAgentsOnLocation
import jade.lang.acl.ACLMessage
import jade.proto.AchieveREInitiator
import jade.util.leap.ArrayList
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

class AgentRetriever(private val containerID: ContainerID) : AchieveREInitiator(null, null) {
    companion object {
        val logger: Logger = LoggerFactory.getLogger(AgentRetriever::class.java)
    }

    private var agentList: jade.util.leap.List? = null

    fun getAgentListNative(): List<AID> {
        @Suppress("UNCHECKED_CAST")
        return (agentList as ArrayList).toList() as List<AID>
    }

    override fun onStart() {
        super.onStart()

        with(myAgent.contentManager) {
            registerLanguage(SLCodec())
            registerOntology(JADEManagementOntology.getInstance())
        }
    }

    override fun prepareRequests(initialRequest: ACLMessage?): Vector<ACLMessage> {
        logger.info("Preparing Requests")
        val request = ACLMessage(ACLMessage.REQUEST).apply {
            addReceiver(myAgent.ams)
            ontology = JADEManagementOntology.getInstance().name
            language = FIPANames.ContentLanguage.FIPA_SL
        }

        val queryAgentsOnLocation = QueryAgentsOnLocation().apply { location = containerID }
        val action = Action(myAgent.ams, queryAgentsOnLocation)

        myAgent.contentManager.fillContent(request, action)
        return Vector<ACLMessage>(1).apply { add(request) }
    }

    override fun handleInform(inform: ACLMessage?) {
        agentList = (myAgent.contentManager.extractContent(inform) as Result).value as ArrayList
    }
}