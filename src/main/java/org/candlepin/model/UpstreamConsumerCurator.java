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
package org.candlepin.model;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.candlepin.auth.interceptor.EnforceAccessControl;
import org.candlepin.exceptions.BadRequestException;
import org.xnap.commons.i18n.I18n;

/**
 * UpstreamConsumerCurator
 */
public class UpstreamConsumerCurator extends AbstractHibernateCurator<UpstreamConsumer> {
    @Inject private I18n i18n;
    public static final int NAME_LENGTH = 250;

    /**
     * @param entityType
     */
    protected UpstreamConsumerCurator() {
        super(UpstreamConsumer.class);
    }

    @Transactional
    @EnforceAccessControl
    @Override
    public UpstreamConsumer create(UpstreamConsumer entity) {
        entity.ensureUUID();
        validate(entity);
        return super.create(entity);
    }

    @Transactional
    @EnforceAccessControl
    public void delete(UpstreamConsumer entity) {
        // save off the ids before we delete
        DeletedConsumer dc = new DeletedConsumer(entity.getUuid(),
            entity.getOwner().getId());

        super.delete(entity);

//        DeletedConsumer existing = deletedConsumerCurator.
//                    findByConsumerUuid(dc.getConsumerUuid());
//        if (existing != null) {
//            // update the owner ID in case the same UUID was specified by two owners
//            existing.setOwnerId(dc.getOwnerId());
//            existing.setUpdated(new Date());
//            deletedConsumerCurator.save(existing);
//        }
//        else {
//            deletedConsumerCurator.create(dc);
//        }
    }

    protected void validate(UpstreamConsumer entity) {
        // #TODO Look at generic validation framework
        if ((entity.getName() != null) &&
            (entity.getName().length() >= NAME_LENGTH)) {
            throw new BadRequestException(i18n.tr(
                "Name of the upstream consumer should be shorter than {0} characters.",
                NAME_LENGTH));
        }
    }
}
