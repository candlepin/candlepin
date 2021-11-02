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
package org.candlepin.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.exceptions.NotAuthorizedException;
import org.candlepin.guice.I18nProvider;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.DeletedConsumerCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.test.TestUtil;

import org.jboss.resteasy.spi.HttpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.security.cert.X509Certificate;
import java.util.Locale;

import javax.security.auth.x500.X500Principal;

public class SSLAuthTest {

    @Mock private HttpRequest httpRequest;
    @Mock private ConsumerCurator consumerCurator;
    @Mock private OwnerCurator ownerCurator;
    @Mock private DeletedConsumerCurator deletedConsumerCurator;
    @Mock private I18nProvider i18nProvider;

    private SSLAuth auth;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        this.auth = new SSLAuth(this.consumerCurator,
            this.ownerCurator,
            this.deletedConsumerCurator,
            this.i18nProvider);
    }

    /**
     * No cert
     */
    @Test
    public void noCert() {
        assertNull(this.auth.getPrincipal(httpRequest));
    }

    /**
     * Happy path - parses the username from the cert's DN correctly.
     */
    @Test
    public void correctUserName() {
        ConsumerType ctype = new ConsumerType(ConsumerTypeEnum.SYSTEM);
        ctype.setId("test-ctype");

        Owner owner = new Owner("test owner");
        owner.setId(TestUtil.randomString());
        Consumer consumer = new Consumer("machine_name", "test user", owner, ctype);
        ConsumerPrincipal expected = new ConsumerPrincipal(consumer, owner);

        String dn = "CN=453-44423-235";

        mockCert(dn);
        when(this.consumerCurator.getConsumer("453-44423-235")).thenReturn(consumer);
        when(this.ownerCurator.findOwnerById(owner.getOwnerId())).thenReturn(owner);
        assertEquals(expected, this.auth.getPrincipal(httpRequest));
    }

    /**
     * DN is set but does not contain UID
     */
    @Test
    public void noUuidOnCert() {
        mockCert("OU=something");
        when(this.consumerCurator.findByUuid(anyString())).thenReturn(
            new Consumer("machine_name", "test user", null, null));
        assertNull(this.auth.getPrincipal(httpRequest));
    }

    /**
     * Uuid in the cert is not found by the curator.
     */
    @Test
    public void noValidConsumerEntity() {
        mockCert("CN=235-8");
        when(this.consumerCurator.findByUuid("235-8")).thenReturn(null);
        assertNull(this.auth.getPrincipal(httpRequest));
    }

    @Test
    public void invalidCertTypeThrowsException() {
        X509Certificate idCert =  mock(X509Certificate.class);
        when(this.httpRequest.getAttribute("javax.servlet.request.X509Certificate"))
            .thenReturn(new X509Certificate[]{idCert});
        when(idCert.getExtensionValue(any())).thenReturn("random".getBytes());
        I18n i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        when(this.i18nProvider.get()).thenReturn(i18n);

        assertThrows(NotAuthorizedException.class, () -> this.auth.getPrincipal(httpRequest));
    }

    private void mockCert(String dn) {
        X509Certificate idCert = mock(X509Certificate.class);
        X500Principal principal = new X500Principal(dn);

        when(idCert.getSubjectX500Principal()).thenReturn(principal);
        when(this.httpRequest.getAttribute("javax.servlet.request.X509Certificate"))
            .thenReturn(new X509Certificate[]{idCert});
    }

}
