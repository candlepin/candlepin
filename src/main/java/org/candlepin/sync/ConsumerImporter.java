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

import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.IdentityCertificateCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.UpstreamConsumer;

import org.codehaus.jackson.map.ObjectMapper;
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

    public ConsumerImporter(OwnerCurator curator, IdentityCertificateCurator idCertCurator,
        I18n i18n, CertificateSerialCurator serialCurator) {
        this.curator = curator;
        this.idCertCurator = idCertCurator;
        this.i18n = i18n;
        this.serialCurator = serialCurator;
    }

    public ConsumerDto createObject(ObjectMapper mapper, Reader reader) throws IOException {
        return mapper.readValue(reader, ConsumerDto.class);
    }

    public void store(Owner owner, ConsumerDto consumer,
        ConflictOverrides forcedConflicts, IdentityCertificate idcert)
        throws SyncDataFormatException {

        if (consumer.getUuid() == null) {
            throw new SyncDataFormatException(i18n.tr("No ID for " +
                    "upstream subscription management application."));
        }

        // Make sure no other owner is already using this upstream UUID:
        Owner alreadyUsing = curator.lookupWithUpstreamUuid(consumer.getUuid());
        if (alreadyUsing != null && !alreadyUsing.getKey().equals(owner.getKey())) {
            log.error("Cannot import manifest for org: " + owner.getKey());
            log.error("Upstream distributor " + consumer.getUuid() +
                " already in use by org: " + alreadyUsing.getKey());

            // NOTE: this is not a conflict that can be overridden because we simply don't
            // allow two orgs to use the same manifest at once. The other org would have to
            // delete their manifest after which it could be used elsewhere.
            throw new SyncDataFormatException(
                i18n.tr("This subscription management application has " +
                    "already been imported by another owner."));
        }

        if (owner.getUpstreamUuid() != null &&
            !owner.getUpstreamUuid().equals(consumer.getUuid())) {
            if (!forcedConflicts.isForced(Importer.Conflict.DISTRIBUTOR_CONFLICT)) {
                throw new ImportConflictException(
                    i18n.tr("Owner has already imported from another " +
                        "subscription management application."),
                    Importer.Conflict.DISTRIBUTOR_CONFLICT);
            }
            else {
                log.warn("Forcing import from a new distributor for org: " +
                        owner.getKey());
                log.warn("Old distributor UUID: " + owner.getUpstreamUuid());
                log.warn("New distributor UUID: " + consumer.getUuid());
            }
        }

        /*
         * WARNING: Strange quirk here, we create a certificate serial object
         * here which does not match the actual serial of the identity
         * certificate. Presumably this is to prevent potential conflicts with
         * a serial that came from somewhere else. This is consistent with
         * importing entitlement certs (as subscription certs).
         */
        if (idcert != null) {
            CertificateSerial cs = new CertificateSerial();
            cs.setCollected(idcert.getSerial().isCollected());
            cs.setExpiration(idcert.getSerial().getExpiration());
            cs.setRevoked(idcert.getSerial().isRevoked());
            cs.setUpdated(idcert.getSerial().getUpdated());
            cs.setCreated(idcert.getSerial().getCreated());
            serialCurator.create(cs);

            idcert.setId(null);
            idcert.setSerial(cs);
            idCertCurator.create(idcert);
        }

        // create an UpstreamConsumer from the imported ConsumerDto
        UpstreamConsumer uc = new UpstreamConsumer(consumer.getName(),
            consumer.getOwner(), consumer.getType(), consumer.getUuid());
        uc.setWebUrl(consumer.getUrlWeb());
        uc.setApiUrl(consumer.getUrlApi());
        uc.setIdCert(idcert);
        owner.setUpstreamConsumer(uc);

        curator.merge(owner);
    }

}
