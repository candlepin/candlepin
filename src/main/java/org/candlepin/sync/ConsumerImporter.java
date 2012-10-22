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

import java.io.IOException;
import java.io.Reader;

import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.xnap.commons.i18n.I18n;

/**
 * ConsumerImporter
 */
public class ConsumerImporter {
    private static Logger log = Logger.getLogger(ConsumerImporter.class);

    private OwnerCurator curator;
    private I18n i18n;

    public ConsumerImporter(OwnerCurator curator, I18n i18n) {
        this.curator = curator;
        this.i18n = i18n;
    }

    public ConsumerDto createObject(ObjectMapper mapper, Reader reader) throws IOException {
        return mapper.readValue(reader, ConsumerDto.class);
    }

    public void store(Owner owner, ConsumerDto consumer, ConflictOverrides forcedConflicts)
        throws SyncDataFormatException {

        if (consumer.getUuid() == null) {
            throw new SyncDataFormatException(i18n.tr("No ID for upstream distributor"));
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
                i18n.tr("This distributor has already been imported by another owner"));
        }

        if (owner.getUpstreamUuid() != null &&
            !owner.getUpstreamUuid().equals(consumer.getUuid())) {
            if (!forcedConflicts.isForced(Importer.Conflict.DISTRIBUTOR_CONFLICT)) {
                throw new ImportConflictException(
                    i18n.tr("Owner has already imported from another distributor"),
                    Importer.Conflict.DISTRIBUTOR_CONFLICT);
            }
            else {
                log.warn("Forcing import from a new distributor for org: " +
                        owner.getKey());
                log.warn("Old distributor UUID: " + owner.getUpstreamUuid());
                log.warn("New distributor UUID: " + consumer.getUuid());
            }
        }

        // TODO: need to determine what to do here
        log.error("FIX ME BEFORE COMMITTING");
        //owner.setUpstreamUuid(consumer.getUuid());
        curator.merge(owner);
    }

}
