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
package org.candlepin.resteasy.interceptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.security.cert.X509Certificate;

import org.candlepin.auth.ConsumerPrincipal;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.DeletedConsumerCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.jboss.resteasy.spi.HttpRequest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class SSLAuthTest {

    @Mock private HttpRequest request;
    @Mock private ConsumerCurator consumerCurator;
    @Mock private DeletedConsumerCurator deletedConsumerCurator;

    private SSLAuth auth;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        this.auth = new SSLAuth(this.consumerCurator, this.deletedConsumerCurator);
    }

    /**
     * No cert
     *
     * @throws Exception
     */
    @Test
    public void noCert() throws Exception {
        assertNull(this.auth.getPrincipal(request));
    }

    /**
     * Happy path - parses the username from the cert's DN correctly.
     *
     * @throws Exception
     */
    @Test
    public void correctUserName() throws Exception {
        Owner owner = new Owner("test owner");
        Consumer consumer = new Consumer("machine_name", "test user", owner,
                new ConsumerType(ConsumerTypeEnum.SYSTEM));
        ConsumerPrincipal expected = new ConsumerPrincipal(consumer);

        String dn = "CN=453-44423-235";

        mockCert(dn);
        when(this.consumerCurator.getConsumer("453-44423-235")).thenReturn(consumer);
        assertEquals(expected, this.auth.getPrincipal(request));
    }

    /**
     * DN is set but does not contain UID
     *
     * @throws Exception
     */
    @Test
    public void noUuidOnCert() throws Exception {
        mockCert("OU=something");
        when(this.consumerCurator.findByUuid(anyString())).thenReturn(
                new Consumer("machine_name", "test user", null, null));
        assertNull(this.auth.getPrincipal(request));
    }

    /**
     * Uuid in the cert is not found by the curator.
     *
     * @throws Exception
     */
    @Test
    public void noValidConsumerEntity() throws Exception {
        mockCert("CN=235-8");
        when(this.consumerCurator.findByUuid("235-8")).thenReturn(null);
        assertNull(this.auth.getPrincipal(request));
    }


    private void mockCert(String dn) {
        X509Certificate idCert =  mock(X509Certificate.class);
        Principal principal = mock(Principal.class);

        when(principal.getName()).thenReturn(dn);
        when(idCert.getSubjectDN()).thenReturn(principal);
        when(this.request.getAttribute("javax.servlet.request.X509Certificate"))
                .thenReturn(new X509Certificate[]{idCert});
    }

}
