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
package org.fedoraproject.candlepin.model;

import org.fedoraproject.candlepin.service.ProductServiceAdapter;

import com.google.inject.Inject;
import com.google.inject.Provider;

import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;

import java.util.List;

import javax.persistence.EntityManager;

/**
 * OwnerInfoCurator
 */

public class OwnerInfoCurator {
    private Provider<EntityManager> entityManager;
    private ConsumerTypeCurator consumerTypeCurator;
    private ProductServiceAdapter productAdapter;
    private static final String DEFAULT_CONSUMER_TYPE = "system";

    @Inject
    public OwnerInfoCurator(Provider<EntityManager> entityManager,
        ConsumerTypeCurator consumerTypeCurator, ProductServiceAdapter psa) {
        this.entityManager = entityManager;
        this.consumerTypeCurator = consumerTypeCurator;
        this.productAdapter = psa;
    }

    public OwnerInfo lookupByOwner(Owner owner) {
        OwnerInfo info = new OwnerInfo();

        List<ConsumerType> types = consumerTypeCurator.listAll();
        for (ConsumerType type : types) {
            Criteria c = currentSession().createCriteria(Consumer.class)
                .add(Restrictions.eq("owner", owner))
                .add(Restrictions.eq("type", type));
            c.setProjection(Projections.rowCount());
            int consumers = (Integer) c.uniqueResult();

            c = currentSession().createCriteria(Entitlement.class)
                .setProjection(Projections.sum("quantity"))
                .createCriteria("consumer")
                .add(Restrictions.eq("owner", owner))
                .add(Restrictions.eq("type", type));

            // If there's no rows summed, quantity returns null.
            Object result = c.uniqueResult();
            int entitlements = 0;
            if (result != null) {
                entitlements = (Integer) result;
            }

            info.addTypeTotal(type, consumers, entitlements);
        }

        info.setConsumerTypesByPool(consumerTypeCurator.listAll());
        for (Pool p : owner.getPools()) {
            String conType = p.getAttributeValue("requires_consumer_type");
            if (conType == null || conType.trim().equals("")) {
                String prodId = p.getProductId();
                Product prod = productAdapter.getProductById(prodId);
                if (prod != null) {
                    conType = prod.getAttributeValue("requires_consumer_type");
                }
            }
            if (conType == null || conType.trim().equals("")) {
                conType = DEFAULT_CONSUMER_TYPE;
            }
            ConsumerType ct = consumerTypeCurator.lookupByLabel(conType);
            info.addToConsumerTypeCountByPool(ct);
        }

        return info;
    }

    protected Session currentSession() {
        Session sess = (Session) entityManager.get().getDelegate();
        return sess;
    }
}
