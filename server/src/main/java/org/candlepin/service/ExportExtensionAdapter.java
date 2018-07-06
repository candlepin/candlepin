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
package org.candlepin.service;

import org.candlepin.service.model.ConsumerInfo;

import java.io.File;
import java.io.IOException;
import java.util.Map;



/**
 * <p>This adapter provides a hook for allowing the extension of what is
 * stored in a manifest file. Any extras should be added to the extensions
 * directory so that anything that is added externally can be easily identified.</p>
 *
 * <p>
 * The following code shows how a simple file could be created and added to the
 * generated manifest:
 * </p>
 *
 * <pre>
 * void extendManifest(File extensionDir, Consumer targetConsumer, Map<String, String> extensionData)
 *    throws IOException {
 *    String version = (String) extensionData.get("version");
 *    File extension = new File(extensionDir, String.format("my-extension-%s.txt", version));
 *    PrintWriter writer = new PrintWriter(extension);
 *    writer.write("An extension was created for consumer " + targetConsumer.getUuid() + ".\n");
 *    writer.write("Version: " + version);
 *    writer.close();
 * }
 * </pre>
 *
 */
public interface ExportExtensionAdapter {

    /**
     * Extends the contents of an export archive file. Implementors can write files to the extension
     * directory which will be included in the resulting export archive.
     *
     * @param extensionDir the directory where export extension data should be placed
     *                     ( ./extensions in the resulting archive)
     * @param targetConsumer the {@link Consumer} that is being exported.
     * @param extensionData data passed via the ext query param when the initial request was made.
     * @throws IOException if there were any issues creating the extension files.
     */
    void extendManifest(File extensionDir, ConsumerInfo targetConsumer, Map<String, String> extensionData)
        throws IOException;

}
