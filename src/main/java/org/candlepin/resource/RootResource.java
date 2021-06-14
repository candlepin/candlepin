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
package org.candlepin.resource;

/**
 * RootResource
 */

import org.candlepin.common.auth.SecurityHole;
import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.dto.api.v1.Link;

import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.ws.rs.Path;

/**
 * A root resource, responsible for returning client a struct of links to the
 * various resources Candlepin exposes. This list will be filtered based on the
 * permissions of the caller.
 */
public class RootResource implements DefaultApi {

    private static final Logger log = LoggerFactory.getLogger(RootResource.class);
    public static final Map<Object, String> RESOURCE_CLASSES;
    private Configuration config;
    private static List<Link> links = null;

    static {
        RESOURCE_CLASSES = new HashMap<>();
        addResource(AdminResource.class);
        addResource(UserResource.class);
        addResource(CertificateSerialResource.class);
        addResource(CdnResource.class);
        addResource(ConsumerResource.class);
        addResource(ConsumerTypeResource.class);
        addResource(ContentResource.class);
        addResource(CrlResource.class);
        addResource(EntitlementResource.class);
        addResource(JobResource.class);
        addResource(OwnerResource.class);
        addResource(PoolResource.class);
        addResource(ProductResource.class);
        addResource(RulesResource.class);
        addResource(StatusResource.class);
        addResource(SubscriptionResource.class);
        addResource(ActivationKeyResource.class);
        addResource(RoleResource.class);
        addResource(HypervisorResource.class);
        addResource(EnvironmentResource.class);
        addResource(RootResource.class);
        addResource(DistributorVersionResource.class);
        addResource(DeletedConsumerResource.class);
    }

    @Inject
    public RootResource(Configuration config) {
        this.config = config;
    }

    protected List<Link> createLinks() {
        // Hidden resources will be omitted from the supported list we send to
        // the clients:
        List<String> hideResources = Arrays.asList(
            config.getString(ConfigProperties.HIDDEN_RESOURCES).split(" "));

        List<Link> newLinks = new LinkedList<>();
        for (Map.Entry<Object, String> entry : RESOURCE_CLASSES.entrySet()) {
            add(resourceLink(entry.getKey(), entry.getValue()), hideResources,
                newLinks);
        }
        return newLinks;
    }

    protected String generateRel(String href) {
        int index = href.lastIndexOf("/");
        if (index == -1) {
            return href;
        }
        return href.substring(index + 1);
    }

    protected Link methodLink(String rel, Method m) {
        Path resource = m.getDeclaringClass().getAnnotation(Path.class);
        Path method = m.getAnnotation(Path.class);

        String href = resource.value() + "/" + method.value();
        // Remove doubled slashes and trailing slash
        href = href.replaceAll("/+", "/").replaceAll("/$", "");

        if (rel == null) {
            rel = generateRel(href);
        }
        return new Link().rel(rel).href(href);
    }

    protected Link classLink(String rel, Class clazz) {
        Path a = (Path) clazz.getAnnotation(Path.class);
        if (a == null) {
            a = (Path) Arrays.stream(clazz.getInterfaces())
                .map(c -> c.getAnnotation(Path.class))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        }
        String href = a.value();
        if (rel == null) {
            rel = generateRel(href);
        }
        return new Link().rel(rel).href(href);
    }

    protected Link resourceLink(Object resource, String rel) {
        if (resource instanceof Method) {
            return methodLink(rel, (Method) resource);
        }
        return classLink(rel, (Class) resource);
    }

    private void add(Link link, List<String> hideResources,
        List<Link> newLinks) {
        String rel = link.getRel();
        if (!hideResources.contains(rel)) {
            newLinks.add(link);
        }
        else {
            log.debug("Hiding supported resource: " + rel);
        }
    }

    @SecurityHole(noAuth = true, anon = true)
    public List<Link> getRootResources() {
        // Create the links when requested. Although
        // this is not thread safe, doing this 2 or 3 times
        // will not hurt anything as it will result in a little
        // bit more garbage
        if (links == null) {
            links = createLinks();
        }
        return links;
    }

    private static void addResource(Object resource) {
        RESOURCE_CLASSES.put(resource, null);
    }
}
