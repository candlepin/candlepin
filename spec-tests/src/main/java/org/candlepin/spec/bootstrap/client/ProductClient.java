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

package org.candlepin.spec.bootstrap.client;

import org.candlepin.ApiClient;
import org.candlepin.ApiException;
import org.candlepin.dto.api.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.v1.OwnerDTO;
import org.candlepin.dto.api.v1.ProductCertificateDTO;
import org.candlepin.dto.api.v1.ProductDTO;
import org.candlepin.resource.ProductsApi;

import java.util.List;

public class ProductClient extends ProductsApi {

    public ProductClient(ApiClient client) {
        super(client);
    }

    @Override
    public ProductDTO getProduct(String productUuid) throws ApiException {
        if (productUuid == null || productUuid.length() == 0) {
            throw new IllegalArgumentException("Product Uuid must not be null or empty.");
        }

        return super.getProduct(productUuid);
    }

    @Override
    public ProductCertificateDTO getProductCertificate(String productUuid) throws ApiException {
        if (productUuid == null || productUuid.length() == 0) {
            throw new IllegalArgumentException("Product Uuid must not be null or empty.");
        }

        return super.getProductCertificate(productUuid);
    }

    @Override
    public List<OwnerDTO> getProductOwners(List<String> productUuids) throws ApiException {
        if (productUuids == null) {
            throw new IllegalArgumentException("Product Uuids must not be null.");
        }

        return super.getProductOwners(productUuids);
    }

    @Override
    public AsyncJobStatusDTO refreshPoolsForProducts(List<String> productUuids, Boolean lazyRegen)
        throws ApiException {
        if (productUuids == null) {
            throw new IllegalArgumentException("Product Uuids must not be null");
        }

        return super.refreshPoolsForProducts(productUuids, lazyRegen);
    }
}
