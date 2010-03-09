/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.model;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;

import org.hibernate.criterion.Restrictions;

import com.wideplay.warp.persist.Transactional;

/**
 * ConsumerCurator
 */
public class ConsumerIdentityCertificateCurator extends
    AbstractHibernateCurator<ConsumerIdentityCertificate> {

    // private static Logger log = Logger
    // .getLogger(ConsumerIdentityCertificateCurator.class);

    protected ConsumerIdentityCertificateCurator() {
        super(ConsumerIdentityCertificate.class);
    }

    /**
     * @param updatedConsumer
     *            updated Consumer values.
     * @return Updated consumers
     */
    @Transactional
    public ConsumerIdentityCertificate update(
        ConsumerIdentityCertificate updated) {
        ConsumerIdentityCertificate existing = find(updated.getId());
        if (existing == null) {
            return create(updated);
        }
        existing.update(updated);
        save(existing);
        return existing;
    }

    /**
     * @param consumers
     *            consumers to update
     * @return updated consumers
     */
    @Transactional
    public Set<ConsumerIdentityCertificate> bulkUpdate(
        Set<ConsumerIdentityCertificate> consumerIdentityCertificates) {
        Set<ConsumerIdentityCertificate> toReturn = new HashSet<ConsumerIdentityCertificate>();
        for (ConsumerIdentityCertificate toUpdate : consumerIdentityCertificates) {
            toReturn.add(update(toUpdate));
        }
        return toReturn;
    }

    public ConsumerIdentityCertificate lookupBySerialNumber(
        BigInteger serialNumber) {
        return (ConsumerIdentityCertificate) currentSession().createCriteria(
            ConsumerIdentityCertificate.class).add(
            Restrictions.eq("serialNumber", serialNumber)).uniqueResult();
    }
}
