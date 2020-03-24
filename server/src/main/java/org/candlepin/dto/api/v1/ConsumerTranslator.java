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

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.ObjectTranslator;
import org.candlepin.dto.TimestampedEntityTranslator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerActivationKey;
import org.candlepin.model.ConsumerCapability;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Release;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * The ConsumerTranslator provides translation from Consumer model objects to
 * ConsumerDTOs
 */
public class ConsumerTranslator extends TimestampedEntityTranslator<Consumer, ConsumerDTO> {

    protected ConsumerTypeCurator consumerTypeCurator;
    protected EnvironmentCurator environmentCurator;
    private OwnerCurator ownerCurator;

    public ConsumerTranslator(ConsumerTypeCurator consumerTypeCurator,
        EnvironmentCurator environmentCurator, OwnerCurator ownerCurator) {

        if (consumerTypeCurator == null) {
            throw new IllegalArgumentException("ConsumerTypeCurator is null");
        }

        if (environmentCurator == null) {
            throw new IllegalArgumentException("environmentCurator is null");
        }

        if (ownerCurator == null) {
            throw new IllegalArgumentException("OwnerCurator is null");
        }

        this.ownerCurator = ownerCurator;
        this.consumerTypeCurator = consumerTypeCurator;
        this.environmentCurator = environmentCurator;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsumerDTO translate(Consumer source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsumerDTO translate(ModelTranslator translator, Consumer source) {
        return source != null ? this.populate(translator, source, new ConsumerDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsumerDTO populate(Consumer source, ConsumerDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsumerDTO populate(ModelTranslator translator, Consumer source, ConsumerDTO dest) {
        dest = super.populate(translator, source, dest);

        dest.setId(source.getId())
            .setUuid(source.getUuid())
            .setName(source.getName())
            .setUsername(source.getUsername())
            .setEntitlementStatus(source.getEntitlementStatus())
            .setServiceLevel(source.getServiceLevel())
            .setRole(source.getRole())
            .setUsage(source.getUsage())
            .setSystemPurposeStatus(source.getSystemPurposeStatus())
            .setAddOns(source.getAddOns())
            .setEntitlementCount(source.getEntitlementCount())
            .setFacts(source.getFacts())
            .setLastCheckin(source.getLastCheckin())
            .setCanActivate(source.isCanActivate())
            .setContentTags(source.getContentTags())
            .setAutoheal(source.isAutoheal())
            .setAnnotations(source.getAnnotations())
            .setContentAccessMode(source.getContentAccessMode());

        Release release = source.getReleaseVer();
        if (release != null) {
            dest.setReleaseVersion(release.getReleaseVer());
        }

        // Process nested objects if we have a ModelTranslator to use to the translation...
        if (translator != null) {
            if (StringUtils.isNotEmpty(source.getOwnerId())) {
                Owner owner = ownerCurator.findOwnerById(source.getOwnerId());
                dest.setOwner(owner != null ? translator.translate(owner, NestedOwnerDTO.class) : null);
            }

            Environment environment = this.environmentCurator.getConsumerEnvironment(source);
            dest.setEnvironment(translator.translate(environment, EnvironmentDTO.class));

            Set<ConsumerInstalledProduct> installedProducts = source.getInstalledProducts();
            if (installedProducts != null) {
                ObjectTranslator<ConsumerInstalledProduct, ConsumerInstalledProductDTO> cipTranslator =
                    translator.findTranslatorByClass(ConsumerInstalledProduct.class,
                    ConsumerInstalledProductDTO.class);
                Set<ConsumerInstalledProductDTO> ips = new HashSet<>();
                for (ConsumerInstalledProduct cip : installedProducts) {
                    if (cip != null) {
                        ConsumerInstalledProductDTO dto = cipTranslator.translate(translator, cip);
                        if (dto != null) {
                            ips.add(dto);
                        }
                    }
                }
                dest.setInstalledProducts(ips);
            }

            Set<ConsumerCapability> capabilities = source.getCapabilities();
            if (capabilities != null) {
                Set<CapabilityDTO> capabilitiesDTO = new HashSet<>();
                ObjectTranslator<ConsumerCapability, CapabilityDTO> capabilityTranslator =
                    translator.findTranslatorByClass(ConsumerCapability.class, CapabilityDTO.class);

                for (ConsumerCapability capability : capabilities) {
                    if (capability != null) {
                        CapabilityDTO dto = capabilityTranslator.translate(translator, capability);
                        if (dto != null) {
                            capabilitiesDTO.add(dto);
                        }
                    }
                }
                dest.setCapabilities(capabilitiesDTO);
            }

            Set<ConsumerActivationKey> keys = source.getActivationKeys();

            if (keys != null) {
                Set<ConsumerActivationKeyDTO> keysDTOSet = new HashSet<>();

                for (ConsumerActivationKey key : keys) {
                    keysDTOSet.add(new ConsumerActivationKeyDTO(key.getActivationKeyId(),
                        key.getActivationKeyName()));
                }

                dest.setActivationKeys(keysDTOSet);
            }

            // Temporary measure to maintain API compatibility
            if (source.getTypeId() != null) {
                ConsumerType ctype = this.consumerTypeCurator.getConsumerType(source);
                dest.setType(translator.translate(ctype, ConsumerTypeDTO.class));
            }
            else {
                dest.setType(null);
            }

            //This will put in the property so that the virtWho instances won't error
            dest.setGuestIds(new ArrayList<>());

            dest.setHypervisorId(translator.translate(source.getHypervisorId(), HypervisorIdDTO.class));
            dest.setIdCertificate(translator.translate(source.getIdCert(), CertificateDTO.class));
        }
        else {
            dest.setReleaseVersion(null);
            dest.setOwner(null);
            dest.setEnvironment(null);
            dest.setInstalledProducts(null);
            dest.setCapabilities(null);
            dest.setHypervisorId(null);
            dest.setType(null);
            dest.setIdCertificate(null);
        }

        return dest;
    }
}
