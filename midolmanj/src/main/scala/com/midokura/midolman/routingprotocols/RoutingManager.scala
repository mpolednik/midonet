/*
 * Copyright 2012 Midokura Pte. Ltd.
 */

package com.midokura.midolman.routingprotocols

import akka.actor.{ActorRef, Props, ActorLogging, Actor}
import com.midokura.midonet.cluster.DataClient
import com.google.inject.Inject
import java.util.UUID
import com.midokura.util.functors.Callback2
import com.midokura.midolman.config.MidolmanConfig
import com.midokura.midonet.cluster.client.{Port, ExteriorRouterPort}
import collection.mutable
import com.midokura.midolman.topology.VirtualTopologyActor
import com.midokura.midolman.topology.VirtualTopologyActor.PortRequest
import com.midokura.midolman.Referenceable

object RoutingManager extends Referenceable {
    val Name = "RoutingManager"
}

class RoutingManager extends Actor with ActorLogging {

    @Inject
    var dataClient: DataClient = null
    @Inject
    var config: MidolmanConfig = null

    private var bgpPortIdx = 0

    private val activePorts = mutable.Set[UUID]()
    private val portHandlers = mutable.Map[UUID, ActorRef]()

    private case class LocalPortActive(portID: UUID, active: Boolean)

    val localPortsCB = new Callback2[UUID, java.lang.Boolean]() {
        def call(portID: UUID, active: java.lang.Boolean) {
            self ! LocalPortActive(portID, active)
        }
    }

    override def preStart() {
        super.preStart()
        if (config.getMidolmanBGPEnabled) {
            dataClient.subscribeToLocalActivePorts(localPortsCB)
            bgpPortIdx = config.getMidolmanBGPPortStartIndex
        }
    }

    override def receive = {
        case LocalPortActive(portID, true) =>
            if (!activePorts(portID)) {
                activePorts.add(portID)
                // Request the port configuration
                VirtualTopologyActor.getRef() ! PortRequest(portID, update = false)
            }

        case LocalPortActive(portID, false) =>
            activePorts.remove(portID)
            context.stop(portHandlers(portID))

        case port: ExteriorRouterPort =>
            // Only exterior virtual router ports support BGP.
            // Create a handler if there isn't one and the port is active
            if (activePorts(port.id) && portHandlers(port.id) == null) {
                bgpPortIdx += 1
                portHandlers.put(
                    port.id,
                    context.actorOf(
                        Props(new RoutingHandler(port, bgpPortIdx)),
                        name = port.id.toString))
            }

        case port: Port[_] => log.error("Port type not supported.")

        case _ => log.error("Unknown message.")
    }

}