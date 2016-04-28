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
package org.candlepin.resource;

import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyContentOverride;
import org.candlepin.model.activationkeys.ActivationKeyContentOverrideCurator;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.util.ContentOverrideValidator;

import com.google.inject.Inject;

import org.xnap.commons.i18n.I18n;

import javax.ws.rs.Path;

import io.swagger.annotations.Api;

/**
 * ActivationKeyContentOverrideResource
 */
@Path("/activation_keys/{activation_key_id}/content_overrides")
@Api("activation_keys")
public class ActivationKeyContentOverrideResource extends
    ContentOverrideResource<ActivationKeyContentOverride,
    ActivationKeyContentOverrideCurator,
    ActivationKey> {

    private ActivationKeyCurator activationKeyCurator;

    /**
     * @param contentOverrideCurator
     * @param contentOverrideValidator
     * @param parentPath
     */
    @Inject
    public ActivationKeyContentOverrideResource(
        ActivationKeyContentOverrideCurator contentOverrideCurator,
        ActivationKeyCurator activationKeyCurator,
        ContentOverrideValidator contentOverrideValidator, I18n i18n) {
        super(contentOverrideCurator, contentOverrideValidator, i18n, "activation_key_id");
        this.activationKeyCurator = activationKeyCurator;
    }

    @Override
    protected ActivationKey findParentById(String parentId) {
        return activationKeyCurator.verifyAndLookupKey(parentId);
    }

}
