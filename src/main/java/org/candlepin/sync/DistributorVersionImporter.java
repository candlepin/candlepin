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

import org.candlepin.model.DistributorVersion;
import org.candlepin.model.DistributorVersionCapability;
import org.candlepin.model.DistributorVersionCurator;

import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.util.Set;

/**
 * DistributorVersionImporter
 */
public class DistributorVersionImporter {
    private static Logger log = LoggerFactory.getLogger(DistributorVersionImporter.class);

    private DistributorVersionCurator curator;

    public DistributorVersionImporter(DistributorVersionCurator curator) {
        this.curator = curator;
    }

    public DistributorVersion createObject(ObjectMapper mapper, Reader reader)
        throws IOException {
        DistributorVersion distributorVersion = mapper.readValue(reader,
            DistributorVersion.class);
        distributorVersion.setId(null);
        for (DistributorVersionCapability dvc : distributorVersion.getCapabilities()) {
            dvc.setId(null);
        }
        return distributorVersion;
    }

    /**
     * @param distVers Set of Distributor Versions.
     */
    public void store(Set<DistributorVersion> distVers) {
        log.debug("Creating/updating distributor versions");
        for (DistributorVersion distVer : distVers) {
            DistributorVersion existing = curator.findByName(distVer.getName());
            if (existing == null) {
                curator.create(distVer);
                log.debug("Created distributor version: " + distVer.getName());
            }
            else {
                existing.setCapabilities(distVer.getCapabilities());
                existing.setDisplayName(distVer.getDisplayName());
                curator.merge(existing);
                log.debug("Updating distributor version: " + distVer.getName());
            }
        }
    }
}
