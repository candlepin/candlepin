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

import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.NotAuthorizedException;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyCurator;

import com.google.inject.Inject;

import org.jboss.resteasy.spi.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Provider;
import javax.ws.rs.core.MultivaluedMap;


/**
 * Auth implementation to allow authentication using activation keys for
 * the consumer registration.
 */
public class ActivationKeyAuth implements AuthProvider {

    private static final Logger log = LoggerFactory.getLogger(ActivationKeyAuth.class);

    private final Provider<I18n> i18nProvider;
    private final ActivationKeyCurator activationKeyCurator;
    private final OwnerCurator ownerCurator;

    @Inject
    ActivationKeyAuth(Provider<I18n> i18nProvider, ActivationKeyCurator activationKeyCurator,
        OwnerCurator ownerCurator) {
        this.i18nProvider = Objects.requireNonNull(i18nProvider);
        this.activationKeyCurator = Objects.requireNonNull(activationKeyCurator);
        this.ownerCurator = Objects.requireNonNull(ownerCurator);
    }

    @Override
    public Principal getPrincipal(HttpRequest httpRequest) {
        MultivaluedMap<String, String> queryParams = httpRequest.getUri().getQueryParameters();
        if (!validateRequiredParams(queryParams)) {
            return null;
        }

        String ownerKey = queryParams.getFirst("owner");
        if (!this.ownerCurator.existsByKey(ownerKey)) {
            throw new NotAuthorizedException(
                this.i18nProvider.get().tr("Organization {0} does not exist.", ownerKey));
        }

        String keys = queryParams.getFirst("activation_keys");
        List<ActivationKey> activationKeys = findActivationKeys(ownerKey, keys);
        if (activationKeys.isEmpty()) {
            throw new NotAuthorizedException(
                this.i18nProvider.get().tr("None of the activation keys specified exist for this org."));
        }

        String foundKeyNames = activationKeys.stream()
            .map(ActivationKey::getName)
            .collect(Collectors.joining(","));

        return new ActivationKeyPrincipal(foundKeyNames);
    }

    private boolean validateRequiredParams(MultivaluedMap<String, String> queryParams) {
        if (!queryParams.containsKey("activation_keys")) {
            return false;
        }
        if (!queryParams.containsKey("owner")) {
            throw new BadRequestException(
                this.i18nProvider.get().tr("Org required to register with activation keys."));
        }
        if (queryParams.containsKey("username")) {
            throw new BadRequestException(
                this.i18nProvider.get().tr("Cannot specify username with activation keys."));
        }

        return true;
    }

    private List<ActivationKey> findActivationKeys(String ownerKey, String keys) {
        Set<String> keyNames = parseActivationKeys(keys);
        List<ActivationKey> foundKeys = this.activationKeyCurator.findByKeyNames(ownerKey, keyNames);
        logMissingKeys(ownerKey, foundKeys, keyNames);
        return foundKeys;
    }

    private Set<String> parseActivationKeys(String activationKeys) {
        Set<String> keys = new LinkedHashSet<>();
        if (activationKeys != null) {
            Collections.addAll(keys, activationKeys.split(","));
        }
        return keys;
    }

    private void logMissingKeys(String ownerKey, List<ActivationKey> foundKeys, Set<String> keys) {
        Set<String> found = foundKeys.stream()
            .map(ActivationKey::getName)
            .collect(Collectors.toSet());

        for (String key : keys) {
            if (!found.contains(key)) {
                log.warn("Activation key \"{}\" not found for organization \"{}\".", key, ownerKey);
            }
        }

    }

}
