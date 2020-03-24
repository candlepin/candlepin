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
package org.candlepin.resource;

import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.controller.CdnManager;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.v1.CdnDTO;
import org.candlepin.dto.api.v1.CertificateDTO;
import org.candlepin.dto.api.v1.CertificateSerialDTO;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Cdn;
import org.candlepin.model.CdnCertificate;
import org.candlepin.model.CdnCurator;
import org.candlepin.model.CertificateSerial;

import com.google.inject.Inject;

import org.xnap.commons.i18n.I18n;

import java.time.OffsetDateTime;
import java.util.Date;

/**
 * CdnResource
 */
public class CdnResource implements CdnApi {

    private I18n i18n;
    private CdnCurator curator;
    private CdnManager cdnManager;
    private ModelTranslator translator;

    @Inject
    public CdnResource(I18n i18n, CdnCurator curator, CdnManager manager, ModelTranslator translator) {
        this.i18n = i18n;
        this.curator = curator;
        this.cdnManager = manager;
        this.translator = translator;
    }

    @Override
    public CandlepinQuery<CdnDTO> getContentDeliveryNetworks() {
        return this.translator.translateQuery(curator.listAll(), CdnDTO.class);
    }

    @Override
    public void deleteCdn(String label) {
        Cdn cdn = curator.getByLabel(label);
        if (cdn != null) {
            cdnManager.deleteCdn(cdn);
        }
    }

    @Override
    public CdnDTO createCdn(CdnDTO cdnDTOInput) {
        Cdn existing = curator.getByLabel(cdnDTOInput.getLabel());
        if (existing != null) {
            throw new BadRequestException(i18n.tr(
                "A CDN with the label {0} already exists", cdnDTOInput.getLabel()));
        }

        Cdn cndToCreate = new Cdn();
        this.populateEntity(cndToCreate, cdnDTOInput);

        if (cdnDTOInput.getLabel() != null) {
            cndToCreate.setLabel(cdnDTOInput.getLabel());
        }

        return this.translator.translate(cdnManager.createCdn(cndToCreate), CdnDTO.class);
    }

    @Override
    public CdnDTO updateCdn(String label, CdnDTO cdnDTOInput) {
        Cdn existing = verifyAndLookupCdn(label);
        this.populateEntity(existing, cdnDTOInput);
        cdnManager.updateCdn(existing);
        return this.translator.translate(existing, CdnDTO.class);
    }

    /**
     * Populates the specified entity with data from the provided DTO.
     * This method will not set the ID field.
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
    private void populateEntity(Cdn entity, CdnDTO dto) {
        if (entity == null) {
            throw new IllegalArgumentException("the Cdn model entity is null");
        }

        if (dto == null) {
            throw new IllegalArgumentException("the Cdn dto is null");
        }

        if (dto.getName() != null) {
            entity.setName(dto.getName());
        }

        if (dto.getUrl() != null) {
            entity.setUrl(dto.getUrl());
        }

        if (dto.getCertificate() != null) {
            CertificateDTO certDTO = dto.getCertificate();
            CdnCertificate cdnCert;

            if (certDTO.getKey() != null && certDTO.getCert() != null) {
                cdnCert = new CdnCertificate();
                cdnCert.setCert(certDTO.getCert());
                cdnCert.setKey(certDTO.getKey());

                if (certDTO.getSerial() != null) {
                    CertificateSerialDTO certSerialDTO = certDTO.getSerial();
                    CertificateSerial certSerial = new CertificateSerial();

                    OffsetDateTime expiration = certSerialDTO.getExpiration();
                    certSerial.setExpiration(expiration != null ?
                        new Date(expiration.toInstant().toEpochMilli()) : null);

                    if (certSerialDTO.getSerial() != null) {
                        certSerial.setSerial(Long.valueOf(certSerialDTO.getSerial()));
                    }

                    if (certSerialDTO.getCollected() != null) {
                        certSerial.setCollected(certSerialDTO.getCollected());
                    }

                    if (certSerialDTO.getRevoked() != null) {
                        certSerial.setRevoked(certSerialDTO.getRevoked());
                    }

                    cdnCert.setSerial(certSerial);
                }
                entity.setCertificate(cdnCert);
            }
            else {
                throw new BadRequestException(i18n.tr("cdn certificate has null key or cert."));
            }
        }
    }

    private Cdn verifyAndLookupCdn(String label) {
        Cdn cdn = curator.getByLabel(label);

        if (cdn == null) {
            throw new NotFoundException(i18n.tr("No such content delivery network: {0}",
                label));
        }
        return cdn;
    }
}
