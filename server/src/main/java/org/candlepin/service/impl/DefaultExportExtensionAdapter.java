/**
 * Copyright (c) 2009 - 2016 Red Hat, Inc.
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

import org.candlepin.service.model.ConsumerInfo;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.candlepin.service.ExportExtensionAdapter;



/**
 * The default implementation of the {@link ExportExtensionAdapter}. This adapter adds
 * nothing to the manifest.
 */
public class DefaultExportExtensionAdapter implements ExportExtensionAdapter {

    @Override
    public void extendManifest(File extensionDir, ConsumerInfo consumer, Map<String, String> extensionData)
        throws IOException {

        // The default implementation does nothing.
    }

}
