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
package org.candlepin.resource;

import org.candlepin.auth.Access;
import org.candlepin.auth.Principal;
import org.candlepin.auth.SubResource;
import org.candlepin.auth.Verify;
import org.candlepin.common.auth.SecurityHole;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.ForbiddenException;
import org.candlepin.controller.PoolManager;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.v1.ActivationKeyDTO;
import org.candlepin.dto.api.v1.ContentOverrideDTO;
import org.candlepin.dto.api.v1.PoolDTO;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.Release;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyContentOverride;
import org.candlepin.model.activationkeys.ActivationKeyContentOverrideCurator;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.policy.activationkey.ActivationKeyRules;
import org.candlepin.resource.validation.DTOValidator;
import org.candlepin.util.ContentOverrideValidator;
import org.candlepin.util.ServiceLevelValidator;
import org.candlepin.util.TransformedIterator;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.apache.commons.lang.StringUtils;
import org.jboss.resteasy.core.ResteasyContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * ActivationKeyResource
 */
public class ActivationKeyResource implements ActivationKeysApi {
    private static final Logger log = LoggerFactory.getLogger(ActivationKeyResource.class);
    private static final Pattern AK_CHAR_FILTER = Pattern.compile("^[a-zA-Z0-9_-]+$");

    private final ActivationKeyCurator activationKeyCurator;
    private final ActivationKeyContentOverrideCurator contentOverrideCurator;
    private final OwnerProductCurator ownerProductCurator;
    private final PoolManager poolManager;
    private final I18n i18n;
    private final ServiceLevelValidator serviceLevelValidator;
    private final ActivationKeyRules activationKeyRules;
    private final ModelTranslator translator;
    private final DTOValidator dtoValidator;
    private final ContentOverrideValidator coValidator;

    @Inject
    public ActivationKeyResource(ActivationKeyCurator activationKeyCurator, I18n i18n,
        PoolManager poolManager, ServiceLevelValidator serviceLevelValidator,
        ActivationKeyRules activationKeyRules, OwnerProductCurator ownerProductCurator,
        ModelTranslator translator, DTOValidator dtoValidator,
        ActivationKeyContentOverrideCurator contentOverrideCurator, ContentOverrideValidator coValidator) {

        this.activationKeyCurator = Objects.requireNonNull(activationKeyCurator);
        this.i18n = Objects.requireNonNull(i18n);
        this.poolManager = Objects.requireNonNull(poolManager);
        this.serviceLevelValidator = Objects.requireNonNull(serviceLevelValidator);
        this.activationKeyRules = Objects.requireNonNull(activationKeyRules);
        this.ownerProductCurator = Objects.requireNonNull(ownerProductCurator);
        this.translator = Objects.requireNonNull(translator);
        this.dtoValidator = Objects.requireNonNull(dtoValidator);
        this.contentOverrideCurator = Objects.requireNonNull(contentOverrideCurator);
        this.coValidator = Objects.requireNonNull(coValidator);
    }

    /**
     * Fetches an activation key using the specified key ID. If a valid activation key could not be
     * found, this method throws an exception.
     *
     * @param keyId
     *  The ID of the activation key to fetch
     *
     * @throws BadRequestException
     *  if the given ID is null, empty or is not associated with a valid activation key
     *
     * @return
     *  an ActivationKey with the specified ID
     */
    private ActivationKey fetchActivationKey(String keyId) {
        if (keyId == null || keyId.isEmpty()) {
            throw new BadRequestException(i18n.tr("Activation key ID is null or empty"));
        }

        ActivationKey key = this.activationKeyCurator.secureGet(keyId);

        if (key == null) {
            throw new BadRequestException(i18n.tr(
                "Activation key with ID \"{0}\" could not be found.", keyId));
        }

        return key;
    }

    @Override
    public ActivationKeyDTO getActivationKey(@Verify(ActivationKey.class) String activationKeyId) {
        ActivationKey key = this.fetchActivationKey(activationKeyId);
        return this.translator.translate(key, ActivationKeyDTO.class);
    }

    @Override
    public Iterable<PoolDTO> getActivationKeyPools(@Verify(ActivationKey.class) String activationKeyId) {
        ActivationKey key = this.fetchActivationKey(activationKeyId);
        return () -> new TransformedIterator<>(key.getPools().iterator(),
            akp -> translator.translate(akp.getPool(), PoolDTO.class)
        );
    }

    @Override
    public ActivationKeyDTO updateActivationKey(@Verify(ActivationKey.class) String activationKeyId,
        ActivationKeyDTO update) {
        dtoValidator.validateConstraints(update);
        dtoValidator.validateCollectionElementsNotNull(update::getProducts, update::getPools,
            update::getContentOverrides);

        ActivationKey toUpdate = this.fetchActivationKey(activationKeyId);

        if (update.getName() != null) {
            Matcher keyMatcher = AK_CHAR_FILTER.matcher(update.getName());

            if (!keyMatcher.matches()) {
                throw new BadRequestException(
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

        if (update.getReleaseVer() != null) {
            toUpdate.setReleaseVer(new Release(update.getReleaseVer().getReleaseVer()));
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

        if (update.getAutoAttach() != null) {
            toUpdate.setAutoAttach(update.getAutoAttach());
        }

        toUpdate = activationKeyCurator.merge(toUpdate);

        return this.translator.translate(toUpdate, ActivationKeyDTO.class);
    }

    @Override
    public ActivationKeyDTO addPoolToKey(@Verify(ActivationKey.class) String activationKeyId,
        @Verify(Pool.class) String poolId, Long quantity) {
        ActivationKey key = this.fetchActivationKey(activationKeyId);
        Pool pool = findPool(poolId);

        String message = activationKeyRules.validatePoolForActivationKey(key, pool, quantity);
        if (message != null) {
            throw new BadRequestException(message);
        }

        // Make sure we don't try to register the pool twice.
        if (key.hasPool(pool)) {
            throw new BadRequestException(
                i18n.tr("Pool ID \"{0}\" has already been registered with this activation key", poolId)
            );
        }

        key.addPool(pool, quantity);
        activationKeyCurator.update(key);

        return this.translator.translate(key, ActivationKeyDTO.class);
    }

    @Override
    public ActivationKeyDTO removePoolFromKey(@Verify(ActivationKey.class) String activationKeyId,
        @Verify(Pool.class) String poolId) {
        ActivationKey key = this.fetchActivationKey(activationKeyId);
        Pool pool = findPool(poolId);
        key.removePool(pool);
        activationKeyCurator.update(key);

        return this.translator.translate(key, ActivationKeyDTO.class);
    }

    public ActivationKeyDTO addProductIdToKey(@Verify(ActivationKey.class) String activationKeyId,
        String productId) {

        ActivationKey key = this.fetchActivationKey(activationKeyId);
        Product product = confirmProduct(key.getOwner(), productId);

        // Make sure we don't try to register the product ID twice.
        if (key.hasProduct(product)) {
            throw new BadRequestException(
                i18n.tr("Product ID \"{0}\" has already been registered with this activation key", productId)
            );
        }

        key.addProduct(product);
        activationKeyCurator.update(key);

        return this.translator.translate(key, ActivationKeyDTO.class);
    }

    @Override
    public ActivationKeyDTO removeProductIdFromKey(@Verify(ActivationKey.class) String activationKeyId,
        String productId) {
        ActivationKey key = this.fetchActivationKey(activationKeyId);
        Product product = confirmProduct(key.getOwner(), productId);
        key.removeProduct(product);
        activationKeyCurator.update(key);
        return this.translator.translate(key, ActivationKeyDTO.class);
    }

    @Override
    public CandlepinQuery<ActivationKeyDTO> findActivationKey() {
        CandlepinQuery<ActivationKey> query = this.activationKeyCurator.listAll();
        return this.translator.translateQuery(query, ActivationKeyDTO.class);
    }

    @Override
    public void deleteActivationKey(@Verify(ActivationKey.class) String activationKeyId) {
        ActivationKey key = this.fetchActivationKey(activationKeyId);
        log.debug("Deleting activation key: {}", activationKeyId);
        activationKeyCurator.delete(key);
    }

    @Override
    @SecurityHole
    public Iterable<ContentOverrideDTO> listActivationKeyContentOverrides(String activationKeyId) {
        Principal principal = ResteasyContext.getContextData(Principal.class);
        ActivationKey parent = this.verifyAndGetParent(activationKeyId, principal, Access.READ_ONLY);

        CandlepinQuery<ActivationKeyContentOverride> query = this.contentOverrideCurator.getList(parent);
        return this.translator.translateQuery(query, ContentOverrideDTO.class);
    }

    @Override
    @Transactional
    @SecurityHole
    public Iterable<ContentOverrideDTO> addActivationKeyContentOverrides(
        String activationKeyId, List<ContentOverrideDTO> entries) {
        Principal principal = ResteasyContext.getContextData(Principal.class);

        this.coValidator.validate(entries);
        ActivationKey parent = this.verifyAndGetParent(activationKeyId, principal, Access.ALL);
        try {
            for (ContentOverrideDTO dto : entries) {
                ActivationKeyContentOverride override = this.contentOverrideCurator
                    .retrieve(parent, dto.getContentLabel(), dto.getName());

                // We're counting on Hibernate to do our batching for us here...
                if (override != null) {
                    override.setValue(dto.getValue());
                    this.contentOverrideCurator.merge(override);
                }
                else {
                    override = new ActivationKeyContentOverride();

                    override.setParent(parent);
                    override.setContentLabel(dto.getContentLabel());
                    override.setName(dto.getName());
                    override.setValue(dto.getValue());

                    this.contentOverrideCurator.create(override);
                }
            }
        }
        catch (RuntimeException e) {
            // Make sure we clear all pending changes, since we don't want to risk storing only a
            // portion of the changes.
            this.contentOverrideCurator.clear();

            // Re-throw the exception
            throw e;
        }

        // Hibernate typically persists automatically before executing a query against a table with
        // pending changes, but if it doesn't, we can add a flush here to make sure this outputs the
        // correct values
        CandlepinQuery<ActivationKeyContentOverride> query = this.contentOverrideCurator.getList(parent);
        return this.translator.translateQuery(query, ContentOverrideDTO.class);
    }

    @Override
    @Transactional
    @SecurityHole
    public Iterable<ContentOverrideDTO> deleteActivationKeyContentOverrides(
        String activationKeyId, List<ContentOverrideDTO> entries) {
        Principal principal = ResteasyContext.getContextData(Principal.class);

        ActivationKey parent = this.verifyAndGetParent(activationKeyId, principal, Access.ALL);

        if (entries.size() == 0) {
            this.contentOverrideCurator.removeByParent(parent);
        }
        else {
            for (ContentOverrideDTO dto : entries) {
                String label = dto.getContentLabel();
                if (StringUtils.isBlank(label)) {
                    this.contentOverrideCurator.removeByParent(parent);
                }
                else {
                    String name = dto.getName();
                    if (StringUtils.isBlank(name)) {
                        this.contentOverrideCurator.removeByContentLabel(parent, dto.getContentLabel());
                    }
                    else {
                        this.contentOverrideCurator.removeByName(parent, dto.getContentLabel(), name);
                    }
                }
            }
        }

        CandlepinQuery<ActivationKeyContentOverride> query = this.contentOverrideCurator.getList(parent);
        return this.translator.translateQuery(query, ContentOverrideDTO.class);
    }

    private Pool findPool(String poolId) {
        Pool pool = poolManager.get(poolId);

        if (pool == null) {
            throw new BadRequestException(i18n.tr("Pool with id {0} could not be found.", poolId));
        }

        return pool;
    }

    private Product confirmProduct(Owner o, String prodId) {
        Product prod = this.ownerProductCurator.getProductById(o, prodId);

        if (prod == null) {
            throw new BadRequestException(i18n.tr("Product with id {0} could not be found.", prodId));
        }

        return prod;
    }

    private ActivationKey verifyAndGetParent(String parentId, Principal principal, Access access) {
        // Throws exception if criteria block the id
        ActivationKey result = this.fetchActivationKey(parentId);

        // Now that we know it exists, verify access level
        if (!principal.canAccess(result, SubResource.NONE, access)) {
            throw new ForbiddenException(i18n.tr("Insufficient permissions"));
        }

        return result;
    }

}
