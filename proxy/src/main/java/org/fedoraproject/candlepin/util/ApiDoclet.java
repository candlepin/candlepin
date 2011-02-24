package org.fedoraproject.candlepin.util;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.DocErrorReporter;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.RootDoc;
import com.sun.javadoc.Tag;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.map.ObjectMapper;

/**
 *
 */
public class ApiDoclet {
    private final static String RETURN_CODE_TAG = "httpcode";

    private static String outputDir;
    private static ObjectMapper mapper = new ObjectMapper();

    /**
     * Set -d flag to indicate the output dir.
     *
     * @param options
     * @param reporter
     * @return
     */
    public static boolean validOptions(String options[][],
				       DocErrorReporter reporter) {
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
        if(option.equals("-d")) {
	    return 2;
        }

        return 0;
    }

    /**
     * Entry point for the doclet.
     *
     * @param root
     * @return
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
            for (MethodDoc methodDoc : classDoc.methods()) {
                if (methodDoc.isPublic()) {
                    methods.add(new RestMethod(methodDoc));
                }
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

    static class RestMethod {
        private String method;
        private String description;
        private List<HttpStatusCode> httpStatusCodes;

        private RestMethod(MethodDoc doc) {
            this.httpStatusCodes = new ArrayList<HttpStatusCode>();

            for (Tag tag : doc.tags(RETURN_CODE_TAG)) {
                this.httpStatusCodes.add(new HttpStatusCode(tag));
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

        public List<HttpStatusCode> getHttpStatusCodes() {
            return this.httpStatusCodes;
        }
    }

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
