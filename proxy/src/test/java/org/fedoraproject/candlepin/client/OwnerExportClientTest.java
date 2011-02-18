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

import static org.junit.Assert.assertNotNull;

//import org.fedoraproject.candlepin.model.Owner;

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.jboss.resteasy.client.ClientExecutor;
import org.jboss.resteasy.client.ProxyFactory;
import org.jboss.resteasy.client.core.executors.ApacheHttpClientExecutor;
import org.jboss.resteasy.plugins.providers.RegisterBuiltin;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
//import org.junit.Test;


/**
 * OwnerExportClientTest
 */
public class OwnerExportClientTest {

    //@Test
    public void exportOwner() {
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        HttpClient httpclient = new HttpClient();
        httpclient.getState().setCredentials(AuthScope.ANY, creds);
        //httpclient.getParams().setAuthenticationPreemptive(true);
        
        ClientExecutor clientExecutor = new ApacheHttpClientExecutor(httpclient);
        RegisterBuiltin.register(ResteasyProviderFactory.getInstance());
        OwnerExportClient oec = ProxyFactory.create(OwnerExportClient.class,
            "http://localhost:8080/candlepin/", clientExecutor);
        System.out.println("1");
        String o = oec.exportOwner("admin");
        System.out.println("2");
        assertNotNull(o);
        System.out.println(o);
    }
}
