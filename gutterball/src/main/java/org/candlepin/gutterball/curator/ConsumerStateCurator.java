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

package org.candlepin.gutterball.curator;

import org.candlepin.gutterball.model.ConsumerState;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.hibernate.criterion.Restrictions;

import java.util.Date;

/**
 * A curator responsible for managing {@link ConsumerState} model objects.
 */
public class ConsumerStateCurator extends BaseCurator<ConsumerState> {

    @Inject
    protected ConsumerStateCurator() {
        super(ConsumerState.class);
    }

    public ConsumerState findByUuid(String uuid) {
        return (ConsumerState) this.currentSession()
            .createCriteria(ConsumerState.class)
            .add(Restrictions.eq("uuid", uuid))
            .setMaxResults(1)
            .uniqueResult();
    }

    @Transactional
    public void setConsumerDeleted(String uuid, Date deletedOn) {
        ConsumerState consumer = this.findByUuid(uuid);
        if (consumer == null) {
            // If consumer state didn't exist, we don't care.
            // The consumer may have already existed before
            // we started collecting data.
            return;
        }

        consumer.setDeleted(deletedOn);
        save(consumer);
    }

}
