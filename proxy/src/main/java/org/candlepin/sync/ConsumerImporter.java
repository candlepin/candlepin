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
package org.candlepin.sync;

import java.io.IOException;
import java.io.Reader;

import org.codehaus.jackson.map.ObjectMapper;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.xnap.commons.i18n.I18n;

/**
 * ConsumerImporter
 */
public class ConsumerImporter {

    private OwnerCurator curator;
    private I18n i18n;

    public ConsumerImporter(OwnerCurator curator, I18n i18n) {
        this.curator = curator;
        this.i18n = i18n;
    }

    public ConsumerDto createObject(ObjectMapper mapper, Reader reader) throws IOException {
        return mapper.readValue(reader, ConsumerDto.class);
    }

    public void store(Owner owner, ConsumerDto consumer) throws SyncDataFormatException {

        if (consumer.getUuid() == null) {
            throw new SyncDataFormatException(i18n.tr("No ID for upstream distributor"));
        }

        if (owner.getUpstreamUuid() != null &&
            !owner.getUpstreamUuid().equals(consumer.getUuid())) {
            throw new SyncDataFormatException(
                i18n.tr("Owner has already imported from another distributor"));
        }

        owner.setUpstreamUuid(consumer.getUuid());
        curator.merge(owner);
    }

}
