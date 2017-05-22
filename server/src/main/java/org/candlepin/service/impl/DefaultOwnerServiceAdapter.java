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

import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.service.OwnerServiceAdapter;

import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.xnap.commons.i18n.I18n;



/**
 * default SubscriptionAdapter implementation
 */
public class DefaultOwnerServiceAdapter implements OwnerServiceAdapter {
    private static Logger log = LoggerFactory.getLogger(DefaultOwnerServiceAdapter.class);

    private OwnerCurator ownerCurator;
    private I18n i18n;

    @Inject
    public DefaultOwnerServiceAdapter(OwnerCurator ownerCurator, I18n i18n) {
        this.ownerCurator = ownerCurator;
        this.i18n = i18n;
    }

    /**
     * @{inheritDocs}
     */
    @Override
    public boolean isOwnerKeyValidForCreation(String ownerKey) {
        return true;
    }

    /**
     * @{inheritDocs}
     */
    @Override
    public String getContentAccessMode(String ownerKey) {
        // Since we're acting as the upstream source, we'll just pass-through any existing value.
        Owner owner = ownerKey != null ? this.ownerCurator.lookupByKey(ownerKey) : null;
        if (owner == null) {
            throw new IllegalArgumentException("ownerKey does not represent a valid owner: " + ownerKey);
        }

        return owner.getContentAccessMode();
    }

    /**
     * @{inheritDocs}
     */
    @Override
    public String getContentAccessModeList(String ownerKey) {
        // Since we're acting as the upstream source, we'll just pass-through any existing value.
        Owner owner = ownerKey != null ? this.ownerCurator.lookupByKey(ownerKey) : null;
        if (owner == null) {
            throw new IllegalArgumentException("ownerKey does not represent a valid owner: " + ownerKey);
        }

        return owner.getContentAccessModeList();
    }
}
