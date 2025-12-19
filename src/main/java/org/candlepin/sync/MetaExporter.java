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
package org.candlepin.sync;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.Writer;

import javax.inject.Inject;


/**
 * Meta maps to meta.json in the export
 *
 */
public class MetaExporter {

    @Inject
    MetaExporter() {
    }

    void export(ObjectMapper mapper, Writer writer, Meta meta) throws IOException {
        mapper.writeValue(writer, meta);
    }

}
