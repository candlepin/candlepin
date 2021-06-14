/**
 * Copyright (c) 2009 - 2021 Red Hat, Inc.
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
package org.candlepin.hostedtest;

import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.model.CertificateInfo;
import org.candlepin.service.model.ProductInfo;

import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;



/**
 * The HostedTestProductServiceAdapter is a ProductServiceAdapter implementation backed by the
 * HostedTestDataStore upstream simulator.
 */
@Singleton
public class HostedTestProductServiceAdapter implements ProductServiceAdapter {

    private final HostedTestDataStore datastore;

    @Inject
    public HostedTestProductServiceAdapter(HostedTestDataStore datastore) {
        if (datastore == null) {
            throw new IllegalArgumentException("datastore is null");
        }

        this.datastore = datastore;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<? extends ProductInfo> getProductsByIds(String ownerKey, Collection<String> ids) {
        if (ownerKey != null && ids != null) {
            return this.datastore.listProducts()
                .stream()
                .filter(prod -> prod != null && ids.contains(prod.getId()))
                .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CertificateInfo getProductCertificate(String ownerKey, String productId) {
        return null;
    }

}
