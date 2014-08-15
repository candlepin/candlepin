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

package org.candlepin.gutterball;

import org.candlepin.gutterball.mongodb.MongoConnection;

import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.util.JSON;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.net.URL;

/**
 * Imports JSON data from a file into a mongodb collection.
 *
 * Although embedded mongodb has an importer, this one is a little more light weight
 * since it reuses a {@link MongoConnection} instance and does not spin a new one up.W
 *
 */
public class MongoJsonDataImporter {

    private MongoConnection mongo;

    public MongoJsonDataImporter(MongoConnection mongo) {
        this.mongo = mongo;
    }

    public void importFile(String collectionName, String file) throws Exception {
        URL fileUrl = MongoJsonDataImporter.class.getClassLoader().getResource(file);
        if (fileUrl == null) {
            throw new FileNotFoundException("Could not import data from: " + file);
        }
        DBCollection collection = mongo.getDB().getCollection(collectionName);

        File jsonFile = new File(fileUrl.getPath());
        BufferedReader reader = new BufferedReader(new FileReader(jsonFile));

        try {
            String next = "";
            while ((next = reader.readLine()) != null) {
                DBObject data = (DBObject) JSON.parse(next);
                collection.insert(data);
            }
        }
        finally {
            reader.close();
        }
    }

}
