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

import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyContentOverride;
import org.candlepin.model.activationkeys.ActivationKeyContentOverrideCurator;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.util.ContentOverrideValidator;

import com.google.inject.Inject;

import org.xnap.commons.i18n.I18n;

import javax.ws.rs.Path;

import io.swagger.annotations.Api;
import io.swagger.annotations.Authorization;



/**
 * ActivationKeyContentOverrideResource
 */
@Path("/activation_keys/{activation_key_id}/content_overrides")
@Api(value = "activation_keys", authorizations = { @Authorization("basic") })
public class ActivationKeyContentOverrideResource extends
    ContentOverrideResource<ActivationKeyContentOverride,
    ActivationKeyContentOverrideCurator, ActivationKey> {

    private ActivationKeyCurator activationKeyCurator;

    /**
     * @param contentOverrideCurator
     * @param contentOverrideValidator
     * @param parentPath
     */
    @Inject
    public ActivationKeyContentOverrideResource(I18n i18n, ActivationKeyContentOverrideCurator akcoCurator,
        ActivationKeyCurator akCurator, ContentOverrideValidator validator, ModelTranslator translator) {

        super(i18n, akcoCurator, translator, validator);

        this.activationKeyCurator = akCurator;
    }

    @Override
    protected ActivationKey findParentById(String parentId) {
        if (parentId == null || parentId.isEmpty()) {
            throw new BadRequestException(i18n.tr("activation key ID is null or empty"));
        }

        ActivationKey key = this.activationKeyCurator.secureGet(parentId);

        if (key == null) {
            throw new BadRequestException(i18n.tr("ActivationKey with id {0} could not be found.", parentId));
        }

        return key;
    }

    @Override
    protected String getParentPath() {
        return "activation_key_id";
    }

    @Override
    protected ActivationKeyContentOverride createOverride() {
        return new ActivationKeyContentOverride();
    }

}
