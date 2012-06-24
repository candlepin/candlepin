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
package org.candlepin.client.model;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Set;

import org.candlepin.client.Constants;
import org.candlepin.client.cmds.Utils;

/**
 * The Class ProductCertificate.
 */
public class ProductCertificate extends AbstractCertificate {

    /**
     * Instantiates a new product certificate.
     *
     * @param certificate the certificate
     */
    public ProductCertificate(final X509Certificate certificate) {
        super(certificate);
    }

    public ProductCertificate() {
    }

    public List<Product> getProducts() {
        List<Product> products = Utils.newList();
        Extensions extensions = new Extensions(getX509Certificate(),
            Constants.PRODUCT_NAMESPACE);
        for (String productHash : findUniqueHashes(extensions,
            Constants.PRODUCT_NAMESPACE)) {
            products.add(new Product(extensions.branch(productHash),
                productHash));
        }
        return products;
    }

    public Product getProduct() {
        return getProducts().get(0);
    }

    /**
     * @param extensions
     * @return
     */
    protected Set<String> findUniqueHashes(Extensions extensions,
        String namespace) {
        Set<String> matches = extensions.find(".*1");
        Set<String> hashes = Utils.newSet();
        for (String match : matches) {
            String hash = match.substring(namespace.length() + 1, match
                .indexOf(".", namespace.length() + 1));
            hashes.add(hash);
        }
        return hashes;
    }

}
