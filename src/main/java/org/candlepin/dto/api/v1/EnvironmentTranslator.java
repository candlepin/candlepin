/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.dto.api.v1;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.ObjectTranslator;
import org.candlepin.dto.api.server.v1.EnvironmentContentDTO;
import org.candlepin.dto.api.server.v1.EnvironmentDTO;
import org.candlepin.dto.api.server.v1.NestedOwnerDTO;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentContent;
import org.candlepin.model.Owner;
import org.candlepin.util.Util;

import java.util.Set;
import java.util.stream.Collectors;



/**
 * The EnvironmentTranslator provides translation from Environment model objects to EnvironmentDTOs
 */
public class EnvironmentTranslator implements ObjectTranslator<Environment, EnvironmentDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public EnvironmentDTO translate(Environment source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EnvironmentDTO translate(ModelTranslator translator, Environment source) {
        return source != null ? this.populate(translator, source, new EnvironmentDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EnvironmentDTO populate(Environment source, EnvironmentDTO dest) {
        return this.populate(null, source, dest);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EnvironmentDTO populate(ModelTranslator translator, Environment source,
        EnvironmentDTO dest) {

        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("destination is null");
        }

        dest.created(Util.toDateTime(source.getCreated()))
            .updated(Util.toDateTime(source.getUpdated()))
            .id(source.getId())
            .name(source.getName())
            .type(source.getType())
            .description(source.getDescription())
            .contentPrefix(source.getContentPrefix());

        if (translator != null) {
            Owner owner = source.getOwner();
            dest.setOwner(owner != null ? translator.translate(owner, NestedOwnerDTO.class) : null);

            Set<EnvironmentContent> envContents = source.getEnvironmentContent();
            if (envContents != null) {
                Set<EnvironmentContentDTO> ecdtos = envContents.stream()
                    .map(ec -> new EnvironmentContentDTO()
                        .contentId(ec.getContentId())
                        .enabled(ec.getEnabled()))
                    .collect(Collectors.toSet());

                dest.setEnvironmentContent(ecdtos);
            }
            else {
                dest.setEnvironmentContent(null);
            }
        }
        else {
            dest.setOwner(null);
            dest.setEnvironmentContent(null);
        }

        return dest;
    }

}
