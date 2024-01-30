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
package org.candlepin.model;

import org.hibernate.criterion.Restrictions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;


@Singleton
public class ProductCertificateCurator extends AbstractHibernateCurator<ProductCertificate> {
    private static final Logger log = LoggerFactory.getLogger(ProductCertificateCurator.class);

    @Inject
    public ProductCertificateCurator() {
        super(ProductCertificate.class);
    }

    public ProductCertificate findForProduct(Product product) {
        log.debug("Finding cert for product: {}", product);

        return (ProductCertificate) currentSession()
            .createCriteria(ProductCertificate.class)
            .add(Restrictions.eq("product", product))
            .uniqueResult();
    }

}
