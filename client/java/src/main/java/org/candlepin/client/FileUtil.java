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
package org.candlepin.client;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * File Utilities
 */
public class FileUtil {

    public static final Logger L = LoggerFactory.getLogger(FileUtil.class);
    private static final int BUF_SIZE = 4096;

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

    public static void removeFiles(File[] files) {
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

    public static void dumpKeyAndCert(String key, String cert, String filePath) {
        try {
            File file = new File(filePath);
            file.getParentFile().mkdirs();
            if (file.exists()) {
                L.warn("File {} already exists. Overwriting it!", filePath);
            }
            FileOutputStream outputStream = new FileOutputStream(file);
            L.debug("Writing key of size: #{} & cert of size: #{}", key
                .length(), cert.length());
            outputStream.write((key + cert).getBytes());
            outputStream.close();
        }
        catch (Exception e) {
            throw new ClientException(e);
        }
    }

    public static String[] readKeyAndCert(String filePath) {
        try {
            File file = new File(filePath);
            String[] result = new String[2];
            if (!file.exists()) {
                throw new ClientException("File : " + filePath +
                    " does not exist. Cannot read key & cert");
            }
            String keyAndCert = readAll(filePath);
            int begCertIndex = keyAndCert.indexOf(BEG_CERTIFICATE);
            result[0] = keyAndCert.substring(0, begCertIndex);
            result[1] = keyAndCert.substring(begCertIndex, keyAndCert.length());
            return result;
        }
        catch (Exception e) {
            throw new ClientException(e);
        }
    }

    private static final String BEG_CERTIFICATE = "-----BEGIN CERTIFICATE-----";

    public static String readAll(String filePath) {
        byte[] buffer = new byte[BUF_SIZE];
        ByteArrayOutputStream bufr = new ByteArrayOutputStream();
        try {
            BufferedInputStream inputStream = new BufferedInputStream(
                new FileInputStream(new File(filePath)));
            int read = -1;
            while ((read = inputStream.read(buffer)) != -1) {
                bufr.write(buffer, 0, read);
            }
            return bufr.toString();
        }
        catch (Exception e) {
            throw new ClientException(e);
        }
    }
}
