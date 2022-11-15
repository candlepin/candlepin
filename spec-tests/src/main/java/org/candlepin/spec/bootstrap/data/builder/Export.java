/**
 * Copyright (c) 2009 - 2022 Red Hat, Inc.
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

package org.candlepin.spec.bootstrap.data.builder;

import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;

import java.io.File;

/**
 * A simple value class meant to carry around the details of a generated export.
 */
public class Export {

    private final ConsumerDTO consumer;
    private final ExportCdn cdn;
    private final File file;
    private final OwnerDTO owner;

    public Export(File file, ConsumerDTO consumer, ExportCdn cdn, OwnerDTO owner) {
        this.file = file;
        this.consumer = consumer;
        this.cdn = cdn;
        this.owner = owner;
    }

    public OwnerDTO owner() {
        return owner;
    }
    public ConsumerDTO consumer() {
        return consumer;
    }

    public ExportCdn cdn() {
        return cdn;
    }

    public File file() {
        return file;
    }
}
