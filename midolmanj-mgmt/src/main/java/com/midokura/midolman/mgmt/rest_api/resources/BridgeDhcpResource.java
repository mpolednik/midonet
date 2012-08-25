/*
 * Copyright 2012 Midokura Europe SARL
 * Copyright 2012 Midokura PTE LTD.
 */

package com.midokura.midolman.mgmt.rest_api.resources;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.servlet.RequestScoped;
import com.midokura.midolman.mgmt.auth.AuthAction;
import com.midokura.midolman.mgmt.auth.AuthRole;
import com.midokura.midolman.mgmt.auth.authorizer.Authorizer;
import com.midokura.midolman.mgmt.auth.authorizer.BridgeAuthorizer;
import com.midokura.midolman.mgmt.data.dao.DhcpDao;
import com.midokura.midolman.mgmt.data.dto.DhcpSubnet;
import com.midokura.midolman.mgmt.data.dto.RelativeUriResource;
import com.midokura.midolman.mgmt.http.VendorMediaType;
import com.midokura.midolman.mgmt.jaxrs.ForbiddenHttpException;
import com.midokura.midolman.mgmt.jaxrs.ResourceUriBuilder;
import com.midokura.midolman.state.NoStatePathException;
import com.midokura.midolman.state.StateAccessException;
import com.midokura.packets.IntIPv4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RequestScoped
public class BridgeDhcpResource {

    private final static Logger log = LoggerFactory
            .getLogger(BridgeDhcpResource.class);

    private final UUID bridgeId;
    private final SecurityContext context;
    private final UriInfo uriInfo;
    private final Authorizer authorizer;
    private final DhcpDao dao;
    private final ResourceFactory factory;

    @Inject
    public BridgeDhcpResource(UriInfo uriInfo,
                              SecurityContext context,
                              BridgeAuthorizer authorizer,
                              DhcpDao dao,
                              ResourceFactory factory,
                              @Assisted UUID bridgeId) {
        this.context = context;
        this.uriInfo = uriInfo;
        this.authorizer = authorizer;
        this.dao = dao;
        this.factory = factory;
        this.bridgeId = bridgeId;
    }

    /**
     * Host Assignments resource locator for dhcp.
     *
     * @returns DhcpHostsResource object to handle sub-resource requests.
     */
    @Path("/{subnetAddr}" + ResourceUriBuilder.DHCP_HOSTS)
    public DhcpHostsResource getDhcpAssignmentsResource(
            @PathParam("subnetAddr") IntIPv4 subnetAddr) {
        return factory.getDhcpAssignmentsResource(bridgeId, subnetAddr);
    }

    /**
     * Handler for creating a DHCP subnet configuration.
     *
     * @param subnet
     *            DHCP subnet configuration object.
     * @throws StateAccessException
     *             Data access error.
     * @returns Response object with 201 status code set if successful.
     */
    @POST
    @RolesAllowed({AuthRole.ADMIN, AuthRole.TENANT_ADMIN})
    @Consumes({ VendorMediaType.APPLICATION_DHCP_SUBNET_JSON,
            MediaType.APPLICATION_JSON })
    public Response create(DhcpSubnet subnet) throws StateAccessException {

        if (!authorizer.authorize(context, AuthAction.WRITE, bridgeId)) {
            throw new ForbiddenHttpException(
                    "Not authorized to configure DHCP for this bridge.");
        }

        dao.createSubnet(bridgeId, subnet);
        URI dhcpsUri = ResourceUriBuilder.getBridgeDhcps(uriInfo.getBaseUri(),
                bridgeId);
        return Response.created(
                ResourceUriBuilder.getBridgeDhcp(
                        dhcpsUri,
                        IntIPv4.fromString(subnet.getSubnetPrefix(),
                                subnet.getSubnetLength()))).build();
    }

    /**
     * Handler to updating a host assignment.
     *
     * @param subnetAddr
     *            Identifier of the DHCP subnet configuration.
     * @param subnet
     *            DHCP subnet configuration object.
     * @throws StateAccessException
     *             Data access error.
     */
    @PUT
    @RolesAllowed({AuthRole.ADMIN, AuthRole.TENANT_ADMIN})
    @Path("/{subnetAddr}")
    @Consumes({ VendorMediaType.APPLICATION_DHCP_SUBNET_JSON,
            MediaType.APPLICATION_JSON })
    public Response update(@PathParam("subnetAddr") IntIPv4 subnetAddr,
            DhcpSubnet subnet)
            throws StateAccessException {

        if (!authorizer.authorize(context, AuthAction.WRITE, bridgeId)) {
            throw new ForbiddenHttpException(
                    "Not authorized to update this bridge's dhcp config.");
        }

        // Make sure that the DhcpSubnet has the same IP address as the URI.
        subnet.setSubnetPrefix(subnetAddr.toUnicastString());
        subnet.setSubnetLength(subnetAddr.getMaskLength());
        dao.updateSubnet(bridgeId, subnet);
        return Response.ok().build();
    }

    /**
     * Handler to getting a DHCP subnet configuration.
     *
     * @param subnetAddr
     *            Subnet IP from the request.
     * @throws StateAccessException
     *             Data access error.
     * @return A Bridge object.
     */
    @GET
    @PermitAll
    @Path("/{subnetAddr}")
    @Produces({ VendorMediaType.APPLICATION_DHCP_SUBNET_JSON,
            MediaType.APPLICATION_JSON })
    public DhcpSubnet get(@PathParam("subnetAddr") IntIPv4 subnetAddr)
            throws StateAccessException {

        if (!authorizer.authorize(context, AuthAction.READ, bridgeId)) {
            throw new ForbiddenHttpException(
                    "Not authorized to view this bridge's dhcp config.");
        }

        DhcpSubnet subnet = dao.getSubnet(bridgeId, subnetAddr);
        if (null != subnet)
            subnet.setParentUri(ResourceUriBuilder.getBridgeDhcps(
                    uriInfo.getBaseUri(), bridgeId));
        return subnet;
    }

    /**
     * Handler to deleting a DHCP subnet configuration.
     *
     * @throws com.midokura.midolman.state.StateAccessException
     *             Data access error.
     */
    @DELETE
    @RolesAllowed({AuthRole.ADMIN, AuthRole.TENANT_ADMIN})
    @Path("/{subnetAddr}")
    public void delete(@PathParam("subnetAddr") IntIPv4 subnetAddr)
            throws StateAccessException {

        if (!authorizer.authorize(context, AuthAction.WRITE, bridgeId)) {
            throw new ForbiddenHttpException(
                    "Not authorized to delete dhcp configuration of "
                            + "this bridge.");
        }

        try {
            dao.deleteSubnet(bridgeId, subnetAddr);
        } catch (NoStatePathException e) {
            // Deleting a non-existing record is OK.
            log.warn("The resource does not exist", e);
        }
    }

    /**
     * Handler to list DHCP subnet configurations.
     *
     * @throws StateAccessException
     *             Data access error.
     * @return A list of DhcpSubnet objects.
     */
    @GET
    @PermitAll
    @Produces({ VendorMediaType.APPLICATION_DHCP_SUBNET_COLLECTION_JSON })
    public List<DhcpSubnet> list() throws StateAccessException {

        if (!authorizer.authorize(context, AuthAction.READ, bridgeId)) {
            throw new ForbiddenHttpException(
                    "Not authorized to view DHCP config of this bridge.");
        }

        List<DhcpSubnet> subnets = dao.getSubnets(bridgeId);
        if (subnets != null) {
            URI dhcpsUri = ResourceUriBuilder.getBridgeDhcps(
                    uriInfo.getBaseUri(), bridgeId);
            for (RelativeUriResource resource : subnets) {
                resource.setParentUri(dhcpsUri);
            }
        }
        return subnets;
    }

}
