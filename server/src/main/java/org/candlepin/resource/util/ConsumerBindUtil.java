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
package org.candlepin.resource.util;

import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.ForbiddenException;
import org.candlepin.controller.Entitler;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerContentOverride;
import org.candlepin.model.ConsumerContentOverrideCurator;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.Release;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyContentOverride;
import org.candlepin.model.activationkeys.ActivationKeyPool;
import org.candlepin.policy.js.quantity.QuantityRules;
import org.candlepin.policy.js.quantity.SuggestedQuantity;
import org.candlepin.resource.dto.AutobindData;
import org.candlepin.util.ServiceLevelValidator;
import org.candlepin.version.CertVersionConflictException;

import com.google.inject.Inject;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Responsible for handling activation keys for the Consumer creation in  {@link ConsumerResource}
 */
public class ConsumerBindUtil {

    private Entitler entitler;
    private I18n i18n;
    private ConsumerContentOverrideCurator consumerContentOverrideCurator;
    private QuantityRules quantityRules;
    private ServiceLevelValidator serviceLevelValidator;
    private static Logger log = LoggerFactory.getLogger(ConsumerBindUtil.class);

    @Inject
    public ConsumerBindUtil(Entitler entitler,
                            I18n i18n,
                            ConsumerContentOverrideCurator consumerContentOverrideCurator,
                            QuantityRules quantityRules,
                            ServiceLevelValidator serviceLevelValidator) {
        this.entitler = entitler;
        this.i18n = i18n;
        this.consumerContentOverrideCurator = consumerContentOverrideCurator;
        this.quantityRules = quantityRules;
        this.serviceLevelValidator = serviceLevelValidator;
    }

    public void handleActivationKeys(Consumer consumer, List<ActivationKey> keys) {
        // Process activation keys.

        boolean listSuccess = false;
        for (ActivationKey key : keys) {
            boolean keySuccess = true;
            handleActivationKeyOverrides(consumer, key.getContentOverrides());
            handleActivationKeyRelease(consumer, key.getReleaseVer());
            keySuccess &= handleActivationKeyServiceLevel(consumer, key.getServiceLevel(), key.getOwner());
            if (key.isAutoAttach() != null && key.isAutoAttach()) {
                handleActivationKeyAutoBind(consumer, key);
            }
            else {
                keySuccess &= handleActivationKeyPools(consumer, key);
            }
            listSuccess |= keySuccess;
        }
        if (!listSuccess) {
            throw new BadRequestException(i18n.tr("No activation key was applied successfully."));
        }
    }

    private boolean handleActivationKeyPools(Consumer consumer,
        ActivationKey key) {
        if (key.getPools().size() == 0) {
            return true;
        }
        boolean onePassed = false;
        List<ActivationKeyPool> toBind = new LinkedList<ActivationKeyPool>();
        for (ActivationKeyPool akp : key.getPools()) {
            if (akp.getPool().getId() != null) {
                toBind.add(akp);
            }
        }

        // Sort pools before binding to avoid deadlocks
        Collections.sort(toBind);
        for (ActivationKeyPool akp : toBind) {
            int quantity = (akp.getQuantity() == null) ?
                getQuantityToBind(akp.getPool(), consumer) :
                    akp.getQuantity().intValue();
            try {
                entitler.sendEvents(entitler.bindByPoolQuantity(consumer, akp.getPool().getId(), quantity));
                onePassed = true;
            }
            catch (ForbiddenException e) {
                log.warn(i18n.tr("Cannot bind to pool ''{0}'' in activation key ''{1}'': {2}",
                        akp.getPool().getId(), akp.getKey().getName(), e.getMessage()));
            }
        }
        return onePassed;
    }

    private void handleActivationKeyAutoBind(Consumer consumer, ActivationKey key) {
        try {
            Set<String> productIds = new HashSet<String>();
            List<String> poolIds = new ArrayList<String>();

            for (Product akp : key.getProducts()) {
                productIds.add(akp.getId());
            }
            for (ConsumerInstalledProduct cip : consumer.getInstalledProducts()) {
                productIds.add(cip.getProductId());
            }
            for (ActivationKeyPool p : key.getPools()) {
                poolIds.add(p.getPool().getId());
            }
            AutobindData autobindData = AutobindData.create(consumer)
                    .forProducts(productIds.toArray(new String[0]))
                    .withPools(poolIds);
            List<Entitlement> ents = entitler.bindByProducts(autobindData);
            entitler.sendEvents(ents);
        }
        catch (ForbiddenException fe) {
            throw fe;
        }
        catch (CertVersionConflictException cvce) {
            throw cvce;
        }
        catch (RuntimeException re) {
            log.warn("Unable to attach a subscription for a product that " +
                "has no pool: " + re.getMessage());
        }
    }

    private void handleActivationKeyOverrides(Consumer consumer,
            Set<ActivationKeyContentOverride> overrides) {
        for (ActivationKeyContentOverride akco : overrides) {
            ConsumerContentOverride consumerOverride =
                akco.buildConsumerContentOverride(consumer);
            this.consumerContentOverrideCurator.addOrUpdate(consumer, consumerOverride);
        }
    }

    private void handleActivationKeyRelease(Consumer consumer, Release release) {
        String relVerString = release.getReleaseVer();
        if (relVerString != null && !relVerString.isEmpty()) {
            consumer.setReleaseVer(release);
        }
    }

    private boolean handleActivationKeyServiceLevel(Consumer consumer,
            String level, Owner owner) {
        if (!StringUtils.isBlank(level)) {
            try {
                serviceLevelValidator.validate(owner, level);
                consumer.setServiceLevel(level);
                return true;
            }
            catch (BadRequestException bre) {
                log.warn(bre.getMessage());
                return false;
            }
        }
        else  {
            return true;
        }
    }

    public int getQuantityToBind(Pool pool, Consumer consumer) {
        Date now = new Date();
        // If the pool is being attached in the future, calculate
        // suggested quantity on the start date
        Date onDate = now.before(pool.getStartDate()) ?
            pool.getStartDate() : now;
        SuggestedQuantity suggested = quantityRules.getSuggestedQuantity(pool,
            consumer, onDate);
        int quantity = Math.max(suggested.getIncrement().intValue(),
            suggested.getSuggested().intValue());
        //It's possible that increment is greater than the number available
        //but whatever we do here, the bind will fail
        return quantity;
    }

    public void validateServiceLevel(Owner owner, String serviceLevel) {
        serviceLevelValidator.validate(owner, serviceLevel);
    }

}
