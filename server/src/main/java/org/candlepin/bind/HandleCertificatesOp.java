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
package org.candlepin.bind;

import org.candlepin.controller.EntitlementCertificateGenerator;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.EntitlementCertificateCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.Product;

import com.google.inject.Inject;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * This bind operation is responsible to create certificates and regenerate
 * relevant certificates during a bind.
 *
 * TODO: either fetch ents to regenerate in preProcess and then mark them dirty in execute,
 * otherwise, write a query to directly update ents without fetching them, all in execute method.
 */
public class HandleCertificatesOp implements BindOperation {

    private EntitlementCertificateGenerator ecGenerator;
    private EntitlementCertificateCurator ecCurator;
    private EntitlementCurator eCurator;
    private Map<String, EntitlementCertificate> certs;
    private Collection<String> modifyingEnts;

    @Inject
    public HandleCertificatesOp(EntitlementCertificateGenerator ecGenerator, EntitlementCertificateCurator
        ecCurator, EntitlementCurator eCurator) {

        this.ecGenerator = ecGenerator;
        this.ecCurator = ecCurator;
        this.eCurator = eCurator;
    }

    /**
     * create certificates without associating the entitlements and certs with each other.
     * also, dont persist the certs.
     * @param context
     */
    @Override
    public boolean preProcess(BindContext context) {
        if (!context.getConsumerType().isType(ConsumerTypeEnum.SHARE)) {
            List<String> poolIds = new LinkedList<>();
            Map<String, Product> products = new HashMap<>();
            Map<String, PoolQuantity> poolQuantities = context.getPoolQuantities();

            for (PoolQuantity poolQuantity : poolQuantities.values()) {
                Pool pool = poolQuantity.getPool();
                products.put(pool.getId(), pool.getProduct());
                poolIds.add(pool.getId());
            }

            certs = ecGenerator.generateEntitlementCertificates(context.getConsumer(),
                products,
                poolQuantities,
                context.getEntitlementMap(),
                false);

            modifyingEnts = this.eCurator.getDependentEntitlementIdsForPools(context.getConsumer(), poolIds);
        }

        return true;
    }

    /**
     * we can now associate and persist the certs.
     * @param context
     */
    @Override
    public boolean execute(BindContext context) {
        if (!context.getConsumerType().isType(ConsumerTypeEnum.SHARE)) {
            Map<String, Entitlement> ents = context.getEntitlementMap();
            for (Entitlement ent: ents.values()) {
                EntitlementCertificate cert = certs.get(ent.getPool().getId());
                ent.addCertificate(cert);
                cert.setEntitlement(ent);
            }
            ecCurator.saveAll(certs.values(), false, false);
            eCurator.saveOrUpdateAll(ents.values(), false, false);
            this.ecGenerator.regenerateCertificatesByEntitlementIds(modifyingEnts, true);
        }
        return true;
    }
}
