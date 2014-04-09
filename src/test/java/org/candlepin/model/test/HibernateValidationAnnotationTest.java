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
package org.candlepin.model.test;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import junit.framework.Assert;

import org.candlepin.model.AbstractCertificate;
import org.candlepin.model.Branding;
import org.candlepin.model.Cdn;
import org.candlepin.model.CdnCertificate;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Content;
import org.candlepin.model.ContentOverride;
import org.candlepin.model.DeletedConsumer;
import org.candlepin.model.DistributorVersion;
import org.candlepin.model.DistributorVersionCapability;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentContent;
import org.candlepin.model.ExporterMetadata;
import org.candlepin.model.GuestId;
import org.candlepin.model.HypervisorId;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.ImportRecord;
import org.candlepin.model.ImportUpstreamConsumer;
import org.candlepin.model.KeyPair;
import org.candlepin.model.Owner;
import org.candlepin.model.PermissionBlueprint;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductAttribute;
import org.candlepin.model.ProductCertificate;
import org.candlepin.model.ProductContent;
import org.candlepin.model.ProductPoolAttribute;
import org.candlepin.model.ProvidedProduct;
import org.candlepin.model.Role;
import org.candlepin.model.Rules;
import org.candlepin.model.SourceStack;
import org.candlepin.model.Statistic;
import org.candlepin.model.Subscription;
import org.candlepin.model.SubscriptionsCertificate;
import org.candlepin.model.UpstreamConsumer;
import org.candlepin.model.User;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.test.DatabaseTestFixture;
import org.junit.Test;

import com.google.inject.matcher.Matcher;
import com.google.inject.matcher.Matchers;

/**
 * HibernateAnnotationTest
 *
 *  Tests to determine if the correct validation annotations are set in the bean classes.
 */
public class HibernateValidationAnnotationTest extends DatabaseTestFixture{
    public HibernateValidationAnnotationTest() {
    }

    private Matcher<AnnotatedElement> sizeAndNotNull = buildMatcher(Size.class, NotNull.class);
    private Matcher<AnnotatedElement> size = buildMatcher(Size.class);
    private Matcher<AnnotatedElement> notNull = buildMatcher(NotNull.class);


    @Test
    public void abstractCertificateTest() throws Exception {
        Map<Field, Matcher<AnnotatedElement>> fm = new HashMap<Field, Matcher<AnnotatedElement>>();
        fm.put(AbstractCertificate.class.getDeclaredField("key"), notNull);
        fm.put(AbstractCertificate.class.getDeclaredField("cert"), notNull);
        runMap(fm);
    }

    @Test
    public void activationKeyTest() throws Exception {
        Map<Field, Matcher<AnnotatedElement>> fm = new HashMap<Field, Matcher<AnnotatedElement>>();
        fm.put(ActivationKey.class.getDeclaredField("id"), notNull);
        fm.put(ActivationKey.class.getDeclaredField("name"), sizeAndNotNull);
        fm.put(ActivationKey.class.getDeclaredField("owner"), notNull);
        fm.put(ActivationKey.class.getDeclaredField("releaseVer"), size);
        fm.put(ActivationKey.class.getDeclaredField("serviceLevel"), size);
        runMap(fm);
    }

    @Test
    public void brandingTest() throws Exception {
        Map<Field, Matcher<AnnotatedElement>> fm = new HashMap<Field, Matcher<AnnotatedElement>>();
        fm.put(Branding.class.getDeclaredField("id"), notNull);
        fm.put(Branding.class.getDeclaredField("productId"), sizeAndNotNull);
        fm.put(Branding.class.getDeclaredField("name"), sizeAndNotNull);
        fm.put(Branding.class.getDeclaredField("type"), sizeAndNotNull);
        runMap(fm);
    }

    @Test
    public void cdnTest() throws Exception {
        Map<Field, Matcher<AnnotatedElement>> fm = new HashMap<Field, Matcher<AnnotatedElement>>();
        fm.put(Cdn.class.getDeclaredField("id"), notNull);
        fm.put(Cdn.class.getDeclaredField("label"), sizeAndNotNull);
        fm.put(Cdn.class.getDeclaredField("name"), sizeAndNotNull);
        fm.put(Cdn.class.getDeclaredField("url"), sizeAndNotNull);
        runMap(fm);
    }

    @Test
    public void cdnCertificateTest() throws Exception {
        Map<Field, Matcher<AnnotatedElement>> fm = new HashMap<Field, Matcher<AnnotatedElement>>();
        fm.put(CdnCertificate.class.getDeclaredField("id"), notNull);
        runMap(fm);
    }

    @Test
    public void certificateSerialTest() throws Exception {
        Map<Field, Matcher<AnnotatedElement>> fm = new HashMap<Field, Matcher<AnnotatedElement>>();
        fm.put(CertificateSerial.class.getDeclaredField("id"), notNull);
        fm.put(CertificateSerial.class.getDeclaredField("revoked"), notNull);
        fm.put(CertificateSerial.class.getDeclaredField("collected"), notNull);
        runMap(fm);
    }

    @Test
    public void consumerTest() throws Exception {
        Map<Field, Matcher<AnnotatedElement>> fm = new HashMap<Field, Matcher<AnnotatedElement>>();
        fm.put(Consumer.class.getDeclaredField("id"), notNull);
        fm.put(Consumer.class.getDeclaredField("uuid"), sizeAndNotNull);
        fm.put(Consumer.class.getDeclaredField("name"), sizeAndNotNull);
        fm.put(Consumer.class.getDeclaredField("username"), size);
        fm.put(Consumer.class.getDeclaredField("entitlementStatus"), size);
        fm.put(Consumer.class.getDeclaredField("serviceLevel"), size);
        fm.put(Consumer.class.getDeclaredField("releaseVer"), size);
        fm.put(Consumer.class.getDeclaredField("facts"), size);
        runMap(fm);
    }

    @Test
    public void consumerInstalledProductTest() throws Exception {
        Map<Field, Matcher<AnnotatedElement>> fm = new HashMap<Field, Matcher<AnnotatedElement>>();
        fm.put(ConsumerInstalledProduct.class.getDeclaredField("id"), notNull);
        fm.put(ConsumerInstalledProduct.class.getDeclaredField("productId"), notNull);
        fm.put(ConsumerInstalledProduct.class.getDeclaredField("productName"), size);
        fm.put(ConsumerInstalledProduct.class.getDeclaredField("version"), size);
        fm.put(ConsumerInstalledProduct.class.getDeclaredField("arch"), size);
        runMap(fm);
    }

    @Test
    public void consumerTypeTest() throws Exception {
        Map<Field, Matcher<AnnotatedElement>> fm = new HashMap<Field, Matcher<AnnotatedElement>>();
        fm.put(ConsumerType.class.getDeclaredField("id"), notNull);
        fm.put(ConsumerType.class.getDeclaredField("label"), sizeAndNotNull);
        fm.put(ConsumerType.class.getDeclaredField("manifest"), notNull);
        runMap(fm);
    }

    @Test
    public void contentTest() throws Exception {
        Map<Field, Matcher<AnnotatedElement>> fm = new HashMap<Field, Matcher<AnnotatedElement>>();
        fm.put(Content.class.getDeclaredField("id"), sizeAndNotNull);
        fm.put(Content.class.getDeclaredField("type"), sizeAndNotNull);
        fm.put(Content.class.getDeclaredField("label"), sizeAndNotNull);
        fm.put(Content.class.getDeclaredField("name"), sizeAndNotNull);
        fm.put(Content.class.getDeclaredField("vendor"), sizeAndNotNull);
        fm.put(Content.class.getDeclaredField("contentUrl"), size);
        fm.put(Content.class.getDeclaredField("requiredTags"), size);
        fm.put(Content.class.getDeclaredField("releaseVer"), size);
        fm.put(Content.class.getDeclaredField("gpgUrl"), size);
        fm.put(Content.class.getDeclaredField("modifiedProductIds"), size);
        fm.put(Content.class.getDeclaredField("arches"), size);
        runMap(fm);
    }

    @Test
    public void contentOverrideTest() throws Exception {
        Map<Field, Matcher<AnnotatedElement>> fm = new HashMap<Field, Matcher<AnnotatedElement>>();
        fm.put(ContentOverride.class.getDeclaredField("id"), notNull);
        fm.put(ContentOverride.class.getDeclaredField("contentLabel"), sizeAndNotNull);
        fm.put(ContentOverride.class.getDeclaredField("name"), sizeAndNotNull);
        fm.put(ContentOverride.class.getDeclaredField("value"), sizeAndNotNull);
        runMap(fm);
    }

    @Test
    public void deletedConsumerTest() throws Exception {
        Map<Field, Matcher<AnnotatedElement>> fm = new HashMap<Field, Matcher<AnnotatedElement>>();
        fm.put(DeletedConsumer.class.getDeclaredField("id"), notNull);
        fm.put(DeletedConsumer.class.getDeclaredField("consumerUuid"), sizeAndNotNull);
        fm.put(DeletedConsumer.class.getDeclaredField("ownerId"), sizeAndNotNull);
        fm.put(DeletedConsumer.class.getDeclaredField("ownerKey"), size);
        fm.put(DeletedConsumer.class.getDeclaredField("ownerDisplayName"), size);
        runMap(fm);
    }

    @Test
    public void distributorVersionTest() throws Exception {
        Map<Field, Matcher<AnnotatedElement>> fm = new HashMap<Field, Matcher<AnnotatedElement>>();
        fm.put(DistributorVersion.class.getDeclaredField("id"), notNull);
        fm.put(DistributorVersion.class.getDeclaredField("name"), sizeAndNotNull);
        fm.put(DistributorVersion.class.getDeclaredField("displayName"), sizeAndNotNull);
        runMap(fm);
    }

    @Test
    public void distributorVersionCapabilityTest() throws Exception {
        Map<Field, Matcher<AnnotatedElement>> fm = new HashMap<Field, Matcher<AnnotatedElement>>();
        fm.put(DistributorVersionCapability.class.getDeclaredField("id"), notNull);
        fm.put(DistributorVersionCapability.class.getDeclaredField("name"), sizeAndNotNull);
        runMap(fm);
    }

    @Test
    public void entitlementTest() throws Exception {
        Map<Field, Matcher<AnnotatedElement>> fm = new HashMap<Field, Matcher<AnnotatedElement>>();
        fm.put(Entitlement.class.getDeclaredField("id"), notNull);
        fm.put(Entitlement.class.getDeclaredField("owner"), notNull);
        fm.put(Entitlement.class.getDeclaredField("consumer"), notNull);
        fm.put(Entitlement.class.getDeclaredField("pool"), notNull);
        runMap(fm);
    }

    @Test
    public void entitlementCertificateTest() throws Exception {
        Map<Field, Matcher<AnnotatedElement>> fm = new HashMap<Field, Matcher<AnnotatedElement>>();
        fm.put(EntitlementCertificate.class.getDeclaredField("id"), notNull);
        fm.put(EntitlementCertificate.class.getDeclaredField("entitlement"), notNull);
        runMap(fm);
    }

    @Test
    public void environmentTest() throws Exception {
        Map<Field, Matcher<AnnotatedElement>> fm = new HashMap<Field, Matcher<AnnotatedElement>>();
        fm.put(Environment.class.getDeclaredField("id"), notNull);
        fm.put(Environment.class.getDeclaredField("owner"), notNull);
        fm.put(Environment.class.getDeclaredField("name"), sizeAndNotNull);
        fm.put(Environment.class.getDeclaredField("description"), size);
        runMap(fm);
    }

    @Test
    public void environmentContentTest() throws Exception {
        Map<Field, Matcher<AnnotatedElement>> fm = new HashMap<Field, Matcher<AnnotatedElement>>();
        fm.put(EnvironmentContent.class.getDeclaredField("id"), notNull);
        fm.put(EnvironmentContent.class.getDeclaredField("environment"), notNull);
        fm.put(EnvironmentContent.class.getDeclaredField("contentId"), sizeAndNotNull);
        runMap(fm);
    }

    @Test
    public void exporterMetadataTest() throws Exception {
        Map<Field, Matcher<AnnotatedElement>> fm = new HashMap<Field, Matcher<AnnotatedElement>>();
        fm.put(ExporterMetadata.class.getDeclaredField("id"), notNull);
        fm.put(ExporterMetadata.class.getDeclaredField("exported"), notNull);
        fm.put(ExporterMetadata.class.getDeclaredField("type"), sizeAndNotNull);
        runMap(fm);
    }

    @Test
    public void guestIdTest() throws Exception {
        Map<Field, Matcher<AnnotatedElement>> fm = new HashMap<Field, Matcher<AnnotatedElement>>();
        fm.put(GuestId.class.getDeclaredField("id"), notNull);
        fm.put(GuestId.class.getDeclaredField("guestId"), sizeAndNotNull);
        fm.put(GuestId.class.getDeclaredField("consumer"), notNull);
        fm.put(GuestId.class.getDeclaredField("attributes"), size);
        runMap(fm);
    }

    @Test
    public void hypervisorIdTest() throws Exception {
        Map<Field, Matcher<AnnotatedElement>> fm = new HashMap<Field, Matcher<AnnotatedElement>>();
        fm.put(HypervisorId.class.getDeclaredField("id"), notNull);
        fm.put(HypervisorId.class.getDeclaredField("hypervisorId"), sizeAndNotNull);
        fm.put(HypervisorId.class.getDeclaredField("consumer"), notNull);
        fm.put(HypervisorId.class.getDeclaredField("owner"), notNull);
        runMap(fm);
    }

    @Test
    public void identityCertificateTest() throws Exception {
        Map<Field, Matcher<AnnotatedElement>> fm = new HashMap<Field, Matcher<AnnotatedElement>>();
        fm.put(IdentityCertificate.class.getDeclaredField("id"), notNull);
        runMap(fm);
    }

    @Test
    public void importRecordTest() throws Exception {
        Map<Field, Matcher<AnnotatedElement>> fm = new HashMap<Field, Matcher<AnnotatedElement>>();
        fm.put(ImportRecord.class.getDeclaredField("id"), notNull);
        fm.put(ImportRecord.class.getDeclaredField("statusMessage"), size);
        fm.put(ImportRecord.class.getDeclaredField("fileName"), size);
        fm.put(ImportRecord.class.getDeclaredField("generatedBy"), size);
        runMap(fm);
    }

    @Test
    public void importUpstreamConsumerTest() throws Exception {
        Map<Field, Matcher<AnnotatedElement>> fm = new HashMap<Field, Matcher<AnnotatedElement>>();
        fm.put(ImportUpstreamConsumer.class.getDeclaredField("id"), notNull);
        fm.put(ImportUpstreamConsumer.class.getDeclaredField("uuid"), sizeAndNotNull);
        fm.put(ImportUpstreamConsumer.class.getDeclaredField("name"), sizeAndNotNull);
        fm.put(ImportUpstreamConsumer.class.getDeclaredField("type"), notNull);
        fm.put(ImportUpstreamConsumer.class.getDeclaredField("ownerId"), notNull);
        fm.put(ImportUpstreamConsumer.class.getDeclaredField("prefixUrlWeb"), size);
        fm.put(ImportUpstreamConsumer.class.getDeclaredField("prefixUrlApi"), size);
        runMap(fm);
    }

    @Test
    public void keyPairTest() throws Exception {
        Map<Field, Matcher<AnnotatedElement>> fm = new HashMap<Field, Matcher<AnnotatedElement>>();
        fm.put(KeyPair.class.getDeclaredField("id"), notNull);
        runMap(fm);
    }

    @Test
    public void ownerTest() throws Exception {
        Map<Field, Matcher<AnnotatedElement>> fm = new HashMap<Field, Matcher<AnnotatedElement>>();
        fm.put(Owner.class.getDeclaredField("id"), notNull);
        fm.put(Owner.class.getDeclaredField("key"), sizeAndNotNull);
        fm.put(Owner.class.getDeclaredField("displayName"), sizeAndNotNull);
        fm.put(Owner.class.getDeclaredField("contentPrefix"), size);
        fm.put(Owner.class.getDeclaredField("defaultServiceLevel"), size);
        fm.put(Owner.class.getDeclaredField("logLevel"), size);
        runMap(fm);
    }

    @Test
    public void permissionBlueprintTest() throws Exception {
        Map<Field, Matcher<AnnotatedElement>> fm = new HashMap<Field, Matcher<AnnotatedElement>>();
        fm.put(PermissionBlueprint.class.getDeclaredField("id"), notNull);
        fm.put(PermissionBlueprint.class.getDeclaredField("owner"), notNull);
        fm.put(PermissionBlueprint.class.getDeclaredField("role"), notNull);
        fm.put(PermissionBlueprint.class.getDeclaredField("type"), notNull);
        runMap(fm);
    }

    @Test
    public void poolTest() throws Exception {
        Map<Field, Matcher<AnnotatedElement>> fm = new HashMap<Field, Matcher<AnnotatedElement>>();
        fm.put(Pool.class.getDeclaredField("id"), notNull);
        fm.put(Pool.class.getDeclaredField("owner"), notNull);
        fm.put(Pool.class.getDeclaredField("subscriptionId"), size);
        fm.put(Pool.class.getDeclaredField("subscriptionSubKey"), size);
        fm.put(Pool.class.getDeclaredField("quantity"), notNull);
        fm.put(Pool.class.getDeclaredField("startDate"), notNull);
        fm.put(Pool.class.getDeclaredField("endDate"), notNull);
        fm.put(Pool.class.getDeclaredField("productId"), sizeAndNotNull);
        fm.put(Pool.class.getDeclaredField("derivedProductId"), size);
        fm.put(Pool.class.getDeclaredField("restrictedToUsername"), size);
        fm.put(Pool.class.getDeclaredField("contractNumber"), size);
        fm.put(Pool.class.getDeclaredField("accountNumber"), size);
        fm.put(Pool.class.getDeclaredField("orderNumber"), size);
        fm.put(Pool.class.getDeclaredField("productName"), size);
        fm.put(Pool.class.getDeclaredField("derivedProductName"), size);
        runMap(fm);
    }

    @Test
    public void productTest() throws Exception {
        Map<Field, Matcher<AnnotatedElement>> fm = new HashMap<Field, Matcher<AnnotatedElement>>();
        fm.put(Product.class.getDeclaredField("id"), notNull);
        fm.put(Product.class.getDeclaredField("name"), sizeAndNotNull);
        fm.put(Product.class.getDeclaredField("dependentProductIds"), size);
        runMap(fm);
    }

    @Test
    public void productAttributeTest() throws Exception {
        Map<Field, Matcher<AnnotatedElement>> fm = new HashMap<Field, Matcher<AnnotatedElement>>();
        fm.put(ProductAttribute.class.getDeclaredField("id"), notNull);
        fm.put(ProductAttribute.class.getDeclaredField("name"), sizeAndNotNull);
        fm.put(ProductAttribute.class.getDeclaredField("value"), size);
        fm.put(ProductAttribute.class.getDeclaredField("product"), notNull);
        runMap(fm);
    }

    @Test
    public void productCertificateTest() throws Exception {
        Map<Field, Matcher<AnnotatedElement>> fm = new HashMap<Field, Matcher<AnnotatedElement>>();
        fm.put(ProductCertificate.class.getDeclaredField("id"), notNull);
        fm.put(ProductCertificate.class.getDeclaredField("product"), notNull);
        runMap(fm);
    }

    @Test
    public void productContentTest() throws Exception {
        Map<Field, Matcher<AnnotatedElement>> fm = new HashMap<Field, Matcher<AnnotatedElement>>();
        fm.put(ProductContent.class.getDeclaredField("product"), notNull);
        fm.put(ProductContent.class.getDeclaredField("content"), notNull);
        runMap(fm);
    }

    @Test
    public void productPoolAttributeTest() throws Exception {
        Map<Field, Matcher<AnnotatedElement>> fm = new HashMap<Field, Matcher<AnnotatedElement>>();
        fm.put(ProductPoolAttribute.class.getDeclaredField("productId"), notNull);
        runMap(fm);

    }
    @Test
    public void providedProductTest() throws Exception {
        Map<Field, Matcher<AnnotatedElement>> fm = new HashMap<Field, Matcher<AnnotatedElement>>();
        fm.put(ProvidedProduct.class.getDeclaredField("id"), notNull);
        fm.put(ProvidedProduct.class.getDeclaredField("productId"), sizeAndNotNull);
        fm.put(ProvidedProduct.class.getDeclaredField("productName"), sizeAndNotNull);
        fm.put(ProvidedProduct.class.getDeclaredField("pool"), notNull);
        runMap(fm);
    }

    @Test
    public void roleTest() throws Exception {
        Map<Field, Matcher<AnnotatedElement>> fm = new HashMap<Field, Matcher<AnnotatedElement>>();
        fm.put(Role.class.getDeclaredField("id"), notNull);
        fm.put(Role.class.getDeclaredField("name"), sizeAndNotNull);
        runMap(fm);
    }

    @Test
    public void ruleTest() throws Exception {
        Map<Field, Matcher<AnnotatedElement>> fm = new HashMap<Field, Matcher<AnnotatedElement>>();
        fm.put(Rules.class.getDeclaredField("id"), notNull);
        fm.put(Rules.class.getDeclaredField("version"), sizeAndNotNull);
        runMap(fm);
    }

    @Test
    public void sourceStackTest() throws Exception {
        Map<Field, Matcher<AnnotatedElement>> fm = new HashMap<Field, Matcher<AnnotatedElement>>();
        fm.put(SourceStack.class.getDeclaredField("id"), notNull);
        fm.put(SourceStack.class.getDeclaredField("sourceStackId"), sizeAndNotNull);
        fm.put(SourceStack.class.getDeclaredField("sourceConsumer"), notNull);
        fm.put(SourceStack.class.getDeclaredField("derivedPool"), notNull);
        runMap(fm);
    }

    @Test
    public void statisticTest() throws Exception {
        Map<Field, Matcher<AnnotatedElement>> fm = new HashMap<Field, Matcher<AnnotatedElement>>();
        fm.put(Statistic.class.getDeclaredField("id"), notNull);
        fm.put(Statistic.class.getDeclaredField("entryType"), sizeAndNotNull);
        fm.put(Statistic.class.getDeclaredField("valueReference"), size);
        fm.put(Statistic.class.getDeclaredField("value"), notNull);
        fm.put(Statistic.class.getDeclaredField("ownerId"), size);
        runMap(fm);
    }

    @Test
    public void subscriptionTest() throws Exception {
        Map<Field, Matcher<AnnotatedElement>> fm = new HashMap<Field, Matcher<AnnotatedElement>>();
        fm.put(Subscription.class.getDeclaredField("id"), notNull);
        fm.put(Subscription.class.getDeclaredField("owner"), notNull);
        fm.put(Subscription.class.getDeclaredField("product"), notNull);
        fm.put(Subscription.class.getDeclaredField("quantity"), notNull);
        fm.put(Subscription.class.getDeclaredField("startDate"), notNull);
        fm.put(Subscription.class.getDeclaredField("endDate"), notNull);
        fm.put(Subscription.class.getDeclaredField("contractNumber"), size);
        fm.put(Subscription.class.getDeclaredField("accountNumber"), size);
        fm.put(Subscription.class.getDeclaredField("orderNumber"), size);
        fm.put(Subscription.class.getDeclaredField("upstreamPoolId"), size);
        fm.put(Subscription.class.getDeclaredField("upstreamEntitlementId"), size);
        fm.put(Subscription.class.getDeclaredField("upstreamConsumerId"), size);
        runMap(fm);
    }

    @Test
    public void subscriptionsCertificateTest() throws Exception {
        Map<Field, Matcher<AnnotatedElement>> fm = new HashMap<Field, Matcher<AnnotatedElement>>();
        fm.put(SubscriptionsCertificate.class.getDeclaredField("id"), notNull);
        runMap(fm);
    }

    @Test
    public void upstreamConsumerTest() throws Exception {
        Map<Field, Matcher<AnnotatedElement>> fm = new HashMap<Field, Matcher<AnnotatedElement>>();
        fm.put(UpstreamConsumer.class.getDeclaredField("id"), notNull);
        fm.put(UpstreamConsumer.class.getDeclaredField("uuid"), sizeAndNotNull);
        fm.put(UpstreamConsumer.class.getDeclaredField("name"), sizeAndNotNull);
        fm.put(UpstreamConsumer.class.getDeclaredField("type"), notNull);
        fm.put(UpstreamConsumer.class.getDeclaredField("ownerId"), notNull);
        fm.put(UpstreamConsumer.class.getDeclaredField("prefixUrlWeb"), size);
        fm.put(UpstreamConsumer.class.getDeclaredField("prefixUrlApi"), size);
        runMap(fm);
    }

    @Test
    public void userTest() throws Exception {
        Map<Field, Matcher<AnnotatedElement>> fm = new HashMap<Field, Matcher<AnnotatedElement>>();
        fm.put(User.class.getDeclaredField("id"), notNull);
        fm.put(User.class.getDeclaredField("username"), sizeAndNotNull);
        fm.put(User.class.getDeclaredField("hashedPassword"), size);
        fm.put(User.class.getDeclaredField("superAdmin"), notNull);
        runMap(fm);
    }

    private void runMap(Map<Field, Matcher<AnnotatedElement>> fm) {
        for (Map.Entry<Field, Matcher<AnnotatedElement>> entry : fm.entrySet()) {
            Matcher<AnnotatedElement> matcher = entry.getValue();
            Field field = entry.getKey();
            Assert.assertTrue(matcher.matches(field));
        }
    }

    // If you send in a non annotation in the class list, this will blow up.
    @SuppressWarnings("unchecked")
    private Matcher<AnnotatedElement> buildMatcher(Class... annotationList) {
        if (annotationList.length == 0) {
            throw new IllegalArgumentException("annotationList cannot be empty");
        }

        Matcher<AnnotatedElement> m = Matchers.annotatedWith(annotationList[0]);

        if (annotationList.length > 1) {
            for (int i = 1; i < annotationList.length; i++) {
                m = Matchers.annotatedWith(annotationList[i]).and(m);
            }
        }

        return m;
    }

    private String longString() {
        StringBuffer result = new StringBuffer();
        for (int i = 0; i < 256; i++) {
            result.append("e");
        }
        return result.toString();
    }
}
