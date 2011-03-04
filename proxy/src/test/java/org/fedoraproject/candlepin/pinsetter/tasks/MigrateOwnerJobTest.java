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
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.OwnerCurator;

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

import java.util.HashMap;


/**
 * MigrateOwnerJobTest
 */
public class MigrateOwnerJobTest {

    private OwnerCurator ownerCurator;
    private CandlepinConnection conn;
    private MigrateOwnerJob moj;
    private Config config;

    
    @Before
    public void init() {
        config = new ConfigForTesting();
        ownerCurator = mock(OwnerCurator.class);
        conn = mock(CandlepinConnection.class);
        moj = new MigrateOwnerJob(ownerCurator, conn, config);
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
        when(client.exportOwner(eq("admin"))).thenReturn(resp);
        when(resp.getStatus()).thenReturn(200);
        JobDataMap map = new JobDataMap();
        map.put("owner_key", "admin");
        map.put("uri", "http://foo.example.com/candlepin");

        moj.execute(buildContext(map));

        verify(conn).connect(any(Credentials.class),
            eq("http://foo.example.com/candlepin"));
        verify(ownerCurator, atLeastOnce()).importOwner(any(Owner.class));
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
