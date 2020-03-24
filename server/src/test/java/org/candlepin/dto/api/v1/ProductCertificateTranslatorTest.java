/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
package org.candlepin.dto.api.v1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.Content;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCertificate;
import org.candlepin.model.ProductContent;


/**
 * Test suite for the CertificateTranslator class
 */
public class ProductCertificateTranslatorTest extends
    AbstractTranslatorTest<ProductCertificate, ProductCertificateDTO, ProductCertificateTranslator> {

    protected ProductCertificateTranslator translator = new ProductCertificateTranslator();

    protected ProductTranslatorTest productTranslatorTest = new ProductTranslatorTest();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        this.productTranslatorTest.initModelTranslator(modelTranslator);

        modelTranslator.registerTranslator(
            this.translator, ProductCertificate.class, ProductCertificateDTO.class);
        modelTranslator.registerTranslator(
            new ProductTranslator(), Product.class, ProductDTO.class);
        modelTranslator.registerTranslator(new ContentTranslator(), Content.class, ContentDTO.class);
        modelTranslator.registerTranslator(
            new ProductContentTranslator(), ProductContent.class, ProductContentDTO.class);
    }

    @Override
    protected ProductCertificateTranslator initObjectTranslator() {
        return this.translator;
    }

    @Override
    protected ProductCertificate initSourceObject() {
        ProductCertificate cert = new ProductCertificate();

        cert.setId("123");
        cert.setKey("cert_key");
        cert.setCert("cert_cert");
        cert.setProduct(this.productTranslatorTest.initSourceObject());

        return cert;
    }

    @Override
    protected ProductCertificateDTO initDestinationObject() {
        // Nothing fancy to do here.
        return new ProductCertificateDTO();
    }

    @Override
    protected void verifyOutput(
        ProductCertificate source, ProductCertificateDTO dest, boolean childrenGenerated) {
        if (source != null) {
            assertEquals(source.getId(), dest.getId());
            assertEquals(source.getKey(), dest.getKey());
            assertEquals(source.getCert(), dest.getCert());

            if (childrenGenerated) {
                this.productTranslatorTest.verifyOutput(source.getProduct(), dest.getProduct(), true);
            }
            else {
                assertNull(dest.getProduct());
            }
        }
        else {
            assertNull(dest);
        }
    }
}
