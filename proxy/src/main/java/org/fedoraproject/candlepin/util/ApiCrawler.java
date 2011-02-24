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
package org.fedoraproject.candlepin.util;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.LinkedList;
import java.util.List;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import org.codehaus.jackson.map.ObjectMapper;
import org.fedoraproject.candlepin.auth.Role;
import org.fedoraproject.candlepin.auth.interceptor.AllowRoles;
import org.fedoraproject.candlepin.resource.RootResource;

/**
 * ApiCrawler - Uses Java Reflection API to crawl the classes in our resources
 * namespace looking for exposed API calls.
 */
public class ApiCrawler {
    private ObjectMapper mapper = new ObjectMapper();
    private List<Class> httpClasses;
    private static final String API_FILE = "target/candlepin_api.json";

    public ApiCrawler() {
        httpClasses = new LinkedList<Class>();
        httpClasses.add(GET.class);
        httpClasses.add(POST.class);
        httpClasses.add(PUT.class);
        httpClasses.add(DELETE.class);
    }

    public void run() throws IOException {
        List<RestApiCall> allApiCalls = new LinkedList<RestApiCall>();
        for (Class c : RootResource.RESOURCE_CLASSES) {
            allApiCalls.addAll(processClass(c));
        }

        FileWriter jsonFile = new FileWriter(API_FILE);
        mapper.writeValue(jsonFile, allApiCalls);
        jsonFile.close();
    }

    private List<RestApiCall> processClass(Class c) {
        Path a = (Path) c.getAnnotation(Path.class);
        String parentUrl = a.value();
        List<RestApiCall> classApiCalls = new LinkedList<RestApiCall>();
        for (Method m : c.getDeclaredMethods()) {
            if (Modifier.isPublic(m.getModifiers())) {
                classApiCalls.add(processMethod(parentUrl, m));
            }
        }
        return classApiCalls;
    }

    private RestApiCall processMethod(String rootPath, Method m) {
        RestApiCall apiCall = new RestApiCall();

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

        processHttpVerb(m, apiCall);
        processAllowedRoles(m, apiCall);
        processQueryParams(m, apiCall);

        apiCall.setReturnType(getReturnType(m));

        // for matching up with documentation
        apiCall.setMethod(getQualifiedName(m));

        return apiCall;
    }

    private void processHttpVerb(Method m, RestApiCall apiCall) {
        for (Class httpClass : httpClasses) {
            if (m.getAnnotation(httpClass) != null) {
                apiCall.addHttpVerb(httpClass.getSimpleName());
            }
        }
    }

    private void processAllowedRoles(Method m, RestApiCall apiCall) {
        AllowRoles allowRoles = m.getAnnotation(AllowRoles.class);
        if (allowRoles != null) {
            for (Role allow : allowRoles.roles()) {
                apiCall.addRole(allow);
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

    private static String getReturnType(Method method) {
        Type returnType = method.getGenericReturnType();
        String typeString = method.getReturnType().getSimpleName().toLowerCase();
        if (returnType instanceof ParameterizedType) {
            ParameterizedType type = (ParameterizedType) returnType;
            Type[] typeArguments = type.getActualTypeArguments();
            typeString += " of";
            for (Type typeArgument : typeArguments) {
                Class typeArgClass = (Class) typeArgument;
                typeString += " " + typeArgClass.getSimpleName().toLowerCase();
            }
        }
        return typeString;
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
        private List<Role> allowedRoles;
        private List<String> httpVerbs;
        private List<ApiParam> queryParams;
        private String returnType;

        public RestApiCall() {
            allowedRoles = new LinkedList<Role>();
            allowedRoles.add(Role.SUPER_ADMIN); // assumed to always have access
            httpVerbs = new LinkedList<String>();
            queryParams = new LinkedList<ApiParam>();
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

        public void addRole(Role role) {
            allowedRoles.add(role);
        }

        public void addQueryParam(String name, String type) {
            queryParams.add(new ApiParam(name, type));
        }

        public void setReturnType(String type) {
            returnType = type;
        }

        public List<Role> getAllowedRoles() {
            return allowedRoles;
        }

        public List<String> getHttpVerbs() {
            return httpVerbs;
        }

        public List<ApiParam> getQueryParams() {
            return queryParams;
        }

        public String getReturnType() {
            return returnType;
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
