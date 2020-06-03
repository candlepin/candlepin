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
import org.candlepin.model.Certificate;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.util.Util;

import java.util.Collections;
import java.util.Set;

/**
 * The EntitlementTranslator provides translation from Entitlement model objects to EntitlementDTOs.
 */
public class EntitlementTranslator implements ObjectTranslator<Entitlement, EntitlementDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public EntitlementDTO translate(Entitlement source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntitlementDTO translate(ModelTranslator translator, Entitlement source) {
        return source != null ? this.populate(translator, source, new EntitlementDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntitlementDTO populate(Entitlement source, EntitlementDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntitlementDTO populate(ModelTranslator modelTranslator, Entitlement source, EntitlementDTO dest) {

        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("dest is null");
        }

        dest.created(Util.toDateTime(source.getCreated()))
            .updated(Util.toDateTime(source.getUpdated()))
            .id(source.getId())
            .quantity(source.getQuantity())
            .deletedFromPool(source.deletedFromPool())
            .startDate(Util.toDateTime(source.getStartDate()))
            .endDate(Util.toDateTime(source.getEndDate()));

        if (modelTranslator != null) {
            Owner owner = source.getOwner();
            dest.setOwner(owner != null ?
                modelTranslator.translate(owner, NestedOwnerDTO.class) : null);

            Pool pool = source.getPool();
            dest.setPool(pool != null ?
                modelTranslator.translate(pool, PoolDTO.class) : null);

            Consumer consumer = source.getConsumer();
            dest.setConsumer(createNestedConsumer(consumer));

            Set<EntitlementCertificate> certs = source.getCertificates();

            if (certs != null && !certs.isEmpty()) {
                for (Certificate cert : certs) {
                    if (cert != null) {
                        dest.addCertificates(modelTranslator.translate(cert, CertificateDTO.class));
                    }
                }
            }
            else {
                dest.setCertificates(Collections.emptySet());
            }
        }

        return dest;
    }

    private NestedConsumerDTO createNestedConsumer(Consumer source) {
        if (source == null) {
            return null;
        }
        return new NestedConsumerDTO()
            .id(source.getId())
            .uuid(source.getUuid())
            .name(source.getName())
            .href(source.getHref());
    }
}
