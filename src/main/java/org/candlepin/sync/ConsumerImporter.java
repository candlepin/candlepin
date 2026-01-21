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
package org.candlepin.sync;

import org.candlepin.dto.manifest.v1.ConsumerDTO;
import org.candlepin.dto.manifest.v1.ConsumerTypeDTO;
import org.candlepin.dto.manifest.v1.OwnerDTO;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.IdentityCertificateCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.UpstreamConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.Reader;


/**
 * ConsumerImporter
 */
public class ConsumerImporter {
    private static Logger log = LoggerFactory.getLogger(ConsumerImporter.class);

    private OwnerCurator curator;
    private IdentityCertificateCurator idCertCurator;
    private I18n i18n;
    private CertificateSerialCurator serialCurator;

    public ConsumerImporter(OwnerCurator curator, IdentityCertificateCurator idCertCurator, I18n i18n,
        CertificateSerialCurator serialCurator) {

        this.curator = curator;
        this.idCertCurator = idCertCurator;
        this.i18n = i18n;
        this.serialCurator = serialCurator;
    }

    public ConsumerDTO createObject(ObjectMapper mapper, Reader reader) throws IOException {
        return mapper.readValue(reader, ConsumerDTO.class);
    }

    public void store(Owner owner, ConsumerDTO consumer, ConflictOverrides forcedConflicts,
        IdentityCertificate idcert) throws SyncDataFormatException {

        if (consumer.getUuid() == null) {
            throw new SyncDataFormatException(
                i18n.tr("No ID for upstream subscription management application."));
        }

        // Make sure no other owner is already using this upstream UUID:
        Owner alreadyUsing = curator.getByUpstreamUuid(consumer.getUuid());
        if (alreadyUsing != null && !alreadyUsing.getKey().equals(owner.getKey())) {
            log.error("Cannot import manifest for org: {}", owner.getKey());
            log.error("Upstream distributor {} already in used by org: {}",
                consumer.getUuid(), alreadyUsing.getKey());

            // NOTE: this is not a conflict that can be overridden because we simply don't
            // allow two orgs to use the same manifest at once. The other org would have to
            // delete their manifest after which it could be used elsewhere.
            throw new SyncDataFormatException(i18n.tr(
                "This subscription management application has already been imported by another owner."));
        }

        String upstreamUuid = owner.getUpstreamUuid();
        if (upstreamUuid != null && !upstreamUuid.equals(consumer.getUuid())) {
            if (!forcedConflicts.isForced(Importer.Conflict.DISTRIBUTOR_CONFLICT)) {
                throw new ImportConflictException(
                    i18n.tr("Owner has already imported from another subscription management application."),
                    Importer.Conflict.DISTRIBUTOR_CONFLICT);
            }
            else {
                log.warn("Forcing import from a new distributor for org: {}", owner.getKey());
                log.warn("Old distributor UUID: {}", owner.getUpstreamUuid());
                log.warn("New distributor UUID: {}", consumer.getUuid());
            }
        }

        /*
         * WARNING: Strange quirk here, we create a certificate serial object here which does not
         * match the actual serial of the identity certificate. Presumably this is to prevent
         * potential conflicts with a serial that came from somewhere else. This is consistent with
         * importing entitlement certs (as subscription certs).
         */
        if (idcert != null) {
            CertificateSerial cs = new CertificateSerial();
            cs.setExpiration(idcert.getSerial().getExpiration());
            cs.setUpdated(idcert.getSerial().getUpdated());
            cs.setCreated(idcert.getSerial().getCreated());
            serialCurator.create(cs);

            idcert.setId(null);
            idcert.setSerial(cs);
            idCertCurator.create(idcert);
        }

        // create an UpstreamConsumer from the imported ConsumerDto
        ConsumerType type = new ConsumerType();
        populateEntity(type, consumer.getType());

        Owner ownerToUse = new Owner();
        if (consumer.getOwner() != null) {
            populateEntity(ownerToUse, consumer.getOwner());
        }

        UpstreamConsumer uc = new UpstreamConsumer(consumer.getName(), ownerToUse, type, consumer.getUuid());
        uc.setWebUrl(consumer.getUrlWeb());
        uc.setApiUrl(consumer.getUrlApi());
        uc.setIdCert(idcert);
        uc.setContentAccessMode(consumer.getContentAccessMode());
        owner.setUpstreamConsumer(uc);

        curator.merge(owner);
    }

    /**
     * Populates the specified entity with data from the provided DTO.
     * This method does not set the upstreamConsumer field.
     *
     * @param entity
     *  The entity instance to populate
     *
     * @param dto
     *  The DTO containing the data with which to populate the entity
     *
     * @throws IllegalArgumentException
     *  if either entity or dto are null
     */
    protected void populateEntity(Owner entity, OwnerDTO dto) {
        if (entity == null) {
            throw new IllegalArgumentException("the owner model entity is null");
        }

        if (dto == null) {
            throw new IllegalArgumentException("the owner dto is null");
        }

        if (dto.getId() != null) {
            entity.setId(dto.getId());
        }

        if (dto.getDisplayName() != null) {
            entity.setDisplayName(dto.getDisplayName());
        }

        if (dto.getKey() != null) {
            entity.setKey(dto.getKey());
        }

        if (dto.getLastRefreshed() != null) {
            entity.setLastRefreshed(dto.getLastRefreshed());
        }

        if (dto.getContentAccessMode() != null) {
            entity.setContentAccessMode(dto.getContentAccessMode());
        }

        if (dto.getContentAccessModeList() != null) {
            entity.setContentAccessModeList(dto.getContentAccessModeList());
        }

        if (dto.getCreated() != null) {
            entity.setCreated(dto.getCreated());
        }

        if (dto.getUpdated() != null) {
            entity.setUpdated(dto.getUpdated());
        }

        if (dto.getParentOwner() != null) {
            // Impl note:
            // We do not allow modifying a parent owner through its children, so all we'll do here
            // is set the parent owner and ignore everything else; including further nested owners.

            OwnerDTO pdto = dto.getParentOwner();
            Owner parent = null;

            if (pdto.getId() != null) {
                // look up by ID
                parent = this.curator.get(pdto.getId());
            }
            else if (pdto.getKey() != null) {
                // look up by key
                parent = this.curator.getByKey(pdto.getKey());
            }

            if (parent == null) {
                throw new NotFoundException(i18n.tr("Unable to find parent owner: {0}", pdto));
            }

            entity.setParentOwner(parent);
        }

        if (dto.getContentPrefix() != null) {
            entity.setContentPrefix(dto.getContentPrefix());
        }

        if (dto.getDefaultServiceLevel() != null) {
            entity.setDefaultServiceLevel(dto.getDefaultServiceLevel());
        }

        if (dto.getLogLevel() != null) {
            entity.setLogLevel(dto.getLogLevel());
        }

        if (dto.isAutobindDisabled() != null) {
            entity.setAutobindDisabled(dto.isAutobindDisabled());
        }

        if (dto.isAutobindHypervisorDisabled() != null) {
            entity.setAutobindHypervisorDisabled(dto.isAutobindHypervisorDisabled());
        }

    }

    /**
     * Populates the specified entity with data from the provided DTO.
     *
     * @param entity
     *  The entity instance to populate
     *
     * @param dto
     *  The DTO containing the data with which to populate the entity
     *
     * @throws IllegalArgumentException
     *  if either entity or dto are null
     */
    protected void populateEntity(ConsumerType entity, ConsumerTypeDTO dto) {
        if (entity == null) {
            throw new IllegalArgumentException("the consumer type model entity is null");
        }

        if (dto == null) {
            throw new IllegalArgumentException("the consumer type dto is null");
        }

        if (dto.getId() != null) {
            entity.setId(dto.getId());
        }

        if (dto.getLabel() != null) {
            entity.setLabel(dto.getLabel());
        }

        if (dto.isManifest() != null) {
            entity.setManifest(dto.isManifest());
        }
        else {
            entity.setManifest(false);
        }
    }
}
