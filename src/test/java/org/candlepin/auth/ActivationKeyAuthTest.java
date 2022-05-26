/**
 * Copyright (c) 2009 - 2022 Red Hat, Inc.
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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.NotAuthorizedException;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyCurator;

import org.jboss.resteasy.spi.HttpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.List;
import java.util.Locale;

import javax.inject.Provider;

@ExtendWith(MockitoExtension.class)
class ActivationKeyAuthTest {

    private static final String TEST_OWNER = "testOwner";

    @Mock
    private ActivationKeyCurator activationKeyCurator;
    @Mock
    private OwnerCurator ownerCurator;
    @Mock
    private HttpRequest request;
    private Provider<I18n> i18nProvider;
    private FakeUriInfo uriInfo;

    @BeforeEach
    void setUp() {
        I18n i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        i18nProvider = () -> i18n;

        uriInfo = new FakeUriInfo("/consumers");
        when(request.getUri()).thenReturn(uriInfo);
    }

    @Test
    void wrongMethodIsRejected() {
        ActivationKeyAuth auth = createActivationKeyAuth();

        Principal principal = auth.getPrincipal(request);

        assertNull(principal);
        verifyNoInteractions(ownerCurator);
    }

    @Test
    void keysAreRequired() {
        ActivationKeyAuth auth = createActivationKeyAuth();
        addQueryParam("owner", TEST_OWNER);

        Principal principal = auth.getPrincipal(request);

        assertNull(principal);
    }

    @Test
    void shouldFailOnMissingOwner() {
        ActivationKeyAuth auth = createActivationKeyAuth();
        addQueryParam("activation_keys", "testkey");

        assertThrows(BadRequestException.class, () -> auth.getPrincipal(request));
    }

    @Test
    void specifiedOwnerMustExist() {
        ActivationKeyAuth auth = createActivationKeyAuth();
        addQueryParam("owner", TEST_OWNER);
        addQueryParam("activation_keys", "testkey");

        assertThrows(NotAuthorizedException.class, () -> auth.getPrincipal(request));
    }

    @Test
    void cannotSpecifyKeysAndUsernameAtOnce() {
        ActivationKeyAuth auth = createActivationKeyAuth();
        addQueryParam("owner", TEST_OWNER);
        addQueryParam("activation_keys", "testkey");
        addQueryParam("username", "testUser");

        assertThrows(BadRequestException.class, () -> auth.getPrincipal(request));
    }

    @Test
    void noKeysFound() {
        ActivationKeyAuth auth = createActivationKeyAuth();
        addQueryParam("owner", TEST_OWNER);
        addQueryParam("activation_keys", "testkey");
        mockOwner(TEST_OWNER);

        assertThrows(NotAuthorizedException.class, () -> auth.getPrincipal(request));
    }

    @Test
    void shouldSucceedForNewConsumer() {
        ActivationKeyAuth auth = createActivationKeyAuth();
        addQueryParam("owner", TEST_OWNER);
        addQueryParam("activation_keys", "testkey");
        mockOwner(TEST_OWNER);
        mockKey(TEST_OWNER, "testkey");

        Principal principal = auth.getPrincipal(request);

        assertNotNull(principal);
    }

    @Test
    void shouldSucceedForNewConsumerInEnv() {
        ActivationKeyAuth auth = createActivationKeyAuth();
        addQueryParam("owner", TEST_OWNER);
        addQueryParam("activation_keys", "testkey");
        addPathParam("env_id", "testenv");
        mockOwner(TEST_OWNER);
        mockKey(TEST_OWNER, "testkey");

        Principal principal = auth.getPrincipal(request);

        assertNotNull(principal);
    }

    private void mockKey(String owner, String key) {
        when(this.activationKeyCurator.findByKeyNames(eq(owner), anyCollection()))
            .thenReturn(List.of(new ActivationKey(key, new Owner())));
    }

    private ActivationKeyAuth createActivationKeyAuth() {
        return new ActivationKeyAuth(
            i18nProvider, activationKeyCurator, ownerCurator);
    }

    private void mockOwner(String ownerKey) {
        when(this.ownerCurator.existsByKey(ownerKey)).thenReturn(true);
    }

    private void addQueryParam(String key, String value) {
        uriInfo.addQueryParam(key, value);
    }

    private void addPathParam(String key, String value) {
        uriInfo.addPathParam(key, value);
    }

}
