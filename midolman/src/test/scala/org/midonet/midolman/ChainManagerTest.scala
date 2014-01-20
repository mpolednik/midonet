/*
 * Copyright (c) 2013 Midokura Europe SARL, All Rights Reserved.
 */
package org.midonet.midolman

import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner
import org.midonet.midolman.FlowController.InvalidateFlowsByTag
import org.midonet.midolman.rules.{LiteralRule, Condition, JumpRule, Rule}
import org.midonet.midolman.rules.RuleResult.Action
import org.midonet.midolman.services.MessageAccumulator
import org.midonet.midolman.simulation.{Chain, CustomMatchers}
import org.midonet.midolman.topology.{FlowTagger, VirtualTopologyActor}
import org.midonet.midolman.topology.VirtualTopologyActor.ChainRequest
import java.util.UUID
import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.ActorSystem

@RunWith(classOf[JUnitRunner])
class ChainManagerTest extends TestKit(ActorSystem("ChainManagerTest"))
        with FeatureSpecLike
        with CustomMatchers
        with GivenWhenThen
        with ImplicitSender
        with Matchers
        with MidolmanServices
        with MockMidolmanActors
        with OneInstancePerTest
        with VirtualConfigurationBuilders {

    var vta: TestableVTA = null

    protected override def registerActors =
        List(VirtualTopologyActor -> (() => new TestableVTA))

    protected override def beforeTest() {
        vta = VirtualTopologyActor.as[TestableVTA]
    }

    feature("ChainManager handles chain's rules") {
        scenario("Load chain with two rules") {
            Given("a chain with two rules")
            val chain = createChain("chain1")
            newTcpDstRuleOnChain(chain, 1, 80, Action.DROP)

            When("the VTA receives a request for it")
            vta.self ! ChainRequest(chain.getId)

            Then("it should return the requested chain, including the rule")
            val c = expectMsgType[Chain]
            c.id shouldEqual chain.getId
            c.getRules.size shouldBe 1
            checkTcpDstRule(c.getRules.get(0), 80, Action.DROP)
        }

        scenario("Receive update when a rule is added") {
            Given("a chain with one rule")
            val chain = createChain("chain1")
            newTcpDstRuleOnChain(chain, 1, 80, Action.DROP)

            When("the VTA receives a subscription request for it")
            vta.self ! ChainRequest(chain.getId, update = true)

            And("it returns the first version of the chain")
            expectMsgType[Chain]
            vta.getAndClear()

            And("a new rule is added")
            newTcpDstRuleOnChain(chain, 2, 81, Action.ACCEPT)

            Then("the VTA should send an update")
            val c = expectMsgType[Chain]
            c.id shouldEqual chain.getId
            c.getRules.size shouldBe 2
            checkTcpDstRule(c.getRules.get(1), 81, Action.ACCEPT)

            And("the VTA should receive a flow invalidation")
            vta.getAndClear().contains(flowInvalidationMsg(c.id)) shouldBe true
        }
    }

    feature("ChainManager loads target chains for jump rules") {
        scenario("Load chain with a jump to another chain") {
            Given("a chain with a jump to another chain")
            val chain1 = createChain("chain1")
            val chain2 = createChain("chain2")
            newJumpRuleOnChain(chain1, 1, new Condition(), chain2.getId)

            When("the VTA receives a request for it")
            vta.self ! ChainRequest(chain1.getId)

            Then("the VTA should return the first chain, which " +
                 "should have a reference to the second chain")
            val c = expectMsgType[Chain]
            c.id shouldEqual chain1.getId
            c.getRules.size shouldBe 1
            checkJumpRule(c.getRules.get(0), chain2.getId)
            c.getJumpTarget(chain2.getId) shouldBe a [Chain]
        }

        scenario("Add a second jump rule to a chain") {
            Given("A chain with a jump to a second chain")
            val chain1 = createChain("chain1")
            val chain2 = createChain("chain2")
            newJumpRuleOnChain(chain1, 1, new Condition(), chain2.getId)

            When("the VTA receives a request for it")
            vta.self ! ChainRequest(chain1.getId, true)

            And("it returns the first version of the first chain")
            expectMsgType[Chain]
            vta.getAndClear()

            And("a second jump rule to a third chain is added")
            val chain3 = createChain("chain3")
            newJumpRuleOnChain(chain1, 2, new Condition(), chain3.getId)

            Then("the VTA should send an update with both jumps")
            val c1 = expectMsgType[Chain]
            c1.id shouldEqual chain1.getId
            c1.getRules.size shouldBe 2
            checkJumpRule(c1.getRules.get(0), chain2.getId)
            checkJumpRule(c1.getRules.get(1), chain3.getId)
            c1.getJumpTarget(chain2.getId) should not be null
            c1.getJumpTarget(chain3.getId) should not be null

            And("the VTA should receive a flow invalidation for the first chain")
            vta.getAndClear().contains(flowInvalidationMsg(c1.id)) shouldBe true
        }

        scenario("Add a jump to a third chain on the second chain") {
            Given("a chain with a jump to a second chain")
            val chain1 = createChain("chain1")
            val chain2 = createChain("chain2")
            newJumpRuleOnChain(chain1, 1, new Condition(), chain2.getId)

            When("the VTA receives a request for it")
            vta.self ! ChainRequest(chain1.getId, true)

            And("it returns the first version of the chain")
            expectMsgType[Chain]
            vta.getAndClear()

            And("a jump to a third chain is added to the second chain")
            val chain3 = createChain("chain3")
            newJumpRuleOnChain(chain2, 1, new Condition(), chain3.getId)

            Then("the VTA should send an update with " +
                 "all three chains connected by jumps")
            val c1 = expectMsgType[Chain]
            c1.id shouldEqual chain1.getId
            c1.getRules.size shouldBe 1
            checkJumpRule(c1.getRules.get(0), chain2.getId)

            val c2 = c1.getJumpTarget(chain2.getId)
            c2 should not be null
            c2.id shouldEqual chain2.getId
            c2.getRules.size shouldBe 1
            checkJumpRule(c2.getRules.get(0), chain3.getId)
            c2.getJumpTarget(chain3.getId) should not be null

            And("the VTA should receive flow invalidations " +
                "for the first two chains")
            val msgs = vta.getAndClear()
            msgs.contains(flowInvalidationMsg(c1.id)) shouldBe true
            msgs.contains(flowInvalidationMsg(c2.id)) shouldBe true
        }

        scenario("Add a rule to a jump target chain") {
            Given("a chain with a jump to a second chain" +
                  "with a jump to a third chain")
            val chain1 = createChain("chain1")
            val chain2 = createChain("chain2")
            val chain3 = createChain("chain3")
            newJumpRuleOnChain(chain1, 1, new Condition(), chain2.getId)
            newJumpRuleOnChain(chain2, 1, new Condition(), chain3.getId)

            When("the VTA receives a request for it")
            vta.self ! ChainRequest(chain1.getId, true)

            And("it returns the first version of the first chain")
            expectMsgType[Chain]

            And("a rule is added to the third chain")
            newTcpDstRuleOnChain(chain3, 1, 80, Action.DROP)

            Then("the VTA should send an update with all three chains " +
                 "connected by jumps and the new rule in the third chain")
            val c1 = expectMsgType[Chain]
            val c2 = c1.getJumpTarget(chain2.getId)
            val c3 = c2.getJumpTarget(chain3.getId)
            c3.getRules.size shouldBe 1
            checkTcpDstRule(c3.getRules.get(0), 80, Action.DROP)

            And("the VTA should receive flow invalidations for all three chains")
            val msgs = vta.getAndClear()
            msgs.contains(flowInvalidationMsg(c1.id))
            msgs.contains(flowInvalidationMsg(c2.id))
            msgs.contains(flowInvalidationMsg(c3.id))
        }
    }

    feature("ChainManager loads IPAddrGroups associated with its chain") {
        scenario("Load chain with a rule with one IPAddrGroup") {
            Given("a chain with a rule with one IPAddrGroup")
            val ipAddrGroup = createIpAddrGroup()
            val addr = "10.0.1.1"
            addAddrToIpAddrGroup(ipAddrGroup.getId, addr)

            val chain = createChain("chain1")
            newIpAddrGroupRuleOnChain(chain, 1, Action.DROP,
                                      Some(ipAddrGroup.getId), None)

            When("the VTA receives a request for it")
            vta.self ! ChainRequest(chain.getId)

            Then("It returns the chain with the IPAddrGroup")
            val c = expectMsgType[Chain]
            c.getRules.size shouldBe 1
            checkIpAddrGroupRule(c.getRules.get(0), Action.DROP,
                                 ipAddrGroup.getId, Set(addr), null, null)
        }

        scenario("Add an address to an IPAddrGroup") {
            Given("A chain with a rule with one IPAddrGroup")
            val ipAddrGroup = createIpAddrGroup()
            val addr1 = "10.0.1.1"
            addAddrToIpAddrGroup(ipAddrGroup.getId, addr1)

            val chain = createChain("chain1")
            newIpAddrGroupRuleOnChain(chain, 1, Action.DROP,
                Some(ipAddrGroup.getId), None)

            When("the VTA receives a request for it")
            vta.self ! ChainRequest(chain.getId, true)

            And("it returns the first version of the chain")
            expectMsgType[Chain]
            vta.getAndClear()

            And("a second address is added to the IPAddrGroup")
            val addr2 = "10.0.1.2"
            addAddrToIpAddrGroup(ipAddrGroup.getId, addr2)

            Then("the VTA should send an update")
            val c = expectMsgType[Chain]
            c.getRules.size shouldBe 1
            checkIpAddrGroupRule(c.getRules.get(0), Action.DROP,
                                 ipAddrGroup.getId, Set(addr1, addr2),
                                 null, null)

            And("the VTA should receive a flow invalidation for the chain")
            vta.getAndClear().contains(flowInvalidationMsg(c.id)) shouldBe true
        }

        scenario("Remove an address from an IPAddrGroup") {
            Given("A chain with a rule with one IPAddrGroup with two rules")
            val ipAddrGroup = createIpAddrGroup()
            val addr1 = "10.0.1.1"
            val addr2 = "10.0.1.2"
            addAddrToIpAddrGroup(ipAddrGroup.getId, addr1)
            addAddrToIpAddrGroup(ipAddrGroup.getId, addr2)

            val chain = createChain("chain1")
            newIpAddrGroupRuleOnChain(chain, 1, Action.DROP,
                None, Some(ipAddrGroup.getId))

            When("the VTA receives a request for it")
            vta.self ! ChainRequest(chain.getId, true)

            And("it returns the first version of the chain")
            val c1 = expectMsgType[Chain]
            checkIpAddrGroupRule(c1.getRules.get(0), Action.DROP, null, null,
                                 ipAddrGroup.getId, Set(addr1, addr2))
            vta.getAndClear()

            And("an address is removed from the IPAddrGroup")
            removeAddrFromIpAddrGroup(ipAddrGroup.getId, addr1)

            Then("the VTA should send an update")
            val c2 = expectMsgType[Chain]
            c2.id shouldEqual chain.getId
            checkIpAddrGroupRule(c2.getRules.get(0), Action.DROP, null, null,
                                 ipAddrGroup.getId, Set(addr2))

            And("the VTA should receive a flow invalidation for the chain")
            vta.getAndClear().contains(flowInvalidationMsg(c2.id)) shouldBe true
        }
    }

    private def checkIpAddrGroupRule(r: Rule, action: Action,
                                     dstId: UUID, dstAddrs: Set[String],
                                     srcId: UUID, srcAddrs: Set[String]) {
        r shouldBe a [LiteralRule]
        r.action shouldBe action
        val c = r.getCondition
        if (dstId != null) {
            c.ipAddrGroupIdDst shouldEqual dstId
            c.ipAddrGroupDst should not be null
            c.ipAddrGroupDst.id shouldEqual dstId
            c.ipAddrGroupDst.addrs.map(_.toString) shouldEqual dstAddrs
        } else {
            c.ipAddrGroupDst shouldBe null
        }

        if (srcId != null) {
            c.ipAddrGroupIdSrc shouldEqual srcId
            c.ipAddrGroupSrc should not be null
            c.ipAddrGroupSrc.id shouldEqual srcId
            c.ipAddrGroupSrc.addrs.map(_.toString) shouldEqual srcAddrs
        } else {
            c.ipAddrGroupSrc shouldBe null
        }
    }


    private def checkTcpDstRule(r: Rule, tpDst: Int, action: Action) {
        r shouldBe a [LiteralRule]
        val range = r.getCondition.tpDst
        range should not be null
        range.start shouldEqual tpDst
        range.end shouldEqual tpDst
        r.action shouldBe action
    }

    private def checkJumpRule(r: Rule, jumpToId: UUID) {
        r shouldBe a [JumpRule]
        val jr1 = r.asInstanceOf[JumpRule]
        jr1.jumpToChainID shouldEqual jumpToId
    }

    def flowInvalidationMsg(id: UUID) =
        InvalidateFlowsByTag(FlowTagger.invalidateFlowsByDevice(id))
}

class TestableVTA extends VirtualTopologyActor with MessageAccumulator {

}


