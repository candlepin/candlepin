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
package org.candlepin.dto;

import org.candlepin.dto.api.v1.CertificateSerialDTO;
import org.candlepin.dto.api.v1.CertificateSerialTranslator;
import org.candlepin.dto.api.v1.CertificateDTO;
import org.candlepin.dto.api.v1.CertificateTranslator;
import org.candlepin.dto.api.v1.ConsumerTypeDTO;
import org.candlepin.dto.api.v1.ConsumerTypeTranslator;
import org.candlepin.dto.api.v1.ContentDTO;
import org.candlepin.dto.api.v1.ContentTranslator;
import org.candlepin.dto.api.v1.OwnerDTO;
import org.candlepin.dto.api.v1.OwnerTranslator;
import org.candlepin.dto.api.v1.ProductDTO;
import org.candlepin.dto.api.v1.ProductTranslator;
import org.candlepin.dto.api.v1.UpstreamConsumerDTO;
import org.candlepin.dto.api.v1.UpstreamConsumerTranslator;
import org.candlepin.dto.shim.ContentDataTranslator;
import org.candlepin.dto.shim.ProductDataTranslator;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.Certificate;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.model.UpstreamConsumer;
import org.candlepin.model.dto.ContentData;
import org.candlepin.model.dto.ProductData;



/**
 * The StandardTranslator is a SimpleModelTranslator that comes pre-configured to handle most, if
 * not all, existing translations.
 */
public class StandardTranslator extends SimpleModelTranslator {

    public StandardTranslator() {
        // API translators
        /////////////////////////////////////////////
        this.registerTranslator(
            new CertificateSerialTranslator(), CertificateSerial.class, CertificateSerialDTO.class);
        this.registerTranslator(
            new CertificateTranslator(), Certificate.class, CertificateDTO.class);
        this.registerTranslator(
            new ConsumerTypeTranslator(), ConsumerType.class, ConsumerTypeDTO.class);
        this.registerTranslator(
            new ContentTranslator(), Content.class, ContentDTO.class);
        this.registerTranslator(
            new OwnerTranslator(), Owner.class, OwnerDTO.class);
        this.registerTranslator(
            new ProductTranslator(), Product.class, ProductDTO.class);
        this.registerTranslator(
            new UpstreamConsumerTranslator(), UpstreamConsumer.class, UpstreamConsumerDTO.class);

        // Shims
        /////////////////////////////////////////////
        // These are temporary translators to ease the transition from the first gen DTOs
        // (ProductData and ContentData) to the second gen DTOs.
        this.registerTranslator(
            new ContentDataTranslator(), ContentData.class, ContentDTO.class);
        this.registerTranslator(
            new ProductDataTranslator(), ProductData.class, ProductDTO.class);
    }

    // Nothing else to do here.
}
