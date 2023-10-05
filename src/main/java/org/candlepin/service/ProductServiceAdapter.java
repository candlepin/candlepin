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
package org.candlepin.service;

import org.candlepin.service.exception.product.ProductUnknownRetrievalException;
import org.candlepin.service.model.CertificateInfo;
import org.candlepin.service.model.ProductInfo;

import java.util.Collection;



/**
 * Product data may originate from a separate service outside Candlepin in some
 * configurations. This interface defines the operations Candlepin requires
 * related to Product data. Different implementations can handle whether or not
 * this info comes from Candlepin's DB or from a separate service.
 */
public interface ProductServiceAdapter {

    /**
     * Query a list of products matching the given string IDs. Only the products
     * found will be returned. When no matching products are found, an empty List
     * will be returned.
     *
     * When this method is called by candlepin, candlepin has already verified that
     * the specified owner is known to candlepin and should not be null. If an
     * implementation receives a null owner, a RuntimeException should be thrown.
     * If the owner is unknown to the service, an empty list of products should
     * be returned.
     *
     * If the ids param is null or empty, an empty list of products will be
     * returned.
     *
     * @param ownerKey the owner/org in which to search for products
     * @param ids list of product ids
     * @return list of products matching the given string IDs,
     *         empty list if none were found.
     */
    Collection<? extends ProductInfo> getProductsByIds(String ownerKey, Collection<String> ids)
        throws ProductUnknownRetrievalException;

    /**
     * Gets the certificate that defines the given product, creating one
     * if necessary. If the implementation does not support product certificates
     * for some reason, null can be returned instead of creating a new one.
     *
     * @param ownerKey the owner/org in which to search for products
     * @param productId the ID of the source product of the certificate
     * @return the stored or created {@link ProductCertificate}
     */
    CertificateInfo getProductCertificate(String ownerKey, String productId);

}
