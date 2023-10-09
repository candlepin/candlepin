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
package org.candlepin.controller;

import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.model.CertificateInfo;
import org.candlepin.service.model.ProductInfo;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;



class MockProductServiceAdapter implements ProductServiceAdapter {
    private Map<String, ProductInfo> pmap;

    public MockProductServiceAdapter(Collection<? extends ProductInfo> pinfo) {
        this.pmap = (pinfo != null ? pinfo.stream() : Stream.<ProductInfo>empty())
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(ProductInfo::getId, Function.identity()));
    }

    public MockProductServiceAdapter(ProductInfo... pinfo) {
        this(pinfo != null ? List.of(pinfo) : null);
    }

    @Override
    public Collection<? extends ProductInfo> getProductsByIds(String ownerKey, Collection<String> ids) {
        return (ids != null ? ids.stream() : Stream.empty())
            .map(this.pmap::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    @Override
    public CertificateInfo getProductCertificate(String ownerKey, String productId) {
        return null;
    }
}
