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
package org.candlepin.client;

import org.candlepin.config.Config;
import org.candlepin.resteasy.JsonProvider;

import com.google.inject.Inject;

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.auth.AuthScope;
import org.jboss.resteasy.client.ClientExecutor;
import org.jboss.resteasy.client.ProxyFactory;
import org.jboss.resteasy.client.core.executors.ApacheHttpClientExecutor;
import org.jboss.resteasy.plugins.providers.RegisterBuiltin;
import org.jboss.resteasy.spi.ResteasyProviderFactory;

/**
 * CandlepinConnection sets up the remote connection to another Candlepin
 * instance. It takes in the stand Config object to configure the JsonProvider
 * then takes in some Credentials to login via the connect method.
 */
public class CandlepinConnection {

    @Inject
    public CandlepinConnection(Config config) {
        ResteasyProviderFactory rpf = ResteasyProviderFactory.getInstance();
        JsonProvider jsonprovider = new JsonProvider(config);
        rpf.addMessageBodyReader(jsonprovider);
        rpf.addMessageBodyWriter(jsonprovider);
        RegisterBuiltin.register(rpf);
    }

    /**
     * Connects to another Candlepin instance located at the given uri.
     * @param clazz the client class to create.
     * @param creds authentication credentials for the given uri.
     * @param uri the Candlepin instance to connect to
     * @return Client proxy used to interact with Candlepin via REST API.
     */
    public <T> T connect(Class<T> clazz, Credentials creds, String uri) {
        HttpClient httpclient = new HttpClient();
        httpclient.getState().setCredentials(AuthScope.ANY, creds);
        ClientExecutor clientExecutor = new ApacheHttpClientExecutor(httpclient);
        return ProxyFactory.create(clazz, uri,
            clientExecutor);
    }
}
