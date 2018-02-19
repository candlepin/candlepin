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

import org.candlepin.audit.Event;
import org.candlepin.dto.api.v1.ActivationKeyDTO;
import org.candlepin.dto.api.v1.ActivationKeyTranslator;
import org.candlepin.dto.api.v1.BrandingDTO;
import org.candlepin.dto.api.v1.BrandingTranslator;
import org.candlepin.dto.api.v1.CapabilityDTO;
import org.candlepin.dto.api.v1.CapabilityTranslator;
import org.candlepin.dto.api.v1.CdnDTO;
import org.candlepin.dto.api.v1.CdnTranslator;
import org.candlepin.dto.api.v1.CertificateDTO;
import org.candlepin.dto.api.v1.CertificateSerialDTO;
import org.candlepin.dto.api.v1.CertificateSerialTranslator;
import org.candlepin.dto.api.v1.CertificateTranslator;
import org.candlepin.dto.api.v1.ConsumerInstalledProductDTO;
import org.candlepin.dto.api.v1.ConsumerInstalledProductTranslator;
import org.candlepin.dto.api.v1.ContentDTO;
import org.candlepin.dto.api.v1.ContentTranslator;
import org.candlepin.dto.api.v1.EntitlementDTO;
import org.candlepin.dto.api.v1.EntitlementTranslator;
import org.candlepin.dto.api.v1.EnvironmentDTO;
import org.candlepin.dto.api.v1.EnvironmentTranslator;
import org.candlepin.dto.api.v1.EventDTO;
import org.candlepin.dto.api.v1.EventTranslator;
import org.candlepin.dto.api.v1.GuestIdDTO;
import org.candlepin.dto.api.v1.GuestIdTranslator;
import org.candlepin.dto.api.v1.HypervisorIdDTO;
import org.candlepin.dto.api.v1.HypervisorIdTranslator;
import org.candlepin.dto.api.v1.JobStatusDTO;
import org.candlepin.dto.api.v1.JobStatusTranslator;
import org.candlepin.dto.api.v1.ProductCertificateDTO;
import org.candlepin.dto.api.v1.ProductCertificateTranslator;
import org.candlepin.dto.api.v1.ProductDTO;
import org.candlepin.dto.api.v1.ProductTranslator;
import org.candlepin.dto.api.v1.UpstreamConsumerDTO;
import org.candlepin.dto.api.v1.UpstreamConsumerTranslator;
import org.candlepin.dto.shim.ContentDataTranslator;
import org.candlepin.dto.shim.ProductDataTranslator;
import org.candlepin.model.Branding;
import org.candlepin.model.Cdn;
import org.candlepin.model.Certificate;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCapability;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Content;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Environment;
import org.candlepin.model.GuestId;
import org.candlepin.model.HypervisorId;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCertificate;
import org.candlepin.model.UpstreamConsumer;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.dto.ContentData;
import org.candlepin.model.dto.ProductData;
import org.candlepin.pinsetter.core.model.JobStatus;

/**
 * The StandardTranslator is a SimpleModelTranslator that comes pre-configured to handle most, if
 * not all, existing translations.
 */
public class StandardTranslator extends SimpleModelTranslator {

    public StandardTranslator() {
        // API translators
        /////////////////////////////////////////////
        this.registerTranslator(
            new ActivationKeyTranslator(), ActivationKey.class, ActivationKeyDTO.class);
        this.registerTranslator(
            new CapabilityTranslator(), ConsumerCapability.class, CapabilityDTO.class);
        this.registerTranslator(
            new CertificateSerialTranslator(), CertificateSerial.class, CertificateSerialDTO.class);
        this.registerTranslator(
            new CertificateTranslator(), Certificate.class, CertificateDTO.class);
        this.registerTranslator(
            new ConsumerInstalledProductTranslator(), ConsumerInstalledProduct.class,
            ConsumerInstalledProductDTO.class);
        this.registerTranslator(
            new org.candlepin.dto.api.v1.ConsumerTranslator(),
            Consumer.class, org.candlepin.dto.api.v1.ConsumerDTO.class);
        this.registerTranslator(
            new org.candlepin.dto.api.v1.ConsumerTypeTranslator(),
            ConsumerType.class, org.candlepin.dto.api.v1.ConsumerTypeDTO.class);
        this.registerTranslator(
            new ContentTranslator(), Content.class, ContentDTO.class);
        this.registerTranslator(
            new EnvironmentTranslator(), Environment.class, EnvironmentDTO.class);
        this.registerTranslator(
            new GuestIdTranslator(), GuestId.class, GuestIdDTO.class);
        this.registerTranslator(
            new HypervisorIdTranslator(), HypervisorId.class, HypervisorIdDTO.class);
        this.registerTranslator(
            new org.candlepin.dto.api.v1.OwnerTranslator(),
            Owner.class, org.candlepin.dto.api.v1.OwnerDTO.class);
        this.registerTranslator(
            new ProductTranslator(), Product.class, ProductDTO.class);
        this.registerTranslator(
            new org.candlepin.dto.api.v1.PoolTranslator(),
            Pool.class, org.candlepin.dto.api.v1.PoolDTO.class);
        this.registerTranslator(
            new BrandingTranslator(), Branding.class, BrandingDTO.class);
        this.registerTranslator(
            new EntitlementTranslator(), Entitlement.class, EntitlementDTO.class);
        this.registerTranslator(
            new UpstreamConsumerTranslator(), UpstreamConsumer.class, UpstreamConsumerDTO.class);
        this.registerTranslator(
            new CdnTranslator(), Cdn.class, CdnDTO.class);
        this.registerTranslator(
            new JobStatusTranslator(), JobStatus.class, JobStatusDTO.class);
        this.registerTranslator(
            new ProductCertificateTranslator(), ProductCertificate.class, ProductCertificateDTO.class);

        // Event translators
        /////////////////////////////////////////////
        this.registerTranslator(
            new EventTranslator(), Event.class, EventDTO.class);

        // Shims
        /////////////////////////////////////////////
        // These are temporary translators to ease the transition from the first gen DTOs
        // (ProductData and ContentData) to the second gen DTOs.
        this.registerTranslator(
            new ContentDataTranslator(), ContentData.class, ContentDTO.class);
        this.registerTranslator(
            new ProductDataTranslator(), ProductData.class, ProductDTO.class);

        // Rules framework translators
        /////////////////////////////////////////////
        this.registerTranslator(
            new org.candlepin.dto.rules.v1.ConsumerTranslator(),
            Consumer.class, org.candlepin.dto.rules.v1.ConsumerDTO.class);
        this.registerTranslator(
            new org.candlepin.dto.rules.v1.ConsumerTypeTranslator(),
            ConsumerType.class, org.candlepin.dto.rules.v1.ConsumerTypeDTO.class);
        this.registerTranslator(
            new org.candlepin.dto.rules.v1.OwnerTranslator(),
            Owner.class, org.candlepin.dto.rules.v1.OwnerDTO.class);
        this.registerTranslator(
            new org.candlepin.dto.rules.v1.PoolTranslator(),
            Pool.class, org.candlepin.dto.rules.v1.PoolDTO.class);
    }

    // Nothing else to do here.
}
