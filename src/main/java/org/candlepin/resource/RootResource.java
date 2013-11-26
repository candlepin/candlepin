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

import org.candlepin.auth.interceptor.SecurityHole;
import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;

import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;


/**
 * A root resource, responsible for returning client a struct of links to the
 * various resources Candlepin exposes. This list will be filtered based on the
 * permissions of the caller.
 */
@Path("/")
public class RootResource {

    private static Logger log = LoggerFactory.getLogger(RootResource.class);
    public static final List<Class> RESOURCE_CLASSES;
    public static final Map<String, Method> PSEUDO_RESOURCES;
    private Config config;
    private static List<Link> links = null;

    static {
        RESOURCE_CLASSES = new LinkedList<Class>();
        RESOURCE_CLASSES.add(AdminResource.class);
        RESOURCE_CLASSES.add(UserResource.class);
        RESOURCE_CLASSES.add(AtomFeedResource.class);
        RESOURCE_CLASSES.add(CertificateSerialResource.class);
        RESOURCE_CLASSES.add(CdnResource.class);
        RESOURCE_CLASSES.add(ConsumerResource.class);
        RESOURCE_CLASSES.add(ConsumerTypeResource.class);
        RESOURCE_CLASSES.add(ContentResource.class);
        RESOURCE_CLASSES.add(CrlResource.class);
        RESOURCE_CLASSES.add(EntitlementResource.class);
        RESOURCE_CLASSES.add(EventResource.class);
        RESOURCE_CLASSES.add(JobResource.class);
        RESOURCE_CLASSES.add(OwnerResource.class);
        RESOURCE_CLASSES.add(PoolResource.class);
        RESOURCE_CLASSES.add(ProductResource.class);
        RESOURCE_CLASSES.add(RulesResource.class);
        RESOURCE_CLASSES.add(StatisticResource.class);
        RESOURCE_CLASSES.add(StatusResource.class);
        RESOURCE_CLASSES.add(SubscriptionResource.class);
        RESOURCE_CLASSES.add(ActivationKeyResource.class);
        RESOURCE_CLASSES.add(RoleResource.class);
        RESOURCE_CLASSES.add(MigrationResource.class);
        RESOURCE_CLASSES.add(HypervisorResource.class);
        RESOURCE_CLASSES.add(EnvironmentResource.class);
        RESOURCE_CLASSES.add(RootResource.class);
        RESOURCE_CLASSES.add(DistributorVersionResource.class);
        RESOURCE_CLASSES.add(DeletedConsumerResource.class);

        PSEUDO_RESOURCES = new HashMap<String, Method>();
        try {
            Method contentOverrideMethod =
                ConsumerResource.class.getMethod("getContentOverrideList", String.class);
            PSEUDO_RESOURCES.put("content_overrides", contentOverrideMethod);

            Method guestIdMethod =
                ConsumerResource.class.getMethod("getGuests", String.class);
            PSEUDO_RESOURCES.put("guest_limit", guestIdMethod);
        }
        catch (NoSuchMethodException e) {
            // If the method name changes, throwing this will abort deployment.
            throw new IllegalStateException(
                "Can not find method to introspect!", e);
        }
    }

    @Inject
    public RootResource(Config config) {
        this.config = config;
    }

    protected List<Link> createLinks() {
        // Hidden resources will be omitted from the supported list we send to the clients:
        List<String> hideResources = Arrays.asList(config.getString(
            ConfigProperties.HIDDEN_RESOURCES).split(" "));

        List<Link> newLinks = new LinkedList<Link>();
        for (Class clazz : RESOURCE_CLASSES) {
            add(resourceLink(clazz), hideResources, newLinks);
        }

        for (Map.Entry<String, Method> entry : PSEUDO_RESOURCES.entrySet()) {
            String rel = entry.getKey();
            Method method = entry.getValue();

            add(methodLink(rel, method), hideResources, newLinks);
        }
        return newLinks;
    }

    protected Link methodLink(String rel, Method m) {
        Path resource = m.getDeclaringClass().getAnnotation(Path.class);
        Path method = m.getAnnotation(Path.class);

        String href = resource.value() + "/" + method.value();
        // Remove doubled slashes and trailing slash
        href = href.replaceAll("/+", "/").replaceAll("/$", "");

        return new Link(rel, href);
    }

    protected Link resourceLink(Class clazz) {
        Path a = (Path) clazz.getAnnotation(Path.class);
        String href = a.value();
        String rel = href;
        // Chop off leading "/" for the resource name:
        if (rel.charAt(0) == '/') {
            rel = rel.substring(1);
        }

        return new Link(rel, href);
    }

    private void add(Link link, List<String> hideResources, List<Link> newLinks) {
        String rel = link.getRel();
        if (!hideResources.contains(rel)) {
            newLinks.add(link);
        }
        else {
            log.debug("Hiding supported resource: " + rel);
        }
    }

    /**
     * @httpcode 200
     * @return a list of links
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
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
}
