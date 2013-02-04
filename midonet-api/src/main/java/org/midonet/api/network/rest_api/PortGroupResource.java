/*
 * Copyright 2012 Midokura Europe SARL
 */
package org.midonet.api.network.rest_api;

import com.google.inject.Inject;
import com.google.inject.servlet.RequestScoped;
import org.midonet.api.VendorMediaType;
import org.midonet.api.auth.ForbiddenHttpException;
import org.midonet.api.rest_api.*;
import org.midonet.api.ResourceUriBuilder;
import org.midonet.api.auth.AuthAction;
import org.midonet.api.auth.AuthRole;
import org.midonet.api.auth.Authorizer;
import org.midonet.api.network.PortGroup;
import org.midonet.api.network.auth.PortAuthorizer;
import org.midonet.api.network.auth.PortGroupAuthorizer;
import org.midonet.api.rest_api.*;
import org.midonet.midolman.state.InvalidStateOperationException;
import org.midonet.midolman.state.StateAccessException;
import org.midonet.cluster.DataClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Root resource class for port groups.
 */
@RequestScoped
public class PortGroupResource extends AbstractResource {

    private final static Logger log = LoggerFactory
            .getLogger(PortGroupResource.class);

    private final PortGroupAuthorizer authorizer;
    private final PortAuthorizer portAuthorizer;
    private final Validator validator;
    private final DataClient dataClient;
    private final ResourceFactory factory;

    @Inject
    public PortGroupResource(RestApiConfig config, UriInfo uriInfo,
                             SecurityContext context,
                             PortGroupAuthorizer authorizer,
                             PortAuthorizer portAuthorizer,
                             Validator validator, DataClient dataClient,
                             ResourceFactory factory) {
        super(config, uriInfo, context);
        this.authorizer = authorizer;
        this.portAuthorizer = portAuthorizer;
        this.validator = validator;
        this.dataClient = dataClient;
        this.factory = factory;
    }

    /**
     * Handler to deleting a port group.
     *
     * @param id
     *            PortGroup ID from the request.
     * @throws org.midonet.midolman.state.StateAccessException
     *             Data access error.
     */
    @DELETE
    @RolesAllowed({ AuthRole.ADMIN, AuthRole.TENANT_ADMIN })
    @Path("{id}")
    public void delete(@PathParam("id") UUID id)
            throws StateAccessException, InvalidStateOperationException {

        org.midonet.cluster.data.PortGroup portGroupData =
                dataClient.portGroupsGet(id);
        if (portGroupData == null) {
            return;
        }

        if (!authorizer.authorize(context, AuthAction.WRITE, id)) {
            throw new ForbiddenHttpException(
                    "Not authorized to delete this port group.");
        }

        dataClient.portGroupsDelete(id);
    }

    /**
     * Handler to getting a port group.
     *
     * @param id
     *            PortGroup ID from the request.
     * @throws org.midonet.midolman.state.StateAccessException
     *             Data access error.
     * @return A PortGroup object.
     */
    @GET
    @PermitAll
    @Path("{id}")
    @Produces({ VendorMediaType.APPLICATION_PORTGROUP_JSON })
    public PortGroup get(@PathParam("id") UUID id)
            throws StateAccessException {

        if (!authorizer.authorize(context, AuthAction.READ, id)) {
            throw new ForbiddenHttpException(
                    "Not authorized to view this port group.");
        }

        org.midonet.cluster.data.PortGroup portGroupData =
                dataClient.portGroupsGet(id);
        if (portGroupData == null) {
            throw new NotFoundHttpException(
                    "The requested resource was not found.");
        }

        // Convert to the REST API DTO
        PortGroup portGroup = new PortGroup(portGroupData);
        portGroup.setBaseUri(getBaseUri());

        return portGroup;
    }

    /**
     * Handler for creating a tenant port group.
     *
     * @param group
     *            PortGroup object.
     * @throws org.midonet.midolman.state.StateAccessException
     *             Data access error.
     * @returns Response object with 201 status code set if successful.
     */
    @POST
    @RolesAllowed({ AuthRole.ADMIN, AuthRole.TENANT_ADMIN })
    @Consumes({ VendorMediaType.APPLICATION_PORTGROUP_JSON,
                   MediaType.APPLICATION_JSON })
    public Response create(PortGroup group)
            throws StateAccessException, InvalidStateOperationException {

        Set<ConstraintViolation<PortGroup>> violations = validator
                .validate(group, PortGroup.PortGroupCreateGroupSequence.class);
        if (!violations.isEmpty()) {
            throw new BadRequestHttpException(violations);
        }

        if (!Authorizer.isAdminOrOwner(context, group.getTenantId())) {
            throw new ForbiddenHttpException(
                    "Not authorized to add PortGroup to this tenant.");
        }

        UUID id = dataClient.portGroupsCreate(group.toData());
        return Response.created(
                ResourceUriBuilder.getPortGroup(getBaseUri(), id))
                .build();
    }

    @GET
    @Path("/name")
    @PermitAll
    @Produces({ VendorMediaType.APPLICATION_PORTGROUP_JSON,
            MediaType.APPLICATION_JSON })
    public PortGroup getByName(@QueryParam("tenant_id") String tenantId,
                               @QueryParam("name") String name)
            throws StateAccessException{
        if (tenantId == null || name == null) {
            throw new BadRequestHttpException(
                    "Currently tenant_id and name are required for search.");
        }

        org.midonet.cluster.data.PortGroup portGroupData =
                dataClient.portGroupsGetByName(tenantId, name);
        if (portGroupData == null) {
            throw new NotFoundHttpException(
                    "The requested resource was not found.");
        }

        if (!authorizer.authorize(
                context, AuthAction.READ, portGroupData.getId())) {
            throw new ForbiddenHttpException(
                    "Not authorized to view this chain.");
        }

        // Convert to the REST API DTO
        PortGroup portGroup = new PortGroup(portGroupData);
        portGroup.setBaseUri(getBaseUri());
        return portGroup;
    }

    /**
     * Handler to getting a collection of PortGroups.  Port groups can be
     * filtered by tenant or port.
     *
     * @throws org.midonet.midolman.state.StateAccessException
     *             Data access error.
     * @return A list of PortGroup objects.
     */
    @GET
    @PermitAll
    @Produces({ VendorMediaType.APPLICATION_PORTGROUP_COLLECTION_JSON,
            MediaType.APPLICATION_JSON })
    public List<PortGroup> list(@QueryParam("tenant_id") String tenantId,
                                @QueryParam("port_id") UUID portId)
            throws StateAccessException {

        if (tenantId == null && portId == null) {
            throw new BadRequestHttpException(
                    "tenant_id or port_id is required for search.");
        }

        List<org.midonet.cluster.data.PortGroup> portGroupDataList =
                null;

        // Port ID filter is more restrictive so handle that if portId is set.
        if (portId != null) {

            // If portId is provided, check the owner.
            if (!portAuthorizer.authorize(context, AuthAction.READ, portId)) {
                throw new ForbiddenHttpException(
                        "Not authorized to view port groups for this port.");
            }
            portGroupDataList = dataClient.portGroupsFindByPort(portId);

        } else {
            if (!Authorizer.isAdminOrOwner(context, tenantId)) {
                throw new ForbiddenHttpException(
                        "Not authorized to view port group for this tenant.");
            }
            portGroupDataList = dataClient.portGroupsFindByTenant(tenantId);
        }

        List<PortGroup> portGroups = new ArrayList<PortGroup>();
        if (portGroupDataList != null) {
            for (org.midonet.cluster.data.PortGroup portGroupData :
                    portGroupDataList) {
                PortGroup portGroup = new PortGroup(portGroupData);
                portGroup.setBaseUri(getBaseUri());
                portGroups.add(portGroup);
            }
        }
        return portGroups;
    }

    @Path("/{id}" + ResourceUriBuilder.PORTS)
    public PortResource.PortGroupPortResource getPortGroupPortResource(
            @PathParam("id") UUID id) {
        return factory.getPortGroupPortResource(id);
    }

}