package edu.swin.hets.controller

import edu.swin.hets.controller.gateway.AgentRetriever
import edu.swin.hets.controller.gateway.ContainerListRetriever
import jade.core.*
import jade.util.leap.Properties
import jade.wrapper.ContainerController
import jade.wrapper.gateway.JadeGateway


class JadeController(private val runtime: Runtime) {
    private val profile: Profile = ProfileImpl(true)
    var mainContainer: ContainerController? = null

    init {
        profile.setParameter(Profile.GUI, "true")
    }

    fun start() {
        // TODO: conditional fallback if servers are not able to be connected to
        mainContainer = runtime.createMainContainer(profile)
        JadeGateway.init(null,
                Properties().apply {
                    setProperty(Profile.MAIN_HOST, "localhost")
                    setProperty(Profile.MAIN_PORT, "1099")
                })
    }

    fun configureAgents() {
        TODO("Detect active servers/dev mode, execute fallback")
    }

    fun stop() {
        JadeGateway.shutdown()
    }

    fun getContainers(): List<ContainerID> {
        val clr = ContainerListRetriever()
        JadeGateway.execute(clr)
        return clr.getContainerListNative()
    }

    fun getAgentsAtContainer(containerID: ContainerID): List<AID> {
        val ar = AgentRetriever(containerID)
        JadeGateway.execute(ar)
        return ar.getAgentListNative()
    }

    fun getAllAgents(): List<AID> {
        return getContainers()
                .map { getAgentsAtContainer(it) }
                .toList()
                .flatMap { it }
    }
}