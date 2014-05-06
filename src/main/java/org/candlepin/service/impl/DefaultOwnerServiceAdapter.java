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
package org.candlepin.service.impl;

import org.candlepin.model.OwnerCurator;
import org.candlepin.service.OwnerServiceAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;

/**
 * default SubscriptionAdapter implementation
 */
public class DefaultOwnerServiceAdapter implements
        OwnerServiceAdapter {

    private static Logger log =
        LoggerFactory.getLogger(DefaultOwnerServiceAdapter.class);
    private OwnerCurator ownerCurator;
    private I18n i18n;

    @Inject
    public DefaultOwnerServiceAdapter(OwnerCurator ownerCurator, I18n i18n) {
        this.ownerCurator = ownerCurator;
        this.i18n = i18n;
    }

    /**
     * Confirms that the key can be created in the system.
     * @param ownerKey key for owner to be created.
     * @return boolean true if the owner key is allowed.
     */
    @Override
    public boolean isOwnerKeyValidForCreation(String ownerKey) {
        return true;
    }
}
