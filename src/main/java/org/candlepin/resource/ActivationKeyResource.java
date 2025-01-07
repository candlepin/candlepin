/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
import org.candlepin.auth.Verify;
import org.candlepin.controller.PoolService;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.server.v1.ActivationKeyDTO;
import org.candlepin.dto.api.server.v1.ContentOverrideDTO;
import org.candlepin.dto.api.server.v1.PoolDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.model.ContentOverride;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.Release;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyContentOverride;
import org.candlepin.model.activationkeys.ActivationKeyContentOverrideCurator;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.policy.activationkey.ActivationKeyRules;
import org.candlepin.resource.server.v1.ActivationKeyApi;
import org.candlepin.resource.validation.DTOValidator;
import org.candlepin.util.ContentOverrideValidator;
import org.candlepin.util.ServiceLevelValidator;
import org.candlepin.util.TransformedIterator;

import com.google.inject.persist.Transactional;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.inject.Inject;



public class ActivationKeyResource implements ActivationKeyApi {
    private static final Logger log = LoggerFactory.getLogger(ActivationKeyResource.class);
    private static final Pattern AK_CHAR_FILTER = Pattern.compile("^[a-zA-Z0-9_-]+$");

    private final ActivationKeyCurator activationKeyCurator;
    private final ActivationKeyContentOverrideCurator contentOverrideCurator;
    private final ProductCurator productCurator;
    private final PoolService poolService;
    private final I18n i18n;
    private final ServiceLevelValidator serviceLevelValidator;
    private final ActivationKeyRules activationKeyRules;
    private final ModelTranslator translator;
    private final DTOValidator dtoValidator;
    private final ContentOverrideValidator coValidator;

    @Inject
    public ActivationKeyResource(ActivationKeyCurator activationKeyCurator, I18n i18n,
        PoolService poolService, ServiceLevelValidator serviceLevelValidator,
        ActivationKeyRules activationKeyRules, ProductCurator productCurator,
        ModelTranslator translator, DTOValidator dtoValidator,
        ActivationKeyContentOverrideCurator contentOverrideCurator, ContentOverrideValidator coValidator) {

        this.activationKeyCurator = Objects.requireNonNull(activationKeyCurator);
        this.i18n = Objects.requireNonNull(i18n);
        this.poolService = Objects.requireNonNull(poolService);
        this.serviceLevelValidator = Objects.requireNonNull(serviceLevelValidator);
        this.activationKeyRules = Objects.requireNonNull(activationKeyRules);
        this.productCurator = Objects.requireNonNull(productCurator);
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
    @Transactional
    public ActivationKeyDTO getActivationKey(@Verify(ActivationKey.class) String activationKeyId) {
        ActivationKey key = this.fetchActivationKey(activationKeyId);
        return this.translator.translate(key, ActivationKeyDTO.class);
    }

    @Override
    @Transactional
    public Iterable<PoolDTO> getActivationKeyPools(@Verify(ActivationKey.class) String activationKeyId) {
        ActivationKey key = this.fetchActivationKey(activationKeyId);
        return () -> new TransformedIterator<>(key.getPools().iterator(),
            akp -> translator.translate(akp.getPool(), PoolDTO.class));
    }

    @Override
    @Transactional
    public ActivationKeyDTO updateActivationKey(
        @Verify(value = ActivationKey.class, require = Access.ALL) String activationKeyId,
        ActivationKeyDTO update) {

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
        this.activationKeyCurator.flush();

        return this.translator.translate(toUpdate, ActivationKeyDTO.class);
    }

    @Override
    @Transactional
    public ActivationKeyDTO addPoolToKey(
        @Verify(value = ActivationKey.class, require = Access.ALL) String activationKeyId,
        @Verify(value = Pool.class, require = Access.READ_ONLY) String poolId,
        Long quantity) {

        ActivationKey key = this.fetchActivationKey(activationKeyId);

        // If we're running in SCA mode, don't allow adding (more) pools to the keys,
        // because owner should see all the pools anyway
        Owner owner = key.getOwner();
        if (owner != null && owner.isUsingSimpleContentAccess()) {
            throw new BadRequestException(i18n.tr("Pools cannot be added to activation keys while" +
                " the org is operating in simple content access mode"));
        }

        Pool pool = findPool(poolId);

        String message = activationKeyRules.validatePoolForActivationKey(key, pool, quantity);
        if (message != null) {
            throw new BadRequestException(message);
        }

        // Make sure we don't try to register the pool twice.
        if (key.hasPool(pool)) {
            throw new BadRequestException(
                i18n.tr("Pool ID \"{0}\" has already been registered with this activation key", poolId));
        }

        key.addPool(pool, quantity);
        // We have to manually invoke the "on update" here due to a strange quirk in the way Hibernate
        // handles element collections with respect to persist and update hooks. Namely, it won't invoke
        // them on the parent entity when the only change made is on the collection.
        key.setUpdated(new Date());
        key = this.activationKeyCurator.merge(key);
        this.activationKeyCurator.flush();

        return this.translator.translate(key, ActivationKeyDTO.class);
    }

    @Override
    @Transactional
    public ActivationKeyDTO removePoolFromKey(
        @Verify(value = ActivationKey.class, require = Access.ALL) String activationKeyId,
        @Verify(value = Pool.class, require = Access.READ_ONLY) String poolId) {

        ActivationKey key = this.fetchActivationKey(activationKeyId);
        Pool pool = findPool(poolId);

        if (key.removePool(pool)) {
            // We have to manually invoke the "on update" here due to a strange quirk in the way Hibernate
            // handles element collections with respect to persist and update hooks. Namely, it won't invoke
            // them on the parent entity when the only change made is on the collection.
            key.setUpdated(new Date());
            this.activationKeyCurator.merge(key);
            this.activationKeyCurator.flush();
        }

        return this.translator.translate(key, ActivationKeyDTO.class);
    }

    @Override
    @Transactional
    public ActivationKeyDTO addProductIdToKey(
        @Verify(value = ActivationKey.class, require = Access.ALL) String activationKeyId,
        String productId) {

        ActivationKey key = this.fetchActivationKey(activationKeyId);
        Product product = confirmProduct(key.getOwner(), productId);

        // Make sure we don't try to register the product ID twice.
        if (key.hasProduct(product)) {
            throw new BadRequestException(
                i18n.tr("Product ID \"{0}\" has already been registered with this activation key",
                    productId));
        }

        if (key.addProduct(product)) {
            // We have to manually invoke the "on update" here due to a strange quirk in the way Hibernate
            // handles element collections with respect to persist and update hooks. Namely, it won't invoke
            // them on the parent entity when the only change made is on the collection.
            key.setUpdated(new Date());
            key = this.activationKeyCurator.merge(key);
            this.activationKeyCurator.flush();
        }

        return this.translator.translate(key, ActivationKeyDTO.class);
    }

    @Override
    @Transactional
    public ActivationKeyDTO removeProductIdFromKey(
        @Verify(value = ActivationKey.class, require = Access.ALL) String activationKeyId,
        String productId) {

        ActivationKey key = this.fetchActivationKey(activationKeyId);
        Product product = confirmProduct(key.getOwner(), productId);

        if (key.removeProduct(product)) {
            // We have to manually invoke the "on update" here due to a strange quirk in the way Hibernate
            // handles element collections with respect to persist and update hooks. Namely, it won't invoke
            // them on the parent entity when the only change made is on the collection.
            key.setUpdated(new Date());
            key = this.activationKeyCurator.merge(key);
            this.activationKeyCurator.flush();
        }

        return this.translator.translate(key, ActivationKeyDTO.class);
    }

    @Override
    @Transactional
    public Stream<ActivationKeyDTO> findActivationKey() {
        return this.activationKeyCurator.listAll()
            .stream()
            .map(this.translator.getStreamMapper(ActivationKey.class, ActivationKeyDTO.class));
    }

    @Override
    @Transactional
    public void deleteActivationKey(@Verify(ActivationKey.class) String activationKeyId) {
        ActivationKey key = this.fetchActivationKey(activationKeyId);
        log.debug("Deleting activation key: {}", activationKeyId);
        activationKeyCurator.delete(key);
    }

    @Override
    @Transactional
    public Stream<ContentOverrideDTO> listActivationKeyContentOverrides(
        @Verify(value = ActivationKey.class, require = Access.READ_ONLY) String activationKeyId) {

        ActivationKey key = this.fetchActivationKey(activationKeyId);

        return this.contentOverrideCurator.getList(key)
            .stream()
            .map(this.translator.getStreamMapper(ContentOverride.class, ContentOverrideDTO.class));
    }

    @Override
    @Transactional
    public Stream<ContentOverrideDTO> addActivationKeyContentOverrides(
        @Verify(value = ActivationKey.class, require = Access.ALL) String activationKeyId,
        List<ContentOverrideDTO> entries) {

        this.coValidator.validate(entries);
        ActivationKey key = this.fetchActivationKey(activationKeyId);

        List<ActivationKeyContentOverride> overrides = entries.stream()
            .map(dto -> new ActivationKeyContentOverride()
                .setKey(key)
                .setContentLabel(dto.getContentLabel())
                .setName(dto.getName())
                .setValue(dto.getValue()))
            .toList();

        Map<String, Map<String, ActivationKeyContentOverride>> overrideMap = this.contentOverrideCurator
            .retrieveAll(key, overrides);

        try {
            for (ActivationKeyContentOverride inbound : overrides) {
                ActivationKeyContentOverride existing = overrideMap
                    .getOrDefault(inbound.getContentLabel(), Map.of())
                    .get(inbound.getName());

                if (existing != null) {
                    existing.setValue(inbound.getValue());
                    this.contentOverrideCurator.merge(existing);
                }
                else {
                    this.contentOverrideCurator.create(inbound);
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

        key.setUpdated(new Date());
        this.activationKeyCurator.flush();

        return this.contentOverrideCurator.getList(key)
            .stream()
            .map(this.translator.getStreamMapper(ContentOverride.class,
                ContentOverrideDTO.class));
    }

    @Override
    @Transactional
    public Stream<ContentOverrideDTO> deleteActivationKeyContentOverrides(
        @Verify(value = ActivationKey.class, require = Access.ALL) String activationKeyId,
        List<ContentOverrideDTO> entries) {

        ActivationKey key = this.fetchActivationKey(activationKeyId);

        if (entries.size() == 0) {
            this.contentOverrideCurator.removeByParent(key);
        }
        else {
            for (ContentOverrideDTO dto : entries) {
                String label = dto.getContentLabel();
                if (StringUtils.isBlank(label)) {
                    this.contentOverrideCurator.removeByParent(key);
                }
                else {
                    String name = dto.getName();
                    if (StringUtils.isBlank(name)) {
                        this.contentOverrideCurator.removeByContentLabel(key, label);
                    }
                    else {
                        this.contentOverrideCurator.removeByName(key, label, name);
                    }
                }
            }
        }

        key.setUpdated(new Date());
        this.activationKeyCurator.flush();

        return this.contentOverrideCurator.getList(key)
            .stream()
            .map(this.translator.getStreamMapper(ContentOverride.class, ContentOverrideDTO.class));
    }

    private Pool findPool(String poolId) {
        Pool pool = this.poolService.get(poolId);

        if (pool == null) {
            throw new BadRequestException(i18n.tr("Pool with id {0} could not be found.", poolId));
        }

        return pool;
    }

    private Product confirmProduct(Owner owner, String prodId) {
        Product prod = this.productCurator.resolveProductId(owner.getKey(), prodId);

        if (prod == null) {
            throw new BadRequestException(i18n.tr("Product with id {0} could not be found.", prodId));
        }

        return prod;
    }

}
