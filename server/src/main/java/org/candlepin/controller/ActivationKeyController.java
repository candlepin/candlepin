/**
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.controller;

import org.candlepin.common.exceptions.AlreadyRegisteredException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.common.exceptions.RuleValidationException;
import org.candlepin.dto.api.v1.ActivationKeyDTO;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.Release;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.model.activationkeys.ActivationKeyPool;
import org.candlepin.policy.activationkey.ActivationKeyRules;
import org.candlepin.util.ServiceLevelValidator;

import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ActivationKeyController
 */
public class ActivationKeyController {

    private static final Logger log = LoggerFactory.getLogger(ActivationKeyController.class);
    private static final Pattern AK_CHAR_FILTER = Pattern.compile("^[a-zA-Z0-9_-]+$");

    private final ActivationKeyCurator activationKeyCurator;
    private final OwnerProductCurator ownerProductCurator;
    private final PoolManager poolManager;
    private final I18n i18n;
    private final ServiceLevelValidator serviceLevelValidator;
    private final ActivationKeyRules activationKeyRules;

    @Inject
    public ActivationKeyController(ActivationKeyCurator activationKeyCurator, I18n i18n,
        PoolManager poolManager, ServiceLevelValidator serviceLevelValidator,
        ActivationKeyRules activationKeyRules, OwnerProductCurator ownerProductCurator) {

        this.activationKeyCurator = Objects.requireNonNull(activationKeyCurator);
        this.i18n = Objects.requireNonNull(i18n);
        this.poolManager = Objects.requireNonNull(poolManager);
        this.serviceLevelValidator = Objects.requireNonNull(serviceLevelValidator);
        this.activationKeyRules = Objects.requireNonNull(activationKeyRules);
        this.ownerProductCurator = Objects.requireNonNull(ownerProductCurator);
    }

    /**
     * Fetches an activation key using the specified key ID. If a valid activation key could not be
     * found, this method throws an exception.
     *
     * @param keyId
     *  The ID of the activation key to fetch
     *
     * @throws IllegalArgumentException
     *  if the given ID is null, empty
     *
     * @throws NotFoundException
     *  if the given ID is not associated with a valid activation key
     *
     * @return
     *  an ActivationKey with the specified ID
     */
    private ActivationKey fetchActivationKey(String keyId) {
        if (keyId == null || keyId.isEmpty()) {
            throw new IllegalArgumentException(i18n.tr("activation key ID is null or empty"));
        }

        ActivationKey key = this.activationKeyCurator.secureGet(keyId);

        if (key == null) {
            throw new NotFoundException(i18n.tr("ActivationKey with id {0} could not be found.", keyId));
        }

        return key;
    }

    public ActivationKey getActivationKey(String activationKeyId) {
        return this.fetchActivationKey(activationKeyId);
    }

    public Set<ActivationKeyPool> getActivationKeyPools(String activationKeyId) {
        return this.fetchActivationKey(activationKeyId)
            .getPools();
    }

    public ActivationKey updateActivationKey(String activationKeyId, ActivationKeyDTO update) {
        ActivationKey toUpdate = this.fetchActivationKey(activationKeyId);

        if (update.getName() != null) {
            Matcher keyMatcher = AK_CHAR_FILTER.matcher(update.getName());

            if (!keyMatcher.matches()) {
                throw new IllegalArgumentException(
                    i18n.tr("The activation key name \"{0}\" must be alphanumeric or " +
                        "include the characters \"-\" or \"_\"", update.getName()));
            }

            toUpdate.setName(update.getName());
        }

        String serviceLevel = update.getServiceLevel();
        if (serviceLevel != null) {
            serviceLevelValidator.validate(toUpdate.getOwner().getId(), serviceLevel);
            toUpdate.setServiceLevel(serviceLevel);
        }

        if (update.getReleaseVersion() != null) {
            toUpdate.setReleaseVer(new Release(update.getReleaseVersion()));
        }

        if (update.getDescription() != null) {
            toUpdate.setDescription(update.getDescription());
        }

        if (update.getUsage() != null) {
            toUpdate.setUsage(update.getUsage());
        }

        if (update.getRole() != null) {
            toUpdate.setRole(update.getRole());
        }

        if (update.getAddOns() != null) {
            Set<String> addOns = new HashSet<>(update.getAddOns());
            toUpdate.setAddOns(addOns);
        }

        if (update.isAutoAttach() != null) {
            toUpdate.setAutoAttach(update.isAutoAttach());
        }
        return activationKeyCurator.merge(toUpdate);
    }

    public ActivationKey addPoolToKey(String activationKeyId, String poolId, Long quantity) {
        ActivationKey key = this.fetchActivationKey(activationKeyId);
        Pool pool = findPool(poolId);

        // Throws a RuleValidationException if adding pool to key is a bad idea
        String message = activationKeyRules.validatePoolForActivationKey(key, pool, quantity);
        if (message != null) {
            throw new RuleValidationException(message);
        }

        // Make sure we don't try to register the pool twice.
        if (key.hasPool(pool)) {
            throw new AlreadyRegisteredException(
                i18n.tr("Pool ID \"{0}\" has already been registered with this activation key", poolId)
            );
        }

        key.addPool(pool, quantity);
        activationKeyCurator.update(key);
        return key;
    }

    public ActivationKey removePoolFromKey(String activationKeyId, String poolId) {
        ActivationKey key = this.fetchActivationKey(activationKeyId);
        Pool pool = findPool(poolId);
        key.removePool(pool);
        activationKeyCurator.update(key);
        return key;
    }

    public ActivationKey addProductIdToKey(String activationKeyId, String productId) {
        ActivationKey key = this.fetchActivationKey(activationKeyId);
        Product product = confirmProduct(key.getOwner(), productId);

        // Make sure we don't try to register the product ID twice.
        if (key.hasProduct(product)) {
            throw new AlreadyRegisteredException(
                i18n.tr("Product ID \"{0}\" has already been registered with this activation key", productId)
            );
        }

        key.addProduct(product);
        activationKeyCurator.update(key);
        return key;
    }

    public ActivationKey removeProductIdFromKey(String activationKeyId, String productId) {
        ActivationKey key = this.fetchActivationKey(activationKeyId);
        Product product = confirmProduct(key.getOwner(), productId);
        key.removeProduct(product);
        activationKeyCurator.update(key);
        return key;
    }

    public CandlepinQuery<ActivationKey> findActivationKey() {
        return this.activationKeyCurator.listAll();
    }

    public void deleteActivationKey(String activationKeyId) {
        ActivationKey key = this.fetchActivationKey(activationKeyId);

        log.debug("Deleting activation key: {}", activationKeyId);

        activationKeyCurator.delete(key);
    }

    private Pool findPool(String poolId) {
        Pool pool = poolManager.get(poolId);

        if (pool == null) {
            throw new NotFoundException(i18n.tr("Pool with id {0} could not be found.", poolId));
        }

        return pool;
    }

    private Product confirmProduct(Owner o, String prodId) {
        Product prod = this.ownerProductCurator.getProductById(o, prodId);

        if (prod == null) {
            throw new NotFoundException(i18n.tr("Product with id {0} could not be found.", prodId));
        }

        return prod;
    }

}
