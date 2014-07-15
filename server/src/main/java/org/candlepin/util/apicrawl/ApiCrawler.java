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

import org.candlepin.auth.interceptor.Verify;
import org.candlepin.config.Config;
import org.candlepin.guice.HttpMethodMatcher;
import org.candlepin.resource.RootResource;
import org.candlepin.resteasy.JsonProvider;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jsonSchema.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator;
import com.google.inject.matcher.Matcher;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedList;
import java.util.List;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

/**
 * ApiCrawler - Uses Java Reflection API to crawl the classes in our resources
 * namespace looking for exposed API calls.
 */
public class ApiCrawler {
    private ObjectMapper mapper = new JsonProvider(new Config())
            .locateMapper(Object.class, MediaType.APPLICATION_JSON_TYPE);
    private List<Class> httpClasses;
    private static final String API_FILE = "target/candlepin_api.json";
    private NonRecursiveModule dontRecurse;

    public ApiCrawler() {
        httpClasses = new LinkedList<Class>();
        httpClasses.add(GET.class);
        httpClasses.add(POST.class);
        httpClasses.add(PUT.class);
        httpClasses.add(DELETE.class);
        httpClasses.add(HEAD.class);

        // jackson schema generation in the 1.x series breaks when you have a cycle in your
        // schema graph (ie, owner defines a parent owner. even though they aren't the same
        // instance in an instantiated object, the class refers to itself).
        //
        // This bug isn't likely to get fixed in 1.x, so we work around it by defining this
        // module that keeps track of seen classes, and when it sees one again, just
        // outputs an empty schema. We have to reset the seen classes between method
        // generation, so that each REST API definition will still have a proper toplevel
        // return type.
        //
        // for more info, see http://jira.codehaus.org/browse/JACKSON-439
        dontRecurse = new NonRecursiveModule(mapper);
        mapper.registerModule(dontRecurse);
    }

    public void run() throws IOException {
        List<RestApiCall> allApiCalls = new LinkedList<RestApiCall>();
        for (Object o : RootResource.RESOURCE_CLASSES.keySet()) {
            if (o instanceof Class) {
                Class c = (Class) o;
                allApiCalls.addAll(processClass(c));
            }
        }

        // we need a different mapper to write the output, one without our
        // schema hack module installed.
        ObjectMapper mapper = new JsonProvider(new Config())
            .locateMapper(Object.class, MediaType.APPLICATION_JSON_TYPE);
        FileWriter jsonFile = new FileWriter(API_FILE);
        try {
            mapper.writeValue(jsonFile, allApiCalls);
        }
        finally {
            jsonFile.close();
        }
    }

    private List<RestApiCall> processClass(Class c) {
        Path a = (Path) c.getAnnotation(Path.class);
        String parentUrl = a.value();
        List<RestApiCall> classApiCalls = new LinkedList<RestApiCall>();
        Matcher<Method> matcher = new HttpMethodMatcher();
        for (Method m : c.getMethods()) {
            if (Modifier.isPublic(m.getModifiers()) && matcher.matches(m)) {
                classApiCalls.add(processMethod(parentUrl, m));
            }
        }
        return classApiCalls;
    }

    private RestApiCall processMethod(String rootPath, Method m) {
        RestApiCall apiCall = new RestApiCall();

        processPath(rootPath, m, apiCall);
        processHttpVerb(m, apiCall);
        processQueryParams(m, apiCall);
        processVerifiedParams(m, apiCall);

        dontRecurse.resetSeen();
        try {
            apiCall.setReturnType(getReturnType(m));
        }
        catch (JsonMappingException e) {
            apiCall.setReturnType(null);
        }

        // for matching up with documentation
        apiCall.setMethod(getQualifiedName(m));

        return apiCall;
    }

    private void processPath(String rootPath, Method m, RestApiCall apiCall) {
        Path subPath = m.getAnnotation(Path.class);
        if (subPath != null) {
            if (rootPath.endsWith("/") || subPath.value().startsWith("/")) {
                apiCall.setUrl(rootPath + subPath.value());
            }
            else {
                apiCall.setUrl(rootPath + "/" + subPath.value());
            }
        }
        else {
            apiCall.setUrl(rootPath);
        }
    }

    private void processHttpVerb(Method m, RestApiCall apiCall) {
        for (Class httpClass : httpClasses) {
            if (m.getAnnotation(httpClass) != null) {
                apiCall.addHttpVerb(httpClass.getSimpleName());
            }
        }
    }

    private void processQueryParams(Method m, RestApiCall apiCall) {
        for (int i = 0; i < m.getParameterAnnotations().length; i++) {
            for (Annotation a : m.getParameterAnnotations()[i]) {
                if (a instanceof QueryParam) {
                    String type = m.getParameterTypes()[i].getSimpleName().toLowerCase();
                    apiCall.addQueryParam(((QueryParam) a).value(), type);
                }

            }
        }
    }

    /* Find parameters that are run through the security interceptor.
     * right now this expects them to only be path params (not query params),
     * but you can have more than one per method.
     */
    private void processVerifiedParams(Method m, RestApiCall apiCall) {
        for (int i = 0; i < m.getParameterAnnotations().length; i++) {
            boolean hasVerify = false;
            String pathName = null;
            for (Annotation a : m.getParameterAnnotations()[i]) {
                if (a instanceof Verify) {
                    hasVerify = true;
                }
                else if (a instanceof PathParam) {
                    PathParam p = (PathParam) a;
                    pathName = p.value();
                }
            }

            if (hasVerify && pathName != null) {
                apiCall.verifiedParams.add(pathName);
            }
        }
    }

    private JsonSchema getReturnType(Method method) throws JsonMappingException {
        Class<?> returnType = method.getReturnType();
        if (returnType.equals(Void.TYPE)) {
            return null;
        }
        JsonSchemaGenerator generator = new JsonSchemaGenerator(mapper);
        return generator.generateSchema(method.getReturnType());
    }

    private static String getQualifiedName(Method method) {
        return method.getDeclaringClass().getName() + "." + method.getName();
    }

    /**
     * RestApiCall: Helper object for storing information about an API method.
     */
    static class RestApiCall {
        private String method;
        private String url;
        private List<String> verifiedParams;
        private List<String> httpVerbs;
        private List<ApiParam> queryParams;
        private JsonSchema returnType;

        public RestApiCall() {
            httpVerbs = new LinkedList<String>();
            queryParams = new LinkedList<ApiParam>();

            // these are the names of the security enforced path params
            verifiedParams = new LinkedList<String>();
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public void addHttpVerb(String verb) {
            this.httpVerbs.add(verb);
        }

        public void addQueryParam(String name, String type) {
            queryParams.add(new ApiParam(name, type));
        }

        public void setReturnType(JsonSchema type) {
            returnType = type;
        }

        public JsonSchema getReturnType() {
            return returnType;
        }

        public List<String> getHttpVerbs() {
            return httpVerbs;
        }

        public List<String> getVerifiedParams() {
            return verifiedParams;
        }

        public List<ApiParam> getQueryParams() {
            return queryParams;
        }

        public String getUrl() {
            return url;
        }

        public String getMethod() {
            return method;
        }

        private static class ApiParam {
            private String name;
            private String type;

            public ApiParam(String name, String type) {
                this.name = name;
                this.type = type;
            }

            public String getName() {
                return name;
            }

            public String getType() {
                return type;
            }
        }
    }

    public static void main(String [] args) throws Exception {
        ApiCrawler crawler = new ApiCrawler();
        crawler.run();
    }
}
