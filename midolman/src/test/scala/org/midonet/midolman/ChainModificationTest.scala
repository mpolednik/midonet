/*
 * Copyright 2015 Midokura SARL
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.midonet.midolman

import java.util.UUID

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

import org.midonet.cluster.data.{Bridge => ClusterBridge, Rule, Chain}
import org.midonet.cluster.data.ports.BridgePort
import org.midonet.midolman.PacketWorkflow.Drop
import org.midonet.midolman.rules.RuleResult
import org.midonet.midolman.topology.VirtualTopologyActor
import org.midonet.midolman.util.MidolmanSpec
import org.midonet.packets._
import org.midonet.packets.util.PacketBuilder._

@RunWith(classOf[JUnitRunner])
class ChainModificationTest extends MidolmanSpec {
    var bridge: ClusterBridge = _
    var inPort: BridgePort = _
    var outPort: BridgePort = _
    var chain: Chain = _
    var chainRules = List[UUID]()

    registerActors(VirtualTopologyActor -> (() => new VirtualTopologyActor))

    override def beforeTest(): Unit = {
        bridge = newBridge("bridge")
        inPort = newBridgePort(bridge)
        outPort = newBridgePort(bridge)

        materializePort(outPort, hostId, "outPort")

        chain = newInboundChainOnBridge("brInFilter", bridge)
        var r: Rule[_,_] = null
        /*
         * Chain config:
         *   0: tcp dst port 80 => ACCEPT
         *   1: tcp src port 9009 => DROP
         *   2: tcp src port 3456 => ACCEPT
         *   3: tcp dst port 81 => DROP
         */
        r = newTcpDstRuleOnChain(chain, 1, 81, RuleResult.Action.DROP)
        chainRules = r.getId :: chainRules

        val tcpCond2 = newCondition(nwProto = Some(TCP.PROTOCOL_NUMBER),
                                    tpSrc = Some(3456))
        r = newLiteralRuleOnChain(chain, 1, tcpCond2, RuleResult.Action.ACCEPT)
        chainRules = r.getId :: chainRules

        val tcpCond3 = newCondition(nwProto = Some(TCP.PROTOCOL_NUMBER),
                                    tpSrc = Some(9009))
        r = newLiteralRuleOnChain(chain, 1, tcpCond3, RuleResult.Action.DROP)
        chainRules = r.getId :: chainRules

        r = newTcpDstRuleOnChain(chain, 1, 80, RuleResult.Action.ACCEPT)
        chainRules = r.getId :: chainRules

        fetchTopology(bridge, inPort, outPort, chain)
    }

    feature ("Rules in a chain can be deleted") {
        scenario ("Middle rule is deleted") {
            simResultFor(9009, 22) should be (Drop)
            simResultFor(9009, 80) should not be Drop
            simResultFor(3456, 81) should not be Drop

            deleteRule(chainRules.apply(0))

            simResultFor(9009, 80) should be (Drop)
            simResultFor(3456, 81) should not be Drop

            deleteRule(chainRules.apply(1))

            simResultFor(9009, 22) should not be Drop
            simResultFor(9009, 80) should not be Drop
            simResultFor(3456, 81) should not be Drop
        }

        scenario ("Last rule is deleted") {
            simResultFor(3000, 81) should be (Drop)
            deleteRule(chainRules.apply(3))
            simResultFor(3000, 81) should not be Drop
        }
    }

    feature ("Rules in a chain can be inserted") {
        scenario ("Middle rule is inserted") {
            simResultFor(3456, 80) should not be Drop

            val tcpCond = newCondition(nwProto = Some(TCP.PROTOCOL_NUMBER),
                                       tpSrc = Some(3456))
            newLiteralRuleOnChain(chain, 1, tcpCond, RuleResult.Action.DROP)

            simResultFor(3456, 80) should be (Drop)
            simResultFor(6543, 80) should not be Drop
        }

        scenario ("Last rule is inserted") {
            simResultFor(7000, 22) should not be Drop

            val tcpCond = newCondition(nwProto = Some(TCP.PROTOCOL_NUMBER),
                                   tpSrc = Some(7000))
            newLiteralRuleOnChain(chain, 5, tcpCond, RuleResult.Action.DROP)

            simResultFor(7000, 22) should be (Drop)
        }
    }

    def simResultFor(srcPort: Short, dstPort: Short) =
        simulate(packetContextFor(tcpPacket(srcPort, dstPort), inPort.getId))._1

    def tcpPacket(srcPort: Short, dstPort: Short) =
        { eth src MAC.random() dst MAC.random() } <<
            { ip4 src IPv4Addr.random dst IPv4Addr.random } <<
                { tcp src srcPort dst dstPort }
}
