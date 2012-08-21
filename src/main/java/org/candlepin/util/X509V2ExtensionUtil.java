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
package org.candlepin.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import org.apache.log4j.Logger;
import org.candlepin.config.Config;
import org.candlepin.json.model.Content;
import org.candlepin.json.model.EntitlementBody;
import org.candlepin.json.model.Order;
import org.candlepin.json.model.Service;
import org.candlepin.json.model.Subscription;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.EnvironmentContent;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.pki.X509ByteExtensionWrapper;
import org.candlepin.pki.X509ExtensionWrapper;
import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;
import org.codehaus.jackson.annotate.JsonMethod;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.annotate.JsonSerialize;

import com.google.common.collect.Collections2;
import com.google.inject.Inject;

/**
 * X509ExtensionUtil
 */
public class X509V2ExtensionUtil extends X509Util{

    private static Logger log = Logger.getLogger(X509V2ExtensionUtil.class);
    private Config config;
    private EntitlementCurator entCurator;
    private String thisVersion = "2.0";


    @Inject
    public X509V2ExtensionUtil(Config config, EntitlementCurator entCurator) {
        // Output everything in UTC
        this.config = config;
        this.entCurator = entCurator;
    }

    public Set<X509ExtensionWrapper> getExtensions(Set<Product> products,
        Entitlement ent, String contentPrefix,
        Map<String, EnvironmentContent> promotedContent,
        org.candlepin.model.Subscription sub) {
        Set<X509ExtensionWrapper> toReturn = new LinkedHashSet<X509ExtensionWrapper>();

        X509ExtensionWrapper versionExtension =
            new X509ExtensionWrapper(OIDUtil.REDHAT_OID + "." +
                OIDUtil.TOPLEVEL_NAMESPACES.get(OIDUtil.ENTITLEMENT_VERSION_KEY),
                false, thisVersion);

        toReturn.add(versionExtension);

        return toReturn;
    }

    public Set<X509ByteExtensionWrapper> getByteExtensions(Set<Product> products,
        Entitlement ent, String contentPrefix,
        Map<String, EnvironmentContent> promotedContent,
        org.candlepin.model.Subscription sub) throws IOException {
        Set<X509ByteExtensionWrapper> toReturn =
            new LinkedHashSet<X509ByteExtensionWrapper>();

        byte[] value = new byte[0];

        EntitlementBody eb = createEntitlementBodyContent(products, ent,
            contentPrefix, promotedContent, sub);

        List<Content> contentList = createContentList(eb);
        List<Node> nodeList = getNodeList(contentList);
        byte[] pathDictionary = makePathDictionary(nodeList);
        List<Node> treeNodeList = new ArrayList<Node>();
        treeNodeList.addAll(nodeList);
        Node root = makeTree(treeNodeList);
        List<String[]> leafCodes = encodeLeafs(nodeList, root);
        List<List<String[]>> pathCodes = encodeContentURLs(contentList, root);

        if (log.isDebugEnabled()) {
            debugOutputTreeAndCodes(root, nodeList, leafCodes, contentList, pathCodes);
        }
        X509ByteExtensionWrapper bodyExtension =
            new X509ByteExtensionWrapper(OIDUtil.REDHAT_OID + "." +
                OIDUtil.TOPLEVEL_NAMESPACES.get(OIDUtil.ENTITLEMENT_DATA_KEY),
                false, pathDictionary);
        toReturn.add(bodyExtension);

        return toReturn;
    }

    private byte[] makePathDictionary(List<Node> nodes) throws IOException {
        byte[] result = new byte[0];
        for (Node n : nodes) {
            try {
                byte[] thisNode = processPayload(n.value);
                byte[] combined = new byte[result.length + thisNode.length + 1];
                for (int i = 0; i < combined.length - 1; ++i) {
                    combined[i] = i < result.length ? result[i] :
                        thisNode[i - result.length];
                }
                result = combined;
            }
            catch (UnsupportedEncodingException uee) {
                throw new IOException(uee);
            }
        }
        return result;
    }

    private byte[] processPayload(String payload)
        throws IOException, UnsupportedEncodingException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DeflaterOutputStream dos = new DeflaterOutputStream(baos,
            new Deflater(Deflater.HUFFMAN_ONLY));
        dos.write(payload.getBytes("UTF-8"));
        dos.finish();
        dos.close();
        return baos.toByteArray();
    }

    public EntitlementBody createEntitlementBody(Set<Product> products,
        Entitlement ent, String contentPrefix,
        Map<String, EnvironmentContent> promotedContent,
        org.candlepin.model.Subscription sub) {

        EntitlementBody toReturn = new EntitlementBody();
        toReturn.setConsumer(ent.getConsumer().getUuid());
        toReturn.setQuantity(ent.getQuantity());
        toReturn.setSubscription(createSubscription(sub, ent));
        toReturn.setOrder(createOrder(sub));
        toReturn.setProducts(createProducts(products, contentPrefix, promotedContent,
            ent.getConsumer(), ent));

        return toReturn;
    }

    public EntitlementBody createEntitlementBodyContent(Set<Product> products,
        Entitlement ent, String contentPrefix,
        Map<String, EnvironmentContent> promotedContent,
        org.candlepin.model.Subscription sub) {

        EntitlementBody toReturn = new EntitlementBody();
        toReturn.setProducts(createProducts(products, contentPrefix, promotedContent,
            ent.getConsumer(), ent));

        return toReturn;
    }

    public Subscription createSubscription(
        org.candlepin.model.Subscription sub, Entitlement ent) {
        Subscription toReturn = new Subscription();
        toReturn.setSku(sub.getProduct().getId().toString());
        toReturn.setName(sub.getProduct().getName());

        String warningPeriod = sub.getProduct().getAttributeValue(
            "warning_period");
        if (warningPeriod != null && !warningPeriod.trim().equals("")) {
            // only included if not the default value of 0
            if (!warningPeriod.equals("0")) {
                toReturn.setWarning(new Integer(warningPeriod));
            }
        }

        String socketLimit = sub.getProduct().getAttributeValue("sockets");
        if (socketLimit != null && !socketLimit.trim().equals("")) {
            toReturn.setSockets(new Integer(socketLimit));
        }

        String management = sub.getProduct().getAttributeValue("management_enabled");
        if (management != null && !management.trim().equals("")) {
            // only included if not the default value of false
            Boolean m = new Boolean(management.equalsIgnoreCase("true") ||
                management.equalsIgnoreCase("1"));
            if (m) {
                toReturn.setManagement(m);
            }
        }

        String stackingId = sub.getProduct().getAttributeValue("stacking_id");
        if (stackingId != null && !stackingId.trim().equals("")) {
            toReturn.setStackingId(stackingId);
        }

        String virtOnly = ent.getPool().getAttributeValue("virt_only");
        if (virtOnly != null && !virtOnly.trim().equals("")) {
            // only included if not the default value of false
            Boolean vo = new Boolean(virtOnly.equalsIgnoreCase("true") ||
                virtOnly.equalsIgnoreCase("1"));
            if (vo) {
                toReturn.setVirtOnly(vo);
            }
        }

        toReturn.setService(createService(sub));
        return toReturn;
    }

    private Service createService(org.candlepin.model.Subscription sub) {
        if (sub.getProduct().getAttributeValue("support_level") == null &&
            sub.getProduct().getAttributeValue("support_type") == null) {
            return null;
        }
        Service toReturn = new Service();
        toReturn.setLevel(sub.getProduct().getAttributeValue("support_level"));
        toReturn.setType(sub.getProduct().getAttributeValue("support_type"));

        return toReturn;
    }

    public Order createOrder(org.candlepin.model.Subscription sub) {
        SimpleDateFormat iso8601DateFormat = Util.getUTCDateFormat();
        Order toReturn = new Order();

        toReturn.setNumber(sub.getId().toString());
        toReturn.setQuantity(sub.getQuantity());
        toReturn.setStart(iso8601DateFormat.format(sub.getStartDate()));
        toReturn.setEnd(iso8601DateFormat.format(sub.getEndDate()));

        if (sub.getContractNumber() != null &&
            !sub.getContractNumber().trim().equals("")) {
            toReturn.setContract(sub.getContractNumber());
        }

        if (sub.getAccountNumber() != null &&
            !sub.getAccountNumber().trim().equals("")) {
            toReturn.setAccount(sub.getAccountNumber());
        }

        return toReturn;
    }

    public List<org.candlepin.json.model.Product> createProducts(Set<Product> products,
        String contentPrefix, Map<String, EnvironmentContent> promotedContent,
        Consumer consumer, Entitlement ent) {
        List<org.candlepin.json.model.Product> toReturn =
            new ArrayList<org.candlepin.json.model.Product>();

        for (Product p : Collections2
            .filter(products, PROD_FILTER_PREDICATE)) {
            toReturn.add(mapProduct(p, contentPrefix, promotedContent, consumer, ent));
        }
        return toReturn;
    }

    private org.candlepin.json.model.Product mapProduct(Product product,
        String contentPrefix, Map<String, EnvironmentContent> promotedContent,
        Consumer consumer, Entitlement ent) {

        org.candlepin.json.model.Product toReturn = new org.candlepin.json.model.Product();

        toReturn.setId(product.getId());
        toReturn.setName(product.getName());

        String version = product.hasAttribute("version") ?
            product.getAttributeValue("version") : "";
        toReturn.setVersion(version);

        String arch = product.hasAttribute("arch") ?
            product.getAttributeValue("arch") : "";
        StringTokenizer st = new StringTokenizer(arch, ",");
        List<String> archList = new ArrayList<String>();
        while (st.hasMoreElements()) {
            archList.add((String) st.nextElement());
        }
        toReturn.setArchitectures(archList);

        toReturn.setContent(createContent(filterProductContent(product, ent),
            contentPrefix, promotedContent, consumer));

        return toReturn;
    }

    public List<Content> createContent(
        Set<ProductContent> productContent, String contentPrefix,
        Map<String, EnvironmentContent> promotedContent, Consumer consumer) {

        List<Content> toReturn = new ArrayList<Content>();

        boolean enableEnvironmentFiltering = config.environmentFileringEnabled();

        for (ProductContent pc : productContent) {
            Content content = new Content();
            if (enableEnvironmentFiltering) {
                if (consumer.getEnvironment() != null && !promotedContent.containsKey(
                    pc.getContent().getId())) {
                    log.debug("Skipping content not promoted to environment: " +
                        pc.getContent().getId());
                    continue;
                }
            }

            // augment the content path with the prefix if it is passed in
            String contentPath = pc.getContent().getContentUrl();
            if (contentPrefix != null) {
                contentPath = contentPrefix + contentPath;
            }

            content.setId(pc.getContent().getId());
            content.setType(pc.getContent().getType());
            content.setName(pc.getContent().getName());
            content.setLabel(pc.getContent().getLabel());
            content.setVendor(pc.getContent().getVendor());
            content.setPath(contentPath);
            content.setGpgUrl(pc.getContent().getGpgUrl());

            // Check if we should override the enabled flag due to setting on promoted
            // content:
            Boolean enabled = pc.getEnabled();
            if ((consumer.getEnvironment() != null) && enableEnvironmentFiltering) {
                // we know content has been promoted at this point:
                Boolean enabledOverride = promotedContent.get(
                    pc.getContent().getId()).getEnabled();
                if (enabledOverride != null) {
                    log.debug("overriding enabled flag: " + enabledOverride);
                    enabled = enabledOverride;
                }
            }
            // only included if not the default value of true
            if (!enabled) {
                content.setEnabled(enabled);
            }

            // Include metadata expiry if specified on the content:
            if (pc.getContent().getMetadataExpire() != null) {
                content.setMetadataExpire(pc.getContent().getMetadataExpire());
            }

            // Include required tags if specified on the content set:
            String requiredTags = pc.getContent().getRequiredTags();
            if ((requiredTags != null) && !requiredTags.equals("")) {
                StringTokenizer st = new StringTokenizer(requiredTags, ",");
                List<String> tagList = new ArrayList<String>();
                while (st.hasMoreElements()) {
                    tagList.add((String) st.nextElement());
                }
                content.setRequiredTags(tagList);
            }
            toReturn.add(content);
        }
        return toReturn;
    }

    /**
     * Scan the product content looking for any which modify some other product. If found
     * we must check that this consumer has another entitlement granting them access
     * to that modified product. If they do not, we should filter out this content.
     *
     * @param prod
     * @param ent
     * @return ProductContent to include in the certificate.
     */
    public Set<ProductContent> filterProductContent(Product prod, Entitlement ent) {
        Set<ProductContent> filtered = new HashSet<ProductContent>();

        for (ProductContent pc : prod.getProductContent()) {
            boolean include = true;
            if (pc.getContent().getModifiedProductIds().size() > 0) {
                include = false;
                Set<String> prodIds = pc.getContent().getModifiedProductIds();
                // If consumer has an entitlement to just one of the modified products,
                // we will include this content set:
                for (String prodId : prodIds) {
                    Set<Entitlement> entsProviding = entCurator.listProviding(
                        ent.getConsumer(), prodId, ent.getStartDate(), ent.getEndDate());
                    if (entsProviding.size() > 0) {
                        include = true;
                        break;
                    }
                }
            }

            if (include) {
                filtered.add(pc);
            }
            else {
                log.debug("No entitlements found for modified products.");
                log.debug("Skipping content set: " + pc.getContent());
            }
        }
        return filtered;
    }

    public static String toJson(Object anObject) {
        String output = "";
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonSerialize.Inclusion.NON_NULL);
        mapper.setVisibility(JsonMethod.FIELD, Visibility.ANY);
        try {
            output = mapper.writeValueAsString(anObject);
        }
        catch (Exception e) {
            log.error("Could no serialize the object to json " + anObject, e);
        }
        return output;
    }

    protected List<Content> createContentList(EntitlementBody eb) {
        // collect content URL's
        List<Content> contentList = new ArrayList<Content>();
        for (org.candlepin.json.model.Product p : eb.getProducts()) {
            for (org.candlepin.json.model.Content c : p.getContent()) {
                contentList.add(c);
            }
        }
        return contentList;
    }

    private void debugOutputTreeAndCodes(Node tree, List<Node> nodes,
        List<String[]> nodePaths, List<Content> contentList,
        List<List<String[]>> urls) {
        log.debug("Node Tree: " + writeTrie(tree, new ArrayList<String>()));
        log.debug("Node paths: ");
        writeNodePaths(nodes, nodePaths);
        log.debug("URL Codes: ");
        writeURLCodes(contentList, urls);
    }

    private void writeNodePaths(List<Node> nodes, List<String[]> nodePaths) {
        int line = 0;
        for (Node n : nodes) {
            log.debug("Node: " + n.value);
            String all = "";
            for (String item : nodePaths.get(line++)) {
                all += item;
            }
            log.debug("Path: [" + all + "]");
        }
    }

    private void writeURLCodes(List<Content> contentList, List<List<String[]>> urls) {
        int line = 0;
        for (List<String[]> url : urls) {
            log.debug("URL: " + contentList.get(line++).getPath());
            String all = "";
            for (String[] path : url) {
                for (String item : path) {
                    all += item;
                }
                all += "-";
            }
            log.debug("[" + all + "]");
        }
    }

    private List<String> writeTrie(Node x, List<String> output) {
        if (!x.value.equals("")) {
            output.add(x.value);
            return output;
        }
        output = writeTrie(x.left, output);
        output = writeTrie(x.right, output);
        return output;
    }

    private Node makeTree(List<Node> nodesList) {
        while (nodesList.size() > 1) {
            Node node1 = findSmallest(null, nodesList);
            Node node2 = findSmallest(node1, nodesList);
            Node parent = mergeNodes(node1, node2);
            nodesList.add(parent);
            nodesList.remove(node1);
            nodesList.remove(node2);
        }
        return nodesList.get(0);
    }

    private List<Node> getNodeList(List<Content> contentSet) {
        List<Node> nodes = new ArrayList<Node>();
        for (Content c : contentSet) {
            String url = c.getPath();
            StringTokenizer st = new StringTokenizer(url, "/");
            for (; st.hasMoreTokens();) {
                String nodeVal = st.nextToken();
                Node foundNode = null;
                for (Node n : nodes) {
                    if (n.value.equals(nodeVal)) {
                        foundNode = n;
                        break;
                    }
                }
                if (foundNode == null) {
                    foundNode = new Node(nodeVal);
                }
                foundNode.weight++;
                nodes.remove(foundNode);
                nodes.add(foundNode);
            }
        }
        return nodes;
    }

    private Node findSmallest(Node notThis, List<Node> nodes) {
        Node smallest = null;
        for (Node n : nodes) {
            if (smallest == null || n.weight < smallest.weight) {
                if (notThis == null || !n.value.equals(notThis.value)) {
                    smallest = n;
                }
            }
        }
        return smallest;
    }

    private Node mergeNodes(Node node1, Node node2) {
        Node left = node1.weight <= node2.weight ? node1 : node2;
        Node right = node1.weight > node2.weight ? node1 : node2;
        Node parent = new Node("", left.weight + right.weight, left, right);
        return parent;
    }

    private static class Node {
        private String value = "";
        private int weight = 0;
        private Node left = null;
        private Node right = null;

        Node(String value, int weight, Node left, Node right) {
            this.value = value;
            this.weight = weight;
            this.left = left;
            this.right = right;
        }
        Node(String value) {
            this.value = value;
        }
    }

    private List<String[]> encodeLeafs(List<Node> nodeList, Node tree) {
        List<String[]> result = new ArrayList<String[]>();
        for (Node n : nodeList) {
            List<String> location = retrieveNodeLocation(n.value, tree,
                new ArrayList<String>());
            String[] path = location.toArray(new String[0]);
            result.add(path);
        }
        return result;
    }

    private List<List<String[]>> encodeContentURLs(List<Content> contentList, Node tree) {
        // final set of locator codes. each list member is a url. each string array is a
        // url item's location
        List<List<String[]>> result = new ArrayList<List<String[]>>();

        // cache to reduce number of tree walks needed
        Map<String, String[]> cache = new HashMap<String, String[]>();

        for (Content c : contentList) {
            List<String[]> path = new ArrayList<String[]>();
            String url = c.getPath();
            StringTokenizer st = new StringTokenizer(url, "/");
            for (; st.hasMoreTokens();) {
                String pathVal = st.nextToken();
                if (cache.get(pathVal) != null) {
                    path.add(cache.get(pathVal));
                }
                else {
                    List<String> location = retrieveNodeLocation(pathVal, tree,
                        new ArrayList<String>());
                    String[] pathArray = location.toArray(new String[0]);
                    path.add(pathArray);
                    cache.put(pathVal, pathArray);
                }
            }
            result.add(path);
        }
        return result;
    }

    private List<String> retrieveNodeLocation(String pathSegment,
        Node tree, List<String> location) {
        Node left = tree.left;
        Node right = tree.right;
        if (left != null && pathSegment.equals(left.value)) {
            location.add("0");
            return location;
        }
        else if (right != null && pathSegment.equals(right.value)) {
            location.add("1");
            return location;
        }
        if (left != null) {
            List<String> leftLocation = new ArrayList<String>();
            leftLocation.addAll(location);
            leftLocation.add("0");
            List<String> moreLeftLocation = retrieveNodeLocation(pathSegment,
                left, leftLocation);
            if (moreLeftLocation != null) {
                return moreLeftLocation;
            }
        }
        if (right != null) {
            List<String> rightLocation = new ArrayList<String>();
            rightLocation.addAll(location);
            rightLocation.add("1");
            List<String> moreRightLocation = retrieveNodeLocation(pathSegment,
                right, rightLocation);
            if (moreRightLocation != null) {
                return moreRightLocation;
            }
        }
        return null;
    }
}
