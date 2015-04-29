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
package org.candlepin.util.apicrawl;

//import com.sun.javadoc.AnnotationDesc;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.javadoc.AnnotationDesc;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.DocErrorReporter;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.RootDoc;
import com.sun.javadoc.Tag;
import com.sun.javadoc.Type;

import org.apache.commons.lang.StringUtils;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *
 */
public class ApiDoclet {
    private static final String RETURN_TAG = "return";
    private static final String RETURN_CODE_TAG = "httpcode";
    private static final String DEPRECATED_TAG = "deprecated";

    private static String outputDir;
    private static ObjectMapper mapper = new ObjectMapper();

    private ApiDoclet() {
        // keep checkstyle happy
    }

    /**
     * Set -d flag to indicate the output dir.
     *
     * @param options
     * @param reporter
     * @return if the options are valid
     */
    public static boolean validOptions(String[][] options, DocErrorReporter reporter) {
        for (int i = 0; i < options.length; i++) {
            String[] opt = options[i];
            if (opt[0].equals("-d")) {
                outputDir = opt[1];

                return true;
            }
        }

        return false;
    }

    public static int optionLength(String option) {
        // allow the semistandard -d flag for output dir,
        // mainly because buildr assumes it is there.
        if (option.equals("-d")) {
            return 2;
        }

        return 0;
    }

    /**
     * Entry point for the doclet.
     *
     * @param root
     * @return if the run was successful
     */
    public static boolean start(RootDoc root) {
        try {
            generateText(root);
        }
        catch (IOException e) {
            return false;
        }

        return true;
    }

    private static void generateText(RootDoc root) throws IOException {
        List<RestMethod> methods = new ArrayList<RestMethod>();

        for (ClassDoc classDoc : root.classes()) {
            boolean docClass = false;
            for (AnnotationDesc a : classDoc.annotations()) {
                // Only look at REST resource classes
                if ("javax.ws.rs.Path".equals(a.annotationType().qualifiedTypeName())) {
                    docClass = true;
                    break;
                }
            }

            if (docClass) {
                getAllMethods(classDoc, methods);
            }
        }

        FileWriter jsonFile = null;

        try {
            jsonFile = new FileWriter(outputDir + "/candlepin_comments.json");
            mapper.writeValue(jsonFile, methods);
        }
        finally {
            jsonFile.close();
        }
    }

    private static void getAllMethods(Type type, List<RestMethod> methods) {
        // Don't walk up beyond our classes.
        if (type != null && type.qualifiedTypeName().startsWith("org.candlepin")) {
            ClassDoc classDoc = type.asClassDoc();
            getAllMethods(classDoc.superclassType(), methods);
            for (MethodDoc methodDoc : classDoc.methods()) {
                if (methodDoc.isPublic()) {
                    methods.add(new RestMethod(methodDoc));
                }
            }
        }
    }

    /**
     * Represents RESTful method documentation, including a method description
     * and a description of possible return codes.
     */
    static class RestMethod {
        private String method;
        private String description;
        private String deprecated;
        private String returns;
        private List<HttpStatusCode> httpStatusCodes;

        private RestMethod(MethodDoc doc) {
            this.httpStatusCodes = new ArrayList<HttpStatusCode>();

            for (Tag tag : doc.tags(RETURN_CODE_TAG)) {
                this.httpStatusCodes.add(new HttpStatusCode(tag));
            }

            for (Tag tag : doc.tags(DEPRECATED_TAG)) {
                this.deprecated = tag.text();
            }

            for (Tag tag : doc.tags(RETURN_TAG)) {
                this.returns = tag.text();
            }

            this.method = doc.qualifiedName();
            this.description = doc.commentText();
        }

        public String getMethod() {
            return this.method;
        }

        public String getDescription() {
            return this.description;
        }

        public String getDeprecated() {
            return this.deprecated;
        }

        public String getReturns() {
            return this.returns;
        }

        public List<HttpStatusCode> getHttpStatusCodes() {
            return this.httpStatusCodes;
        }
    }

    /**
     * A pairing of an HTTP return code with a description of the conditions
     * on which it is returned.
     */
    static class HttpStatusCode {
        private int statusCode;
        private String description;

        private HttpStatusCode(Tag tag) {
            // Split on whitespace
            String[] tagParts = tag.text().split("\\s+");
            statusCode = Integer.parseInt(tagParts[0]);

            tagParts = Arrays.copyOfRange(tagParts, 1, tagParts.length, String[].class);
            description = StringUtils.join(tagParts, " ");
        }

        public int getStatusCode() {
            return this.statusCode;
        }

        public String getDescription() {
            return this.description;
        }
    }

}
