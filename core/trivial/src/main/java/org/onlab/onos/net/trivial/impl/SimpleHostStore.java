package org.onlab.onos.net.trivial.impl;

import static org.onlab.onos.net.host.HostEvent.Type.HOST_ADDED;
import static org.onlab.onos.net.host.HostEvent.Type.HOST_MOVED;
import static org.onlab.onos.net.host.HostEvent.Type.HOST_REMOVED;
import static org.onlab.onos.net.host.HostEvent.Type.HOST_UPDATED;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Service;
import org.onlab.onos.net.ConnectPoint;
import org.onlab.onos.net.DefaultHost;
import org.onlab.onos.net.DeviceId;
import org.onlab.onos.net.Host;
import org.onlab.onos.net.HostId;
import org.onlab.onos.net.host.HostDescription;
import org.onlab.onos.net.host.HostEvent;
import org.onlab.onos.net.host.HostStore;
import org.onlab.onos.net.host.PortAddresses;
import org.onlab.onos.net.provider.ProviderId;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.MacAddress;
import org.onlab.packet.VlanId;
import org.slf4j.Logger;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;

/**
 * Manages inventory of end-station hosts using trivial in-memory
 * implementation.
 */
@Component(immediate = true)
@Service
public class SimpleHostStore implements HostStore {

    private final Logger log = getLogger(getClass());

    // Host inventory
    private final Map<HostId, Host> hosts = new ConcurrentHashMap<>();

    // Hosts tracked by their location
    private final Multimap<ConnectPoint, Host> locations = HashMultimap.create();

    private final Map<ConnectPoint, PortAddresses> portAddresses =
            new ConcurrentHashMap<>();

    @Activate
    public void activate() {
        log.info("Started");
    }

    @Deactivate
    public void deactivate() {
        log.info("Stopped");
    }

    @Override
    public HostEvent createOrUpdateHost(ProviderId providerId, HostId hostId,
                                        HostDescription hostDescription) {
        Host host = hosts.get(hostId);
        if (host == null) {
            return createHost(providerId, hostId, hostDescription);
        }
        return updateHost(providerId, host, hostDescription);
    }

    // creates a new host and sends HOST_ADDED
    private HostEvent createHost(ProviderId providerId, HostId hostId,
                                 HostDescription descr) {
        DefaultHost newhost = new DefaultHost(providerId, hostId,
                                              descr.hwAddress(),
                                              descr.vlan(),
                                              descr.location(),
                                              descr.ipAddresses());
        synchronized (this) {
            hosts.put(hostId, newhost);
            locations.put(descr.location(), newhost);
        }
        return new HostEvent(HOST_ADDED, newhost);
    }

    // checks for type of update to host, sends appropriate event
    private HostEvent updateHost(ProviderId providerId, Host host,
                                 HostDescription descr) {
        DefaultHost updated;
        HostEvent event;
        if (!host.location().equals(descr.location())) {
            updated = new DefaultHost(providerId, host.id(),
                                      host.mac(),
                                      host.vlan(),
                                      descr.location(),
                                      host.ipAddresses());
            event = new HostEvent(HOST_MOVED, updated);

        } else if (!(host.ipAddresses().equals(descr.ipAddresses()))) {
            updated = new DefaultHost(providerId, host.id(),
                                      host.mac(),
                                      host.vlan(),
                                      descr.location(),
                                      descr.ipAddresses());
            event = new HostEvent(HOST_UPDATED, updated);
        } else {
            return null;
        }
        synchronized (this) {
            hosts.put(host.id(), updated);
            locations.remove(host.location(), host);
            locations.put(updated.location(), updated);
        }
        return event;
    }

    @Override
    public HostEvent removeHost(HostId hostId) {
        synchronized (this) {
            Host host = hosts.remove(hostId);
            if (host != null) {
                locations.remove((host.location()), host);
                return new HostEvent(HOST_REMOVED, host);
            }
            return null;
        }
    }

    @Override
    public int getHostCount() {
        return hosts.size();
    }

    @Override
    public Iterable<Host> getHosts() {
        return Collections.unmodifiableSet(new HashSet<>(hosts.values()));
    }

    @Override
    public Host getHost(HostId hostId) {
        return hosts.get(hostId);
    }

    @Override
    public Set<Host> getHosts(VlanId vlanId) {
        Set<Host> vlanset = new HashSet<>();
        for (Host h : hosts.values()) {
            if (h.vlan().equals(vlanId)) {
                vlanset.add(h);
            }
        }
        return vlanset;
    }

    @Override
    public Set<Host> getHosts(MacAddress mac) {
        Set<Host> macset = new HashSet<>();
        for (Host h : hosts.values()) {
            if (h.mac().equals(mac)) {
                macset.add(h);
            }
        }
        return macset;
    }

    @Override
    public Set<Host> getHosts(IpPrefix ip) {
        Set<Host> ipset = new HashSet<>();
        for (Host h : hosts.values()) {
            if (h.ipAddresses().contains(ip)) {
                ipset.add(h);
            }
        }
        return ipset;
    }

    @Override
    public Set<Host> getConnectedHosts(ConnectPoint connectPoint) {
        return ImmutableSet.copyOf(locations.get(connectPoint));
    }

    @Override
    public Set<Host> getConnectedHosts(DeviceId deviceId) {
        Set<Host> hostset = new HashSet<>();
        for (ConnectPoint p : locations.keySet()) {
            if (p.deviceId().equals(deviceId)) {
                hostset.addAll(locations.get(p));
            }
        }
        return hostset;
    }

    @Override
    public void updateAddressBindings(PortAddresses addresses) {
        // TODO portAddresses.put(addresses.connectPoint(), addresses);
    }

    @Override
    public void removeAddressBindings(PortAddresses addresses) {
        // TODO Auto-generated method stub

    }

    @Override
    public void clearAddressBindings(ConnectPoint connectPoint) {
        // TODO Auto-generated method stub

    }

    @Override
    public Set<PortAddresses> getAddressBindings() {
        return new HashSet<>(portAddresses.values());
    }

    @Override
    public PortAddresses getAddressBindingsForPort(ConnectPoint connectPoint) {
        return portAddresses.get(connectPoint);
    }

}
