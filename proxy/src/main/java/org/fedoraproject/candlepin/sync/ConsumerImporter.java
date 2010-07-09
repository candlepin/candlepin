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
package org.fedoraproject.candlepin.sync;

import java.io.IOException;
import java.io.Reader;

import org.codehaus.jackson.map.ObjectMapper;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.OwnerCurator;

/**
 * ConsumerImporter
 */
public class ConsumerImporter {

    private OwnerCurator curator;
    
    public ConsumerImporter(OwnerCurator curator) {
        this.curator = curator;
    }
    
    public ConsumerDto createObject(ObjectMapper mapper, Reader reader) throws IOException {
        return mapper.readValue(reader, ConsumerDto.class);
    }

    public void store(Owner owner, ConsumerDto consumer) throws SyncDataFormatException {
        
        if (consumer.getUuid() == null) {
            throw new SyncDataFormatException("null uuid on consumer info");
        }
        
        if (owner.getUpstreamUuid() != null &&
            owner.getUpstreamUuid() != consumer.getUuid()) {
            throw new SyncDataFormatException("mismatched consumer uuid for this owner");
        }
        
        owner.setUpstreamUuid(consumer.getUuid());
        curator.merge(owner);
    }

}
