/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.pinsetter.tasks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.fedoraproject.candlepin.client.CandlepinConnection;
import org.fedoraproject.candlepin.client.OwnerClient;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.config.ConfigProperties;
import org.fedoraproject.candlepin.exceptions.BadRequestException;
import org.fedoraproject.candlepin.exceptions.NotFoundException;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.fedoraproject.candlepin.model.IdentityCertificate;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementCurator;
import org.fedoraproject.candlepin.model.OwnerCurator;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.PoolCurator;
import org.fedoraproject.candlepin.model.EntitlementCurator;
import org.fedoraproject.candlepin.model.KeyPairCurator;
import org.fedoraproject.candlepin.model.IdentityCertificateCurator;

import org.apache.commons.httpclient.Credentials;
import org.jboss.resteasy.client.ClientResponse;
import org.junit.Before;
import org.junit.Test;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.spi.TriggerFiredBundle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


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
    private KeyPairCurator keyPairCurator;
    private IdentityCertificateCurator idCertCurator;

    
    @Before
    public void init() {
        config = new ConfigForTesting();
        ownerCurator = mock(OwnerCurator.class);
        consumerCurator = mock(ConsumerCurator.class);
        conn = mock(CandlepinConnection.class);
        poolCurator = mock(PoolCurator.class);
        entCurator = mock(EntitlementCurator.class);
        keyPairCurator = mock(KeyPairCurator.class);
        idCertCurator = mock(IdentityCertificateCurator.class);
        moj = new MigrateOwnerJob(ownerCurator, conn, config, poolCurator,
            entCurator, consumerCurator, keyPairCurator, idCertCurator);
    }
    
    @Test
    public void testMigrateOwner() {
        JobDetail jd = moj.migrateOwner("admin",
            "http://foo.example.com/candlepin");
        assertNotNull(jd);
        assertNotNull(jd.getJobDataMap());
        assertEquals("admin", jd.getJobDataMap().get("owner_key"));
        assertEquals("http://foo.example.com/candlepin",
            jd.getJobDataMap().get("uri"));
    }
    
    @Test(expected = Exception.class)
    public void nullOwner() {
        moj.migrateOwner(null, "http://foo.example.com/candlepin");
    }
    
    @Test(expected = BadRequestException.class)
    public void nullUrl() {
        moj.migrateOwner("admin", null);
    }
    
    @Test(expected = BadRequestException.class)
    public void invalidUrlFormat() {
        moj.migrateOwner("admin", "");
    }
    
    // used by execute tests
    private JobExecutionContext buildContext(JobDataMap map) {
        Scheduler s = mock(Scheduler.class);
        TriggerFiredBundle bundle = mock(TriggerFiredBundle.class);
        JobDetail detail = mock(JobDetail.class);
        Trigger trig = mock(Trigger.class);
        when(detail.getJobDataMap()).thenReturn(map);
        when(bundle.getJobDetail()).thenReturn(detail);
        when(bundle.getTrigger()).thenReturn(trig);
        when(trig.getJobDataMap()).thenReturn(new JobDataMap());
        
        return new JobExecutionContext(s, bundle, null);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void execute() throws JobExecutionException {
        OwnerClient client = mock(OwnerClient.class);
        when(conn.connect(any(Credentials.class),
            any(String.class))).thenReturn(client);
        ClientResponse<Owner> resp = mock(ClientResponse.class);
        
        List<Pool> pools = new ArrayList<Pool>();
        pools.add(mock(Pool.class));
        
        List<Consumer> consumers = new ArrayList<Consumer>();
        consumers.add(mock(Consumer.class));

        List<Entitlement> ents = new ArrayList<Entitlement>();
        ents.add(mock(Entitlement.class));
 
        ClientResponse<List<Pool>> prsp = mock(ClientResponse.class);
        ClientResponse<List<Consumer>> crsp = mock(ClientResponse.class); 
	ClientResponse<List<Entitlement>> ersp = mock(ClientResponse.class);

        when(client.exportOwner(eq("admin"))).thenReturn(resp);
        when(client.exportPools(eq("admin"))).thenReturn(prsp);
	when(client.exportEntitlements(eq("admin"))).thenReturn(ersp);
        when(client.exportOwnerConsumers(eq("admin"))).thenReturn(crsp);
        when(resp.getStatus()).thenReturn(200);
        when(prsp.getStatus()).thenReturn(200);
        when(ersp.getStatus()).thenReturn(200);
        
        when(prsp.getEntity()).thenReturn(pools);
        when(crsp.getEntity()).thenReturn(consumers);
        when(ersp.getEntity()).thenReturn(ents);
        JobDataMap map = new JobDataMap();
        map.put("owner_key", "admin");
        map.put("uri", "http://foo.example.com/candlepin");

        // test it :)
        moj.execute(buildContext(map));

        // verify everything was called
        verify(conn).connect(any(Credentials.class),
            eq("http://foo.example.com/candlepin"));
        verify(ownerCurator, atLeastOnce()).importOwner(any(Owner.class));
        verify(poolCurator, atLeastOnce()).importPool(any(Pool.class));
        verify(consumerCurator, atLeastOnce()).importConsumer(any(Consumer.class));
        verify(entCurator, atLeastOnce()).importEntitlement(any(Entitlement.class));
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
        when(conn.connect(any(Credentials.class),
            any(String.class))).thenReturn(client);
        ClientResponse<Owner> resp = mock(ClientResponse.class);
        when(client.exportOwner(eq("doesnotexist"))).thenReturn(resp);
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
}
