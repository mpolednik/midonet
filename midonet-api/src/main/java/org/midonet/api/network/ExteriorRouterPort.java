/*
 * Copyright 2014 Midokura SARL
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
package org.midonet.api.network;

import org.midonet.api.ResourceUriBuilder;
import org.midonet.cluster.Client;
import org.midonet.cluster.data.Port;

import java.net.URI;
import java.util.UUID;

/**
 * Data transfer class for exterior router port.
 */
public class ExteriorRouterPort extends RouterPort implements ExteriorPort {

    /**
     * Constructor
     */
    public ExteriorRouterPort() {
        super();
    }

    /**
     * Constructor
     *
     * @param id
     *            ID of the port
     * @param deviceId
     *            ID of the device
     */
    public ExteriorRouterPort(UUID id, UUID deviceId) {
        super(id, deviceId);
    }

    /**
     * Constructor
     *
     * @param portData
     *            Exterior bridge port data object
     */
    public ExteriorRouterPort(
            org.midonet.cluster.data.ports.RouterPort
                    portData) {
        super(portData);
    }

    @Override
    public org.midonet.cluster.data.ports.RouterPort toData() {
        org.midonet.cluster.data.ports.RouterPort data =
                new org.midonet.cluster.data.ports
                        .RouterPort();
        super.setConfig(data);
        data.setProperty(Port.Property.v1PortType,
                Client.PortType.ExteriorRouter.toString());
        return data;
    }

    @Override
    public String getType() {
        return PortType.EXTERIOR_ROUTER;
    }

    @Override
    public String toString() {
        return super.toString() + ", vifId=" + vifId;
    }

}
