/*
 * Copyright 2011 Midokura KK
 * Copyright 2012 Midokura PTE LTD.
 */
package com.midokura.midolman.mgmt.data;

import com.midokura.midolman.mgmt.data.dao.*;
import com.midokura.midolman.mgmt.data.dao.MetricDao;
import com.midokura.midolman.state.StateAccessException;

/**
 * ZooKeeper DAO factory interface.
 */
public interface DaoFactory {

    /**
     * Get Application DAO
     *
     * @return ApplicationDao object
     * @throws StateAccessException
     *             Data access error.
     */
    ApplicationDao getApplicationDao() throws StateAccessException;

    /**
     * Get ad route DAO
     *
     * @return AdRouteDao object
     * @throws StateAccessException
     *             Data access error.
     */
    AdRouteDao getAdRouteDao() throws StateAccessException;

    /**
     * Get BGP DAO
     *
     * @return BgpDao object
     * @throws StateAccessException
     *             Data access error.
     */
    BgpDao getBgpDao() throws StateAccessException;

    /**
     * Get bridge DAO
     *
     * @return BridgeDao object
     * @throws StateAccessException
     *             Data access error.
     */
    BridgeDao getBridgeDao() throws StateAccessException;

    /**
     * Get chain DAO
     *
     * @return ChainDao object
     * @throws StateAccessException
     *             Data access error.
     */
    ChainDao getChainDao() throws StateAccessException;

    /**
     * Get host DAO
     *
     * @return HostDao object
     * @throws StateAccessException
     *              Data access error.
     */
    HostDao getHostDao() throws StateAccessException;

    /**
     * Get port DAO
     *
     * @return PortDao object
     * @throws StateAccessException
     *             Data access error.
     */
    PortDao getPortDao() throws StateAccessException;

    /**
     * Get route DAO
     *
     * @return RouteDao object
     * @throws StateAccessException
     *             Data access error.
     */
    RouteDao getRouteDao() throws StateAccessException;

    /**
     * Get router DAO
     *
     * @return RouterDao object
     * @throws StateAccessException
     *             Data access error.
     */
    RouterDao getRouterDao() throws StateAccessException;

    /**
     * Get rule DAO
     *
     * @return RuleDao object
     * @throws StateAccessException
     *             Data access error.
     */
    RuleDao getRuleDao() throws StateAccessException;

    /**
     * Get tenant DAO
     *
     * @return TenantDao object
     * @throws StateAccessException
     *             Data access error.
     */
    TenantDao getTenantDao() throws StateAccessException;

    /**
     * Get VIF DAO
     *
     * @return VifDao object
     * @throws StateAccessException
     *             Data access error.
     */
    VifDao getVifDao() throws StateAccessException;

    /**
     * Get VPN DAO
     *
     * @return VpnDao object
     * @throws StateAccessException
     *             Data access error.
     */
    VpnDao getVpnDao() throws StateAccessException;

    /**
     * Get DHCP DAO
     *
     * @return DhcpDao object
     * @throws StateAccessException
     *             Data access error.
     */
    DhcpDao getDhcpDao() throws StateAccessException;

    PortGroupDao getPortGroupDao() throws StateAccessException;
    /**
     * Get Metric DAO
     *
     * @return MetricDao object
     * @throws StateAccessException
     *             Data access error.
     */
    MetricDao getMetricDao() throws StateAccessException;
}
