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
package org.candlepin.sync;

import org.candlepin.dto.manifest.v1.ConsumerDTO;
import org.candlepin.dto.manifest.v1.ConsumerTypeDTO;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.IdentityCertificateCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.UpstreamConsumer;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

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
        Owner alreadyUsing = curator.lookupWithUpstreamUuid(consumer.getUuid());
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

        if (owner.getUpstreamUuid() != null &&
            !owner.getUpstreamUuid().equals(consumer.getUuid())) {
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
            cs.setCollected(idcert.getSerial().isCollected());
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
        Owner consumerDTOOwner = new Owner();
        consumerDTOOwner.setId(consumer.getOwner());

        UpstreamConsumer uc = new UpstreamConsumer(consumer.getName(),
            consumerDTOOwner, type, consumer.getUuid());
        uc.setWebUrl(consumer.getUrlWeb());
        uc.setApiUrl(consumer.getUrlApi());
        uc.setIdCert(idcert);
        uc.setContentAccessMode(consumer.getContentAccessMode());
        owner.setUpstreamConsumer(uc);

        curator.merge(owner);
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
