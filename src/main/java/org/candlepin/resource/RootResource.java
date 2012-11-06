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

import com.google.inject.Inject;

import org.apache.log4j.Logger;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;


import org.candlepin.auth.interceptor.SecurityHole;
import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;

/**
 * A root resource, responsible for returning client a struct of links to the
 * various resources Candlepin exposes. This list will be filtered based on the
 * permissions of the caller.
 */
@Path("/")
public class RootResource {

    private static Logger log = Logger.getLogger(RootResource.class);
    public static final List<Class> RESOURCE_CLASSES;
    private Config config;
    private static List<Link> links = null;

    static {
        RESOURCE_CLASSES = new LinkedList<Class>();
        RESOURCE_CLASSES.add(AdminResource.class);
        RESOURCE_CLASSES.add(UserResource.class);
        RESOURCE_CLASSES.add(AtomFeedResource.class);
        RESOURCE_CLASSES.add(CertificateSerialResource.class);
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
    }

    @Inject
    public RootResource(Config config) {
        this.config = config;
    }

    @SecurityHole(noAuth = true, anon = true)
    protected List<Link> createLinks() {
        // Hidden resources will be omitted from the supported list we send to the clients:
        List<String> hideResources = Arrays.asList(config.getString(
            ConfigProperties.HIDDEN_RESOURCES).split(" "));

        List<Link> newLinks = new LinkedList<Link>();
        for (Class c : RESOURCE_CLASSES) {
            Path a = (Path) c.getAnnotation(Path.class);
            String href = a.value();
            String rel = href;
            // Chop off leading "/" for the resource name:
            if (rel.charAt(0) == '/') {
                rel = rel.substring(1);
            }

            if (!hideResources.contains(rel)) {
                newLinks.add(new Link(rel, href));
            }
            else {
                log.debug("Hiding supported resource: " + rel);
            }

        }
        return newLinks;
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
