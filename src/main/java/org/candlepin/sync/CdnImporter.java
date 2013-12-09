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

import org.candlepin.model.Cdn;
import org.candlepin.model.CdnCurator;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.util.Set;

/**
 * DistributorVersionImporter
 */
public class CdnImporter {
    private static Logger log =  LoggerFactory.getLogger(CdnImporter.class);

    private CdnCurator curator;

    public CdnImporter(CdnCurator curator) {
        this.curator = curator;
    }

    public Cdn createObject(ObjectMapper mapper, Reader reader)
        throws IOException {
        Cdn cdn = mapper.readValue(reader,
            Cdn.class);
        cdn.setId(null);
        return cdn;
    }

    /**
     * @param cdnSet Set of CDN's.
     */
    public void store(Set<Cdn> cdnSet) {
        log.debug("Creating/updating cdns");
        for (Cdn cdn : cdnSet) {
            Cdn existing = curator.lookupByLabel(cdn.getLabel());
            if (existing == null) {
                curator.create(cdn);
                log.debug("Created CDN: " + cdn.getName());
            }
            else {
                existing.setName(cdn.getName());
                existing.setUrl(cdn.getUrl());
                curator.merge(existing);
                log.debug("Updating CDN: " + cdn.getName());
            }
        }
    }
}
