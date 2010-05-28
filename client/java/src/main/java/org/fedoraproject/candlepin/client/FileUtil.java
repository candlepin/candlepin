/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.client;

import java.io.File;
import java.io.FileWriter;

/**
 * File Utilities
 */
public class FileUtil {

    protected FileUtil() {
    };

    public static void mkdir(String dirName) {
        try {
            File dir = new File(dirName);
            if (!dir.exists()) {
                dir.mkdirs();
            }
        }
        catch (Exception e) {
            throw new ClientException(e);
        }
    }

    public static void removeFiles(String[] files) {
        for (String fileName : files) {
            File file = new File(fileName);
            file.delete();
        }
    }
    

    public static void removeFiles(File [] files) {
        for (File file : files) {
            file.delete();
        }
    }

    public static void dumpToFile(String filename, String contents) {
        try {
            File file = new File(filename);
            FileWriter fout = new FileWriter(file);

            fout.append(contents);
            fout.close();
        }
        catch (Exception e) {
            throw new ClientException(e);
        }
    }
}
