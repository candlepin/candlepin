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
package org.fedoraproject.candlepin.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.model.Owner;

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.jboss.resteasy.client.ClientResponse;
import org.junit.Test;


/**
 * CandlepinConnectionTest
 */
public class CandlepinConnectionTest {

    @Test
    public void connect() {
        CandlepinConnection conn = new CandlepinConnection(new Config());
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        OwnerClient client = conn.connect(creds, "http://localhost:8080/candlepin/");
        ClientResponse<Owner> resp = client.exportOwner("admin");
        
        assertNotNull(resp);
        assertEquals(200, resp.getStatus());
        Owner o = resp.getEntity();
        assertNotNull(o);
        System.out.println(o);
    }
    
    @Test
    public void doesnotexist() {
        CandlepinConnection conn = new CandlepinConnection(new Config());
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        OwnerClient client = conn.connect(creds, "http://localhost:8080/candlepin/");
        ClientResponse<Owner> resp = client.exportOwner("doesnotexist");
        
        assertNotNull(resp);
        assertEquals(404, resp.getStatus());
    }
}
