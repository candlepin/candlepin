/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.pinsetter.tasks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.audit.EventSink;
import org.candlepin.client.CandlepinConnection;
import org.candlepin.client.ConsumerClient;
import org.candlepin.client.OwnerClient;
import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;

import org.apache.commons.httpclient.Credentials;
import org.jboss.resteasy.client.ClientResponse;
import org.junit.Before;
import org.junit.Test;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.impl.JobExecutionContextImpl;
import org.quartz.spi.OperableTrigger;
import org.quartz.spi.TriggerFiredBundle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.ws.rs.core.Response;


/**
 * MigrateOwnerJobTest
 */
public class MigrateOwnerJobTest {

    private OwnerCurator ownerCurator;
    private ConsumerCurator consumerCurator;
    private CandlepinConnection conn;
    private MigrateOwnerJob moj;
    private Config config;
    private PoolCurator poolCurator;
    private EntitlementCurator entCurator;
    private EventSink sink;

    @Before
    public void init() {
        config = new ConfigForTesting();
        ownerCurator = mock(OwnerCurator.class);
        consumerCurator = mock(ConsumerCurator.class);
        conn = mock(CandlepinConnection.class);
        poolCurator = mock(PoolCurator.class);
        entCurator = mock(EntitlementCurator.class);
        sink = mock(EventSink.class);
        moj = new MigrateOwnerJob(conn, config, ownerCurator, poolCurator,
            entCurator, consumerCurator, sink);
    }

    @Test
    public void testMigrateOwner() {
        JobDetail jd = MigrateOwnerJob.migrateOwner("admin",
            "http://foo.example.com/candlepin", false);
        assertNotNull(jd);
        assertNotNull(jd.getJobDataMap());
        assertEquals("admin", jd.getJobDataMap().get("owner_key"));
        assertEquals("http://foo.example.com/candlepin",
            jd.getJobDataMap().get("uri"));
        assertTrue(jd.requestsRecovery());
        assertFalse(jd.isDurable());
        assertEquals(false, jd.getJobDataMap().get("delete"));
    }

    @Test(expected = Exception.class)
    public void nullOwner() {
        MigrateOwnerJob.migrateOwner(null, "http://foo.example.com/candlepin",
            false);
    }

    @Test(expected = BadRequestException.class)
    public void nullUrl() {
        MigrateOwnerJob.migrateOwner("admin", null, false);
    }

    @Test(expected = BadRequestException.class)
    public void invalidUrlFormat() {
        MigrateOwnerJob.migrateOwner("admin", "", false);
    }

    // used by execute tests
    private JobExecutionContext buildContext(JobDataMap map) {
        Scheduler s = mock(Scheduler.class);
        TriggerFiredBundle bundle = mock(TriggerFiredBundle.class);
        JobDetail detail = mock(JobDetail.class);
        OperableTrigger trig = mock(OperableTrigger.class);
        when(detail.getJobDataMap()).thenReturn(map);
        when(bundle.getJobDetail()).thenReturn(detail);
        when(bundle.getTrigger()).thenReturn(trig);
        when(trig.getJobDataMap()).thenReturn(new JobDataMap());

        return new JobExecutionContextImpl(s, bundle, null);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void execute() throws JobExecutionException {
        OwnerClient oclient = mock(OwnerClient.class);
        ConsumerClient conclient = mock(ConsumerClient.class);
        when(conn.connect(eq(OwnerClient.class), any(Credentials.class),
            any(String.class))).thenReturn(oclient);
        when(conn.connect(eq(ConsumerClient.class), any(Credentials.class),
            any(String.class))).thenReturn(conclient);

        ClientResponse<Owner> resp = mock(ClientResponse.class);

        List<Pool> pools = new ArrayList<Pool>();
        pools.add(mock(Pool.class));

        List<Consumer> consumers = new ArrayList<Consumer>();
        Consumer consumer = mock(Consumer.class);
        when(consumer.getUuid()).thenReturn("357ec012");
        consumers.add(consumer);

        List<Entitlement> ents = new ArrayList<Entitlement>();
        Entitlement ent = mock(Entitlement.class);
        when(ent.getId()).thenReturn("ff8080812e9");
        ents.add(ent);

        ClientResponse<List<Pool>> prsp = mock(ClientResponse.class);
        ClientResponse<List<Consumer>> crsp = mock(ClientResponse.class);
        ClientResponse<List<Entitlement>> ersp = mock(ClientResponse.class);
        Response drsp = mock(Response.class);

        when(oclient.replicateOwner(eq("admin"))).thenReturn(resp);
        when(oclient.replicatePools(eq("admin"))).thenReturn(prsp);
        when(oclient.replicateEntitlements(eq("admin"))).thenReturn(ersp);
        when(oclient.replicateConsumers(eq("admin"))).thenReturn(crsp);
        when(oclient.deleteOwner(eq("admin"), eq(false))).thenReturn(drsp);
        when(conclient.replicateEntitlements(eq("357ec012"),
            any(String.class))).thenReturn(ersp);
        when(resp.getStatus()).thenReturn(200);
        when(prsp.getStatus()).thenReturn(200);
        when(crsp.getStatus()).thenReturn(200);
        when(ersp.getStatus()).thenReturn(200);
        when(drsp.getStatus()).thenReturn(204); // typical response from delete

        when(prsp.getEntity()).thenReturn(pools);
        when(crsp.getEntity()).thenReturn(consumers);
        when(ersp.getEntity()).thenReturn(ents).thenReturn(ents);
        JobDataMap map = new JobDataMap();
        map.put("owner_key", "admin");
        map.put("uri", "http://foo.example.com/candlepin");
        map.put("delete", true);

        Owner owner = mock(Owner.class);
        when(ownerCurator.lookupByKey(eq("admin"))).thenReturn(owner);
        when(consumerCurator.listByOwner(any(Owner.class))).thenReturn(consumers);
        when(entCurator.find(eq("ff8080812e9"))).thenReturn(ent);

        // test it :)
        moj.execute(buildContext(map));

        // verify everything was called
        verify(conn).connect(eq(OwnerClient.class), any(Credentials.class),
            eq("http://foo.example.com/candlepin"));
        verify(ownerCurator, atLeastOnce()).replicate(any(Owner.class));
        verify(poolCurator, atLeastOnce()).replicate(any(Pool.class));
        verify(consumerCurator, atLeastOnce()).replicate(any(Consumer.class));
        verify(entCurator, atLeastOnce()).replicate(any(Entitlement.class));
        verify(entCurator, atLeastOnce()).merge(any(Entitlement.class));
        verify(oclient, atLeastOnce()).deleteOwner(eq("admin"), eq(false));
        verify(sink, atLeastOnce()).emitOwnerMigrated(any(Owner.class));
    }

    @Test(expected = Exception.class)
    public void executeInvalidKey() throws JobExecutionException {
        JobDataMap map = new JobDataMap();
        map.put("owner_key", "admin");
        map.put("badurikey", "http://foo.example.com/candlepin");

        moj.execute(buildContext(map));
    }

    @Test(expected = BadRequestException.class)
    public void executeBadValues() throws JobExecutionException {
        JobDataMap map = new JobDataMap();
        map.put("owner_key", "admin");
        map.put("uri", "");

        moj.execute(buildContext(map));
    }

    @Test(expected = NotFoundException.class)
    @SuppressWarnings("unchecked")
    public void executeNonExistentOwner() throws JobExecutionException {
        OwnerClient client = mock(OwnerClient.class);
        when(conn.connect(eq(OwnerClient.class), any(Credentials.class),
            any(String.class))).thenReturn(client);
        ClientResponse<Owner> resp = mock(ClientResponse.class);
        when(client.replicateOwner(eq("doesnotexist"))).thenReturn(resp);
        when(resp.getStatus()).thenReturn(404);

        JobDataMap map = new JobDataMap();
        map.put("owner_key", "doesnotexist");
        map.put("uri", "http://foo.example.com/candlepin");

        moj.execute(buildContext(map));
    }

    private static class ConfigForTesting extends Config {
        public ConfigForTesting() {
            super(new HashMap<String, String>() {
                private static final long serialVersionUID = 1L;
                {
                    this.put(ConfigProperties.SHARD_USERNAME, "admin");
                    this.put(ConfigProperties.SHARD_PASSWORD, "admin");
                    this.put(ConfigProperties.SHARD_WEBAPP, "candlepin");
                }
            });
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void bz874785NullPointer() throws JobExecutionException {
        OwnerClient oclient = mock(OwnerClient.class);
        ConsumerClient conclient = mock(ConsumerClient.class);
        when(conn.connect(eq(OwnerClient.class), any(Credentials.class),
            any(String.class))).thenReturn(oclient);
        when(conn.connect(eq(ConsumerClient.class), any(Credentials.class),
            any(String.class))).thenReturn(conclient);

        ClientResponse<Owner> resp = mock(ClientResponse.class);

        List<Pool> pools = new ArrayList<Pool>();
        pools.add(mock(Pool.class));

        List<Consumer> consumers = new ArrayList<Consumer>();
        Consumer consumer = mock(Consumer.class);
        when(consumer.getUuid()).thenReturn("357ec012");

        // return null getFacts and ensure we don't blow up because of it
        when(consumer.getFacts()).thenReturn(null);
        consumers.add(consumer);

        List<Entitlement> ents = new ArrayList<Entitlement>();
        Entitlement ent = mock(Entitlement.class);
        when(ent.getId()).thenReturn("ff8080812e9");
        ents.add(ent);

        ClientResponse<List<Pool>> prsp = mock(ClientResponse.class);
        ClientResponse<List<Consumer>> crsp = mock(ClientResponse.class);
        ClientResponse<List<Entitlement>> ersp = mock(ClientResponse.class);
        Response drsp = mock(Response.class);

        when(oclient.replicateOwner(eq("admin"))).thenReturn(resp);
        when(oclient.replicatePools(eq("admin"))).thenReturn(prsp);
        when(oclient.replicateEntitlements(eq("admin"))).thenReturn(ersp);
        when(oclient.replicateConsumers(eq("admin"))).thenReturn(crsp);
        when(oclient.deleteOwner(eq("admin"), eq(false))).thenReturn(drsp);
        when(conclient.replicateEntitlements(eq("357ec012"),
            any(String.class))).thenReturn(ersp);
        when(resp.getStatus()).thenReturn(200);
        when(prsp.getStatus()).thenReturn(200);
        when(crsp.getStatus()).thenReturn(200);
        when(ersp.getStatus()).thenReturn(200);
        when(drsp.getStatus()).thenReturn(204); // typical response from delete

        when(prsp.getEntity()).thenReturn(pools);
        when(crsp.getEntity()).thenReturn(consumers);
        when(ersp.getEntity()).thenReturn(ents).thenReturn(ents);
        JobDataMap map = new JobDataMap();
        map.put("owner_key", "admin");
        map.put("uri", "http://foo.example.com/candlepin");
        map.put("delete", true);

        Owner owner = mock(Owner.class);
        when(ownerCurator.lookupByKey(eq("admin"))).thenReturn(owner);
        when(consumerCurator.listByOwner(any(Owner.class))).thenReturn(consumers);
        when(entCurator.find(eq("ff8080812e9"))).thenReturn(ent);

        // test it :)
        moj.execute(buildContext(map));

        // verify everything was called
        verify(conn).connect(eq(OwnerClient.class), any(Credentials.class),
            eq("http://foo.example.com/candlepin"));
        verify(ownerCurator, atLeastOnce()).replicate(any(Owner.class));
        verify(poolCurator, atLeastOnce()).replicate(any(Pool.class));
        verify(consumerCurator, atLeastOnce()).replicate(any(Consumer.class));
        verify(entCurator, atLeastOnce()).replicate(any(Entitlement.class));
        verify(entCurator, atLeastOnce()).merge(any(Entitlement.class));
        verify(oclient, atLeastOnce()).deleteOwner(eq("admin"), eq(false));
        verify(sink, atLeastOnce()).emitOwnerMigrated(any(Owner.class));
    }
}
