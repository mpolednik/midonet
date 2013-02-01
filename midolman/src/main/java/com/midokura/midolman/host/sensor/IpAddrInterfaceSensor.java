/*
 * Copyright 2012 Midokura Pte. Ltd.
 */

package com.midokura.midolman.host.sensor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.midokura.midolman.host.interfaces.InterfaceDescription;
import com.midokura.util.process.ProcessHelper;
import static com.midokura.midolman.host.interfaces.InterfaceDescription.Endpoint;
import static com.midokura.midolman.host.interfaces.InterfaceDescription.Type;

public class IpAddrInterfaceSensor implements InterfaceSensor {

    // 18839: invalTap0: <BROADCAST,NOARP,UP,LOWER_UP> mtu 1500 qdisc pfifo_fast state UP qlen 500
    public static final Pattern START_INTERFACE =
        Pattern.compile("^\\d+: ([^:]+): <([^>]+)>.*mtu (\\d+).*state ([A-Z]+).*$");

    //     link/ether aa:4b:d7:1d:24:66 brd ff:ff:ff:ff:ff:ff
    public static final Pattern LINK_ADDR_PATTERN =
        Pattern.compile("^\\s*link/(loopback|ether) ([0-9a-f]{2}(?::[0-9a-f]{2}){5}) brd [0-9a-f]{2}(?::[0-9a-f]{2}){5}$");

    //      inet6 fe80::b02a:88ff:feab:9b54/64 scope link
    public static final Pattern INET6_ADDR_PATTERN =
        Pattern.compile("^\\s*inet6 ([0-9a-f:]+)/(\\d+) .*$");

    //      inet 172.16.16.16/16 brd 172.16.255.255 scope global eth0:1
    public static final Pattern INET4_ADDR_PATTERN =
        Pattern.compile("^\\s*inet ([0-9]+(?:\\.[0-9]+){3})/(\\d+) .*$");


    private final static Logger log =
        LoggerFactory.getLogger(IpAddrInterfaceSensor.class);

    ///////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////
    @Override
    public List<InterfaceDescription> updateInterfaceData(List<InterfaceDescription> interfaces) {
        if (interfaces == null) {
            return Collections.emptyList();
        }

        return parseInterfaces(getInterfacesOutput());
    }

    ///////////////////////////////////////////////////////////////////////////
    // Protected methods
    ///////////////////////////////////////////////////////////////////////////
    protected List<String> getInterfacesOutput() {
        return ProcessHelper.executeCommandLine("ip addr").consoleOutput;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////
    private List<InterfaceDescription> parseInterfaces(List<String> interfacesOutput) {

        List<InterfaceDescription> interfaces = new ArrayList<InterfaceDescription>();

        InterfaceDescription currentInterface = null;
        for (String line : interfacesOutput) {
            InterfaceDescription newInterface;

            newInterface = tryNewInterface(line);

            if (newInterface != null) {
                currentInterface = newInterface;
                interfaces.add(currentInterface);
            } else {
                parseInterfaceLine(line, currentInterface);
            }
        }

        return interfaces;
    }

    private InterfaceDescription tryNewInterface(String line) {

        Matcher newInterfaceMatcher = START_INTERFACE.matcher(line);
        if ( newInterfaceMatcher.matches() ) {
            String name = newInterfaceMatcher.group(1);
            String[] statusFlags = newInterfaceMatcher.group(2).split(",");
            String mtu = newInterfaceMatcher.group(3);
            String state = newInterfaceMatcher.group(4);

            InterfaceDescription interfaceDescription = new InterfaceDescription(name);

            interfaceDescription.setMtu(Integer.parseInt(mtu));

            if (interfaceDescription.getName().equals("lo") ) {
                interfaceDescription.setEndpoint(Endpoint.LOCALHOST);
                interfaceDescription.setType(Type.PHYS);
            }

            if (state.equals("UP") || hasUp(statusFlags)) {
                interfaceDescription.setUp(true);
                interfaceDescription.setHasLink(true);
            } else {
                interfaceDescription.setUp(false);
                interfaceDescription.setHasLink(false);
            }

            return interfaceDescription;
        }

        return null;
    }

    private void parseInterfaceLine(String line, InterfaceDescription interfaceDescription) {
        if (interfaceDescription == null) {
            return;
        }

        Matcher matcher;

        matcher = LINK_ADDR_PATTERN.matcher(line);
        if ( matcher.matches() ) {
            interfaceDescription.setMac(matcher.group(2));
            if ( matcher.group(1).equals("loopback")) {
                interfaceDescription.setEndpoint(Endpoint.LOCALHOST);
                interfaceDescription.setType(Type.PHYS);
            }
            return;
        }

        matcher = INET6_ADDR_PATTERN.matcher(line);
        if ( matcher.matches() ) {
            interfaceDescription.setInetAddress(matcher.group(1));
            return;
        }

        matcher = INET4_ADDR_PATTERN.matcher(line);
        if ( matcher.matches() ) {
            interfaceDescription.setInetAddress(matcher.group(1));
        }
    }

    private boolean hasUp (String[] statusFlags) {
        for (String token : statusFlags) {
             if (token.matches("UP")) {
                 return true;
             }
        }
        return false;
    }
}