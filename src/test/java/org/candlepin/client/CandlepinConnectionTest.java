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
package org.candlepin.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.model.Owner;

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.jboss.resteasy.client.ClientResponse;
import org.junit.Ignore;
import org.junit.Test;


/**
 * CandlepinConnectionTest
 */
public class CandlepinConnectionTest {

    @Ignore("needs mock connection to test with")
    @Test
    public void connect() {
        CandlepinConnection conn = new CandlepinConnection(
            new CandlepinCommonTestConfig());
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        OwnerClient client = conn.connect(OwnerClient.class, creds,
            "http://localhost:8080/candlepin/");
        ClientResponse<Owner> resp = client.replicateOwner("admin");

        assertNotNull(resp);
        assertEquals(200, resp.getStatus());
        Owner o = resp.getEntity();
        assertNotNull(o);
        System.out.println(o);
    }

    @Ignore("needs mock connection to test with")
    @Test
    public void doesnotexist() {
        CandlepinConnection conn = new CandlepinConnection(
            new CandlepinCommonTestConfig());
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        OwnerClient client = conn.connect(OwnerClient.class, creds,
            "http://localhost:8080/candlepin/");
        ClientResponse<Owner> resp = client.replicateOwner("doesnotexist");

        assertNotNull(resp);
        assertEquals(404, resp.getStatus());
    }
}
