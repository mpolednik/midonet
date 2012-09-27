/*
 * Copyright 2012 Midokura Europe SARL
 */

package com.midokura.midolman.simulation

import collection.JavaConversions._
import akka.dispatch.{Promise, ExecutionContext, Future}
import akka.actor.ActorSystem
import akka.util.duration._
import com.midokura.midonet.cluster.DataClient
import com.midokura.packets.{IntIPv4, MAC, DHCP}
import java.util.UUID
import com.midokura.midolman.{FlowController, DatapathController}
import com.midokura.midolman.topology.VirtualTopologyActor
import com.midokura.midonet.cluster.client._
import com.midokura.midolman.topology.VirtualTopologyActor.PortRequest
import compat.Platform


class DhcpImpl(val dataClient: DataClient, val inPortId: UUID,
                  val request: DHCP, val sourceMac: MAC,
                  val cookie: Option[Int])
                 (implicit val ec: ExecutionContext,
                  val actorSystem: ActorSystem) {
    private val log = akka.event.Logging(actorSystem, this.getClass)
    private val datapathController = DatapathController.getRef(actorSystem)
    private val flowController = FlowController.getRef(actorSystem)
    private val virtualTopologyManager = VirtualTopologyActor.getRef(actorSystem)

    private var serverAddr: IntIPv4 = null
    private var serverMac: MAC = null
    private var routerAddr: IntIPv4 = null
    private var yiaddr: IntIPv4 = null

    def handleDHCP : Future[Boolean] = {
        // These fields are decided based on the port configuration.
        // DHCP is handled differently for bridge and router ports.

        // Get the RCU port object and start simulation.
        Coordinator.expiringAsk(
            virtualTopologyManager,
            PortRequest(inPortId, false),
            Platform.currentTime + (10 seconds).toMillis)
            .mapTo[Port[_]]
        .flatMap {
            port: Port[_] => port match {
                case p: ExteriorBridgePort =>
                    dhcpFromBridgePort(p)
                case p: ExteriorRouterPort =>
                    dhcpFromRouterPort(p)
                case _ =>
                    // We don't handle this, but don't throw an error.
                    Promise.successful(false)
            }
        }
    }

    private def dhcpFromBridgePort(port: ExteriorBridgePort): Future[Boolean] =
    {
        // TODO(pino): use an async API
        val subnets = dataClient.dhcpSubnetsGetByBridge(port.deviceID)
        // Look for the DHCP's source MAC in the list of hosts in each subnet
        for (sub <- subnets) {
            val hosts = dataClient.dhcpHostsGetBySubnet(
                port.deviceID, sub.getSubnetAddr)
            for (host <- hosts) {
                if (sourceMac eq host.getMAC) {
                    serverAddr = sub.getServerAddr()
                    // TODO(pino): the server MAC should be in configuration.
                    serverMac = MAC.fromString("02:a8:9c:de:39:27")
                    routerAddr = sub.getDefaultGateway()
                    yiaddr = host.getIp.clone.setMaskLength(
                        sub.getSubnetAddr.getMaskLength)
                    return makeDhcpReply()
                }
            }
        }
        // Couldn't find a static DHCP host assignment for this mac.
        Promise.successful(false)
    }

    private def dhcpFromRouterPort(port: ExteriorRouterPort): Future[Boolean] =
    {
        serverAddr = port.portAddr.clone
        serverMac = port.portMac
        // TODO(pino): the router address should be consistent across ports
        routerAddr = serverAddr
        // XXX TODO(pino): add localNwAddr to cluster.client.Port
        //yiaddr = new IntIPv4(port.localNwAddr, port.nwLength())
        makeDhcpReply
    }

    private def makeDhcpReply(): Future[Boolean] = {
        Promise.successful(true)
    }




    /*if (config instanceof PortDirectory.MaterializedBridgePortConfig) {
            UUID bridgeId = config.device_id
            List<BridgeDhcpZkManager.Subnet> subnets =
                dhcpMgr.getSubnets(bridgeId)
            // Look for the sourceMac in the list of hosts in each subnet.
            boolean foundHost = false
            for (BridgeDhcpZkManager.Subnet sub : subnets) {
                List<BridgeDhcpZkManager.Host> hosts =
                    dhcpMgr.getHosts(bridgeId, sub.getSubnetAddr())
                for (BridgeDhcpZkManager.Host host : hosts) {
                    if (host.getMac().equals(sourceMac)) {
                        foundHost = true;
                        serverAddr = sub.getServerAddr();
                        serverMac = MAC.fromString("02:a8:9c:de:39:27");
                        routerAddr = sub.getDefaultGateway();
                        yiaddr = host.getIp();
                        nwAddr = sub.getSubnetAddr();
                        break;
                    }
                }
                if (foundHost)
                    break;
            }
            if (!foundHost) {
                // Couldn't find a static DHCP host assignment for this mac.
                return false;
            }
        } else if (config instanceof PortDirectory.MaterializedRouterPortConfig) {
            PortDirectory.MaterializedRouterPortConfig rtrPortConfig =
                PortDirectory.MaterializedRouterPortConfig.class.cast(config);
            serverAddr = new IntIPv4(rtrPortConfig.portAddr);
            serverMac = rtrPortConfig.getHwAddr();
            routerAddr = serverAddr;
            yiaddr = new IntIPv4(rtrPortConfig.localNwAddr);
            nwAddr = new IntIPv4(rtrPortConfig.nwAddr, rtrPortConfig.nwLength);
        } else {
            // Unsupported port type.
            return false;
        }
        byte[] chaddr = request.getClientHardwareAddress();
        if (null == chaddr) {
            log.warn("handleDhcpRequest dropping bootrequest with null chaddr");
            return true;
        }
        if (chaddr.length != 6) {
            log.warn("handleDhcpRequest dropping bootrequest with chaddr "
                + "with length {} greater than 6.", chaddr.length);
            return true;
        }
        log.debug("handleDhcpRequest: on port {} bootrequest with chaddr {} "
            + "and ciaddr {}",
            new Object[] { inPortId, Net.convertByteMacToString(chaddr),
                IPv4.fromIPv4Address(request.getClientIPAddress()) });

        // Extract all the options and put them in a map
        Map<Byte, DHCPOption> reqOptions = new HashMap<Byte, DHCPOption>();
        Set<Byte> requestedCodes = new HashSet<Byte>();
        for (DHCPOption opt : request.getOptions()) {
            byte code = opt.getCode();
            reqOptions.put(code, opt);
            log.debug("handleDhcpRequest found option {}:{}", code,
                DHCPOption.codeToName.get(code));
            if (code == DHCPOption.Code.DHCP_TYPE.value()) {
                if (opt.getLength() != 1) {
                    log.warn("handleDhcpRequest dropping bootrequest - "
                        + "dhcp msg type option has bad length or data.");
                    return true;
                }
                log.debug("handleDhcpRequest dhcp msg type {}:{}",
                    opt.getData()[0],
                DHCPOption.msgTypeToName.get(opt.getData()[0]));
            }
            if (code == DHCPOption.Code.PRM_REQ_LIST.value()) {
                if (opt.getLength() <= 0) {
                    log.warn("handleDhcpRequest dropping bootrequest - "
                        + "param request list has bad length");
                    return true;
                }
                for (int i = 0; i < opt.getLength(); i++) {
                    byte c = opt.getData()[i];
                    requestedCodes.add(c);
                    log.debug("handleDhcpRequest client requested option "
                        + "{}:{}", c, DHCPOption.codeToName.get(c));
                }
            }
        }
        DHCPOption typeOpt = reqOptions.get(DHCPOption.Code.DHCP_TYPE.value());
        if (null == typeOpt) {
            log.warn("handleDhcpRequest dropping bootrequest - no dhcp msg "
                + "type found.");
            return true;
        }
        byte msgType = typeOpt.getData()[0];
        boolean drop = true;
        List<DHCPOption> options = new ArrayList<DHCPOption>();
        DHCPOption opt;
        if (DHCPOption.MsgType.DISCOVER.value() == msgType) {
            drop = false;
            // Reply with a dchp OFFER.
            opt = new DHCPOption(DHCPOption.Code.DHCP_TYPE.value(),
                DHCPOption.Code.DHCP_TYPE.length(),
                new byte[] { DHCPOption.MsgType.OFFER.value() });
            options.add(opt);

        } else if (DHCPOption.MsgType.REQUEST.value() == msgType) {
            drop = false;
            // Reply with a dchp ACK.
            opt = new DHCPOption(DHCPOption.Code.DHCP_TYPE.value(),
                DHCPOption.Code.DHCP_TYPE.length(),
                new byte[] { DHCPOption.MsgType.ACK.value() });
            options.add(opt);
            // http://tools.ietf.org/html/rfc2131 Section 3.1, Step 3:
            // "The client broadcasts a DHCPREQUEST message that MUST include
            // the 'server identifier' option to indicate which server is has
            // selected."
            // TODO(pino): figure out why Linux doesn't send us the server id
            // and try re-enabling this code.
            opt = reqOptions.get(DHCPOption.Code.SERVER_ID.value());
            if (null == opt) {
                log.warn("handleDhcpRequest dropping dhcp REQUEST - no " +
                    "server id option found.");
                // TODO(pino): re-enable this.
                //return true;
            } else {
                // The server id should correspond to this port's address.
                int ourServId = serverAddr.getAddress();
                int theirServId = IPv4.toIPv4Address(opt.getData());
                if (ourServId != theirServId) {
                    log.warn("handleDhcpRequest dropping dhcp REQUEST - client "
                        + "chose server {} not us {}",
                        IPv4.fromIPv4Address(theirServId),
                        IPv4.fromIPv4Address(ourServId));
                }
            }
            // The request must contain a requested IP address option.
            opt = reqOptions.get(DHCPOption.Code.REQUESTED_IP.value());
            if (null == opt) {
                log.warn("handleDhcpRequest dropping dhcp REQUEST - no "
                    + "requested ip option found.");
                return true;
            }
            // The requested ip must correspond to the yiaddr in our offer.
            int reqIp = IPv4.toIPv4Address(opt.getData());
            int offeredIp = yiaddr.getAddress();
            // TODO(pino): must keep state and remember the offered ip based
            // on the chaddr or the client id option.
            if (reqIp != offeredIp) {
                log.warn("handleDhcpRequest dropping dhcp REQUEST - the " +
                    "requested ip {} is not the offered yiaddr {}",
                    IPv4.fromIPv4Address(reqIp), IPv4.fromIPv4Address(offeredIp));
                // TODO(pino): send a dhcp NAK reply.
                return true;
            }
        }
        if (drop) {
            log.warn("handleDhcpRequest dropping bootrequest - we don't "
                + "handle msg type {}:{}", msgType,
                DHCPOption.msgTypeToName.get(msgType));
            return true;
        }
        DHCP reply = new DHCP();
        reply.setOpCode(DHCP.OPCODE_REPLY);
        reply.setTransactionId(request.getTransactionId());
        reply.setHardwareAddressLength((byte) 6);
        reply.setHardwareType((byte) ARP.HW_TYPE_ETHERNET);
        reply.setClientHardwareAddress(sourceMac);
        reply.setServerIPAddress(serverAddr.getAddress());
        // TODO(pino): use explicitly assigned address not localNwAddr!!
        reply.setYourIPAddress(yiaddr.getAddress());
        // TODO(pino): do we need to include the DNS option?
        opt = new DHCPOption(DHCPOption.Code.MASK.value(),
            DHCPOption.Code.MASK.length(),
            IPv4.toIPv4AddressBytes(~0 << (32 - nwAddr.getMaskLength())));
        options.add(opt);
        // Generate the broadcast address... this is nwAddr with 1's in the
        // last 32-nwAddrLength bits.
        int mask = ~0 >>> nwAddr.getMaskLength();
        int bcast = mask | nwAddr.getAddress();
        log.debug("handleDhcpRequest setting bcast addr option to {}",
            IPv4.fromIPv4Address(bcast));
        opt = new DHCPOption(
            DHCPOption.Code.BCAST_ADDR.value(),
            DHCPOption.Code.BCAST_ADDR.length(),
            IPv4.toIPv4AddressBytes(bcast));
        options.add(opt);
        opt = new DHCPOption(
            DHCPOption.Code.IP_LEASE_TIME.value(),
            DHCPOption.Code.IP_LEASE_TIME.length(),
            // This is in seconds.  One day is more than enough.
            IPv4.toIPv4AddressBytes((int) TimeUnit.DAYS.toSeconds(1)));
        options.add(opt);
        opt = new DHCPOption(DHCPOption.Code.INTERFACE_MTU.value(),
            DHCPOption.Code.INTERFACE_MTU.length(),
            new byte[] { (byte)(mtu/256), (byte)(mtu%256) });
        options.add(opt);
        if (routerAddr != null) {
            opt = new DHCPOption(
                DHCPOption.Code.ROUTER.value(),
                DHCPOption.Code.ROUTER.length(),
                IPv4.toIPv4AddressBytes(routerAddr.getAddress()));
            options.add(opt);
        }
        // in MidoNet the DHCP server is the same as the router
        opt = new DHCPOption(
            DHCPOption.Code.SERVER_ID.value(),
            DHCPOption.Code.SERVER_ID.length(),
            IPv4.toIPv4AddressBytes(serverAddr.getAddress()));
        options.add(opt);
        // And finally add the END option.
        opt = new DHCPOption(DHCPOption.Code.END.value(),
            DHCPOption.Code.END.length(), null);
        options.add(opt);
        reply.setOptions(options);

        UDP udp = new UDP();
        udp.setSourcePort((short) 67);
        udp.setDestinationPort((short) 68);
        udp.setPayload(reply);

        IPv4 ip = new IPv4();
        ip.setSourceAddress(serverAddr.getAddress());
        ip.setDestinationAddress("255.255.255.255");
        ip.setProtocol(UDP.PROTOCOL_NUMBER);
        ip.setPayload(udp);

        Ethernet eth = new Ethernet();
        eth.setEtherType(IPv4.ETHERTYPE);
        eth.setPayload(ip);

        eth.setSourceMACAddress(serverMac);
        eth.setDestinationMACAddress(sourceMac);

        log.debug("handleDhcpRequest: sending DHCP reply {} to port {}", eth,
            inPortId);

        controller.sendPacket(eth.serialize(), inPortId);
        return true;



        Promise.successful(true)
    }*/

}