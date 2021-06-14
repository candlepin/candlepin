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

import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.v1.ContentDTO;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;

import com.google.inject.Inject;

import org.xnap.commons.i18n.I18n;

import java.util.Objects;

public class ContentResource implements ContentApi {

    private final ContentCurator contentCurator;
    private final I18n i18n;
    private final ModelTranslator modelTranslator;

    @Inject
    public ContentResource(ContentCurator contentCurator, I18n i18n, ModelTranslator modelTranslator) {
        this.i18n = Objects.requireNonNull(i18n);
        this.contentCurator = Objects.requireNonNull(contentCurator);
        this.modelTranslator = Objects.requireNonNull(modelTranslator);
    }

    @Override
    public Iterable<ContentDTO> listContent() {
        return this.modelTranslator.translateQuery(this.contentCurator.listAll(), ContentDTO.class);
    }

    @Override
    public ContentDTO getContent(String contentUuid) {
        Content content = this.contentCurator.getByUuid(contentUuid);

        if (content == null) {
            throw new NotFoundException(
                i18n.tr("Content with UUID \"{0}\" could not be found.", contentUuid));
        }

        return this.modelTranslator.translate(content, ContentDTO.class);
    }

}
