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
package org.candlepin.liquibase;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

/**
 * Test to ensure that none of our Liquibase changesets use a schema version newer than what is provided in
 * the Liquibase jar.  If we use a newer version, Java will attempt to download the schema which breaks
 * disconnected installations.
 *
 * This class is not really a unit test, per se, but there's not really a better place for it.
 */
public class SchemaCompatibilityTest {
    private static final Double EXPECTED_XSD_VERSION = 4.19;

    @Test
    public void verifySchemaVersion() throws Exception {
        List<File> changesets = gatherChangesets();
        XPath xPath = XPathFactory.newInstance().newXPath();
        // the the schemaLocation attribute node within the databaseChangeLog element
        String expression = "/databaseChangeLog/@schemaLocation";
        Pattern xsdPattern = Pattern.compile("http://www.liquibase" +
            ".org/xml/ns/dbchangelog/dbchangelog-(\\d.\\d+).xsd");

        List<String> warnings = new ArrayList<>();
        for (File f : changesets) {
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = builderFactory.newDocumentBuilder();
            Document changeset = builder.parse(f);

            Node schemaLocation = (Node) xPath.compile(expression).evaluate(changeset,
                XPathConstants.NODE);

            if (schemaLocation == null) {
                warnings.add("Missing schemaLocation attribute for " + f);
            }
            else {
                String location = schemaLocation.getNodeValue();
                Matcher matcher = xsdPattern.matcher(location);
                if (matcher.find()) {
                    Double xsdVersion = Double.parseDouble(matcher.group(1));
                    if (EXPECTED_XSD_VERSION < xsdVersion) {
                        warnings.add("XSD version mismatch (found: " + xsdVersion +
                            ", expected less than or equal to: " +
                            EXPECTED_XSD_VERSION + ") for " + f);
                    }
                }
                else {
                    warnings.add("Could not parse schemaLocation value for " + f);
                }
            }
        }

        if (!warnings.isEmpty()) {
            fail(String.join("\n", warnings));
        }
    }

    private List<File> gatherChangesets() throws Exception {
        final PathMatcher xmlMatcher = FileSystems.getDefault().getPathMatcher("glob:*.xml");
        Path resources = Paths.get("src", "main", "resources", "db", "changelog");

        final List<File> matches = new ArrayList<>();
        Files.walkFileTree(resources, new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (xmlMatcher.matches(file.getFileName())) {
                    matches.add(file.toFile());
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                throw new IOException("Could not visit" + file);
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }
        });

        return matches;
    }
}
