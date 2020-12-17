/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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

import org.candlepin.dto.api.v1.ActivationKeyDTO;
import org.candlepin.dto.api.v1.ActivationKeyTranslator;
import org.candlepin.dto.api.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.v1.AsyncJobStatusTranslator;
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
import org.candlepin.dto.api.v1.ComplianceReasonDTO;
import org.candlepin.dto.api.v1.ComplianceReasonTranslator;
import org.candlepin.dto.api.v1.ComplianceStatusDTO;
import org.candlepin.dto.api.v1.ComplianceStatusTranslator;
import org.candlepin.dto.api.v1.ConsumerInstalledProductDTO;
import org.candlepin.dto.api.v1.ConsumerInstalledProductTranslator;
import org.candlepin.dto.api.v1.ContentDTO;
import org.candlepin.dto.api.v1.ContentOverrideDTO;
import org.candlepin.dto.api.v1.ContentOverrideTranslator;
import org.candlepin.dto.api.v1.ContentTranslator;
import org.candlepin.dto.api.v1.DeletedConsumerDTO;
import org.candlepin.dto.api.v1.DeletedConsumerTranslator;
import org.candlepin.dto.api.v1.EnvironmentDTO;
import org.candlepin.dto.api.v1.EnvironmentTranslator;
import org.candlepin.dto.api.v1.GuestIdArrayElementTranslator;
import org.candlepin.dto.api.v1.GuestIdDTO;
import org.candlepin.dto.api.v1.GuestIdDTOArrayElement;
import org.candlepin.dto.api.v1.GuestIdTranslator;
import org.candlepin.dto.api.v1.HypervisorConsumerDTO;
import org.candlepin.dto.api.v1.HypervisorConsumerTranslator;
import org.candlepin.dto.api.v1.HypervisorIdDTO;
import org.candlepin.dto.api.v1.HypervisorIdTranslator;
import org.candlepin.dto.api.v1.ImportRecordDTO;
import org.candlepin.dto.api.v1.ImportRecordTranslator;
import org.candlepin.dto.api.v1.ImportUpstreamConsumerDTO;
import org.candlepin.dto.api.v1.ImportUpstreamConsumerTranslator;
import org.candlepin.dto.api.v1.NestedOwnerDTO;
import org.candlepin.dto.api.v1.NestedOwnerDTOTranslator;
import org.candlepin.dto.api.v1.NestedUpstreamConsumerDTO;
import org.candlepin.dto.api.v1.NestedUpstreamConsumerTranslator;
import org.candlepin.dto.api.v1.OwnerInfoTranslator;
import org.candlepin.dto.api.v1.PermissionBlueprintDTO;
import org.candlepin.dto.api.v1.PermissionBlueprintDTOTranslator;
import org.candlepin.dto.api.v1.PermissionBlueprintInfoTranslator;
import org.candlepin.dto.api.v1.PermissionBlueprintTranslator;
import org.candlepin.dto.api.v1.PoolQuantityDTO;
import org.candlepin.dto.api.v1.PoolQuantityTranslator;
import org.candlepin.dto.api.v1.ProductCertificateDTO;
import org.candlepin.dto.api.v1.ProductCertificateTranslator;
import org.candlepin.dto.api.v1.ProductDTO;
import org.candlepin.dto.api.v1.ProductTranslator;
import org.candlepin.dto.api.v1.RoleDTO;
import org.candlepin.dto.api.v1.RoleDTOTranslator;
import org.candlepin.dto.api.v1.RoleInfoTranslator;
import org.candlepin.dto.api.v1.RoleTranslator;
import org.candlepin.dto.api.v1.SystemPurposeComplianceStatusDTO;
import org.candlepin.dto.api.v1.SystemPurposeComplianceStatusTranslator;
import org.candlepin.dto.api.v1.UeberCertificateDTO;
import org.candlepin.dto.api.v1.UeberCertificateTranslator;
import org.candlepin.dto.api.v1.UpstreamConsumerDTO;
import org.candlepin.dto.api.v1.UpstreamConsumerDTOArrayElement;
import org.candlepin.dto.api.v1.UpstreamConsumerDTOArrayElementTranslator;
import org.candlepin.dto.api.v1.UpstreamConsumerTranslator;
import org.candlepin.dto.api.v1.UserDTO;
import org.candlepin.dto.api.v1.UserDTOTranslator;
import org.candlepin.dto.api.v1.UserInfoTranslator;
import org.candlepin.dto.api.v1.UserTranslator;
import org.candlepin.dto.shim.ContentDTOTranslator;
import org.candlepin.dto.shim.ContentDataTranslator;
import org.candlepin.dto.shim.ProductDTOTranslator;
import org.candlepin.dto.shim.ProductDataTranslator;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.Branding;
import org.candlepin.model.Cdn;
import org.candlepin.model.Certificate;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCapability;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Content;
import org.candlepin.model.ContentOverride;
import org.candlepin.model.DeletedConsumer;
import org.candlepin.model.DistributorVersion;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.GuestId;
import org.candlepin.model.HypervisorId;
import org.candlepin.model.ImportRecord;
import org.candlepin.model.ImportUpstreamConsumer;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.PermissionBlueprint;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCertificate;
import org.candlepin.model.Role;
import org.candlepin.model.UeberCertificate;
import org.candlepin.model.UpstreamConsumer;
import org.candlepin.model.User;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.dto.ContentData;
import org.candlepin.model.dto.ProductData;
import org.candlepin.policy.SystemPurposeComplianceStatus;
import org.candlepin.policy.js.compliance.ComplianceReason;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.service.model.OwnerInfo;
import org.candlepin.service.model.PermissionBlueprintInfo;
import org.candlepin.service.model.RoleInfo;
import org.candlepin.service.model.UserInfo;

import com.google.inject.Inject;

/**
 * The StandardTranslator is a SimpleModelTranslator that comes pre-configured to handle most, if
 * not all, existing translations.
 */
public class StandardTranslator extends SimpleModelTranslator {

    @Inject
    public StandardTranslator(ConsumerTypeCurator consumerTypeCurator,
        EnvironmentCurator environmentCurator, OwnerCurator ownerCurator) {

        // API translators
        /////////////////////////////////////////////
        this.registerTranslator(
            new ActivationKeyTranslator(), ActivationKey.class, ActivationKeyDTO.class);
        this.registerTranslator(
            new AsyncJobStatusTranslator(), AsyncJobStatus.class, AsyncJobStatusDTO.class);
        this.registerTranslator(
            new BrandingTranslator(), Branding.class, BrandingDTO.class);
        this.registerTranslator(
            new CapabilityTranslator(), ConsumerCapability.class, CapabilityDTO.class);
        this.registerTranslator(
            new CdnTranslator(), Cdn.class, CdnDTO.class);
        this.registerTranslator(
            new CertificateSerialTranslator(), CertificateSerial.class, CertificateSerialDTO.class);
        this.registerTranslator(
            new CertificateTranslator(), Certificate.class, CertificateDTO.class);
        this.registerTranslator(
            new ComplianceReasonTranslator(), ComplianceReason.class, ComplianceReasonDTO.class);
        this.registerTranslator(
            new ComplianceStatusTranslator(), ComplianceStatus.class, ComplianceStatusDTO.class);
        this.registerTranslator(
            new org.candlepin.dto.api.v1.ConsumerTranslator(
            consumerTypeCurator, environmentCurator, ownerCurator),
            Consumer.class, org.candlepin.dto.api.v1.ConsumerDTO.class);
        this.registerTranslator(
            new ConsumerInstalledProductTranslator(), ConsumerInstalledProduct.class,
            ConsumerInstalledProductDTO.class);
        this.registerTranslator(
            new org.candlepin.dto.api.v1.ConsumerTypeTranslator(),
            ConsumerType.class, org.candlepin.dto.api.v1.ConsumerTypeDTO.class);
        this.registerTranslator(
            new ContentTranslator(), Content.class, ContentDTO.class);
        this.registerTranslator(
            new ContentOverrideTranslator(), ContentOverride.class, ContentOverrideDTO.class);
        this.registerTranslator(
            new DeletedConsumerTranslator(), DeletedConsumer.class, DeletedConsumerDTO.class);
        this.registerTranslator(
            new org.candlepin.dto.api.v1.DistributorVersionTranslator(),
            DistributorVersion.class, org.candlepin.dto.api.v1.DistributorVersionDTO.class);
        this.registerTranslator(
            new org.candlepin.dto.api.v1.EntitlementTranslator(),
            Entitlement.class, org.candlepin.dto.api.v1.EntitlementDTO.class);
        this.registerTranslator(
            new EnvironmentTranslator(), Environment.class, EnvironmentDTO.class);
        this.registerTranslator(
            new GuestIdTranslator(), GuestId.class, GuestIdDTO.class);
        this.registerTranslator(
            new GuestIdArrayElementTranslator(), GuestId.class, GuestIdDTOArrayElement.class);
        this.registerTranslator(
            new HypervisorConsumerTranslator(ownerCurator), Consumer.class, HypervisorConsumerDTO.class);
        this.registerTranslator(
            new HypervisorIdTranslator(), HypervisorId.class, HypervisorIdDTO.class);
        this.registerTranslator(
            new ImportRecordTranslator(), ImportRecord.class, ImportRecordDTO.class);
        this.registerTranslator(new ImportUpstreamConsumerTranslator(),
            ImportUpstreamConsumer.class, ImportUpstreamConsumerDTO.class);
        this.registerTranslator(
            new org.candlepin.dto.api.v1.NestedOwnerTranslator(),
            Owner.class, org.candlepin.dto.api.v1.NestedOwnerDTO.class);
        this.registerTranslator(
            new NestedOwnerDTOTranslator(), NestedOwnerDTO.class, Owner.class);
        this.registerTranslator(
            new org.candlepin.dto.api.v1.OwnerTranslator(),
            Owner.class, org.candlepin.dto.api.v1.OwnerDTO.class);
        this.registerTranslator(
            new OwnerInfoTranslator(), OwnerInfo.class, org.candlepin.dto.api.v1.OwnerDTO.class);
        this.registerTranslator(
            new PermissionBlueprintTranslator(), PermissionBlueprint.class, PermissionBlueprintDTO.class);
        this.registerTranslator(
            new PermissionBlueprintDTOTranslator(), PermissionBlueprintDTO.class, PermissionBlueprint.class);
        this.registerTranslator(
            new PermissionBlueprintInfoTranslator(),
            PermissionBlueprintInfo.class, PermissionBlueprintDTO.class);
        this.registerTranslator(
            new org.candlepin.dto.api.v1.PoolTranslator(),
            Pool.class, org.candlepin.dto.api.v1.PoolDTO.class);
        this.registerTranslator(
            new PoolQuantityTranslator(), PoolQuantity.class, PoolQuantityDTO.class);
        this.registerTranslator(
            new ProductTranslator(), Product.class, ProductDTO.class);
        this.registerTranslator(
            new ProductCertificateTranslator(), ProductCertificate.class, ProductCertificateDTO.class);
        this.registerTranslator(
            new RoleTranslator(), Role.class, RoleDTO.class);
        this.registerTranslator(
            new RoleDTOTranslator(), RoleDTO.class, Role.class);
        this.registerTranslator(
            new RoleInfoTranslator(), RoleInfo.class, RoleDTO.class);
        this.registerTranslator(
            new SystemPurposeComplianceStatusTranslator(), SystemPurposeComplianceStatus.class,
            SystemPurposeComplianceStatusDTO.class);
        this.registerTranslator(
            new UeberCertificateTranslator(), UeberCertificate.class, UeberCertificateDTO.class);
        this.registerTranslator(
            new UpstreamConsumerTranslator(), UpstreamConsumer.class, UpstreamConsumerDTO.class);
        this.registerTranslator(
            new UpstreamConsumerDTOArrayElementTranslator(),
            UpstreamConsumer.class, UpstreamConsumerDTOArrayElement.class);
        this.registerTranslator(
            new NestedUpstreamConsumerTranslator(),
            UpstreamConsumer.class, NestedUpstreamConsumerDTO.class);
        this.registerTranslator(
            new UserTranslator(), User.class, UserDTO.class);
        this.registerTranslator(
            new UserDTOTranslator(), UserDTO.class, User.class);
        this.registerTranslator(
            new UserInfoTranslator(), UserInfo.class, UserDTO.class);

        // Manifest import/export translators
        /////////////////////////////////////////////
        this.registerTranslator(
            new org.candlepin.dto.manifest.v1.BrandingTranslator(),
            Branding.class, org.candlepin.dto.manifest.v1.BrandingDTO.class);
        this.registerTranslator(
            new org.candlepin.dto.manifest.v1.CdnTranslator(),
            Cdn.class, org.candlepin.dto.manifest.v1.CdnDTO.class);
        this.registerTranslator(
            new org.candlepin.dto.manifest.v1.CertificateTranslator(),
            Certificate.class, org.candlepin.dto.manifest.v1.CertificateDTO.class);
        this.registerTranslator(
            new org.candlepin.dto.manifest.v1.CertificateSerialTranslator(),
            CertificateSerial.class, org.candlepin.dto.manifest.v1.CertificateSerialDTO.class);
        this.registerTranslator(
            new org.candlepin.dto.manifest.v1.ConsumerTranslator(consumerTypeCurator, ownerCurator),
            Consumer.class, org.candlepin.dto.manifest.v1.ConsumerDTO.class);
        this.registerTranslator(
            new org.candlepin.dto.manifest.v1.ConsumerTypeTranslator(),
            ConsumerType.class, org.candlepin.dto.manifest.v1.ConsumerTypeDTO.class);
        this.registerTranslator(
            new org.candlepin.dto.manifest.v1.ContentTranslator(),
            Content.class, org.candlepin.dto.manifest.v1.ContentDTO.class);
        this.registerTranslator(
            new org.candlepin.dto.manifest.v1.DistributorVersionTranslator(),
            DistributorVersion.class, org.candlepin.dto.manifest.v1.DistributorVersionDTO.class);
        this.registerTranslator(
            new org.candlepin.dto.manifest.v1.EntitlementTranslator(),
            Entitlement.class, org.candlepin.dto.manifest.v1.EntitlementDTO.class);
        this.registerTranslator(
            new org.candlepin.dto.manifest.v1.OwnerTranslator(),
            Owner.class, org.candlepin.dto.manifest.v1.OwnerDTO.class);
        this.registerTranslator(
            new org.candlepin.dto.manifest.v1.PoolTranslator(),
            Pool.class, org.candlepin.dto.manifest.v1.PoolDTO.class);
        this.registerTranslator(
            new org.candlepin.dto.manifest.v1.ProductTranslator(),
            Product.class, org.candlepin.dto.manifest.v1.ProductDTO.class);
        this.registerTranslator(
            new org.candlepin.dto.manifest.v1.UpstreamConsumerTranslator(),
            UpstreamConsumer.class, org.candlepin.dto.manifest.v1.UpstreamConsumerDTO.class);

        // Shims
        /////////////////////////////////////////////
        // These are temporary translators to ease the transition from the first gen DTOs
        // (ProductData and ContentData) to the second gen DTOs.
        this.registerTranslator(
            new ContentDataTranslator(), ContentData.class, ContentDTO.class);
        this.registerTranslator(
            new ProductDataTranslator(), ProductData.class, ProductDTO.class);
        this.registerTranslator(
            new ContentDTOTranslator(), org.candlepin.dto.manifest.v1.ContentDTO.class, ContentData.class);
        this.registerTranslator(
            new ProductDTOTranslator(), org.candlepin.dto.manifest.v1.ProductDTO.class, ProductData.class);

        // Rules framework translators
        /////////////////////////////////////////////
        this.registerTranslator(
            new org.candlepin.dto.rules.v1.ActivationKeyTranslator(),
            ActivationKey.class, org.candlepin.dto.rules.v1.ActivationKeyDTO.class);
        this.registerTranslator(
            new org.candlepin.dto.rules.v1.ComplianceReasonTranslator(),
            ComplianceReason.class, org.candlepin.dto.rules.v1.ComplianceReasonDTO.class);
        this.registerTranslator(
            new org.candlepin.dto.rules.v1.ComplianceStatusTranslator(),
            ComplianceStatus.class, org.candlepin.dto.rules.v1.ComplianceStatusDTO.class);
        this.registerTranslator(
            new org.candlepin.dto.rules.v1.ConsumerTranslator(consumerTypeCurator, ownerCurator),
            Consumer.class, org.candlepin.dto.rules.v1.ConsumerDTO.class);
        this.registerTranslator(
            new org.candlepin.dto.rules.v1.ConsumerTypeTranslator(),
            ConsumerType.class, org.candlepin.dto.rules.v1.ConsumerTypeDTO.class);
        this.registerTranslator(
            new org.candlepin.dto.rules.v1.EntitlementTranslator(),
            Entitlement.class, org.candlepin.dto.rules.v1.EntitlementDTO.class);
        this.registerTranslator(
            new org.candlepin.dto.rules.v1.GuestIdTranslator(),
            GuestId.class, org.candlepin.dto.rules.v1.GuestIdDTO.class);
        this.registerTranslator(
            new org.candlepin.dto.rules.v1.OwnerTranslator(),
            Owner.class, org.candlepin.dto.rules.v1.OwnerDTO.class);
        this.registerTranslator(
            new org.candlepin.dto.rules.v1.PoolTranslator(),
            Pool.class, org.candlepin.dto.rules.v1.PoolDTO.class);
    }

    // Nothing else to do here.
}
