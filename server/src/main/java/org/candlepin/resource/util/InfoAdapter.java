/**
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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

package org.candlepin.resource.util;

import org.candlepin.dto.api.v1.AttributeDTO;
import org.candlepin.dto.api.v1.BrandingDTO;
import org.candlepin.dto.api.v1.CdnDTO;
import org.candlepin.dto.api.v1.CertificateDTO;
import org.candlepin.dto.api.v1.CertificateSerialDTO;
import org.candlepin.dto.api.v1.ContentDTO;
import org.candlepin.dto.api.v1.NestedOwnerDTO;
import org.candlepin.dto.api.v1.OwnerDTO;
import org.candlepin.dto.api.v1.PermissionBlueprintDTO;
import org.candlepin.dto.api.v1.ProductContentDTO;
import org.candlepin.dto.api.v1.ProductDTO;
import org.candlepin.dto.api.v1.RoleDTO;
import org.candlepin.dto.api.v1.SubscriptionDTO;
import org.candlepin.dto.api.v1.UserDTO;
import org.candlepin.service.model.BrandingInfo;
import org.candlepin.service.model.CdnInfo;
import org.candlepin.service.model.CertificateInfo;
import org.candlepin.service.model.CertificateSerialInfo;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.service.model.OwnerInfo;
import org.candlepin.service.model.PermissionBlueprintInfo;
import org.candlepin.service.model.ProductContentInfo;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.service.model.RoleInfo;
import org.candlepin.service.model.SubscriptionInfo;
import org.candlepin.service.model.UserInfo;
import org.candlepin.util.ListView;
import org.candlepin.util.SetView;
import org.candlepin.util.Util;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents the adapters class to convert the DTOs object into
 * info interface implemented objects. Initially, DTO classes were
 * implementing Info interfaces. This class helps to get an equivalent
 * implemented interface object.
 */
public class InfoAdapter {

    private InfoAdapter() {
        //nothing to do here
    }

    /**
     * This method adapts the BrandingDTO
     * into BrandingInfo object.
     *
     * @param source BrandingDTO object
     *
     * @return BrandingInfo object
     */
    public static BrandingInfo brandingInfoAdapter(BrandingDTO source) {
        return new BrandingInfo() {
            /**
             * {@inheritDoc}
             */
            @Override
            public String getName() {
                return source.getName();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getType() {
                return source.getType();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getProductId() {
                return source.getProductId();
            }
        };
    }

    /**
     * This method adapts the ContentDTO
     * into ContentInfo object.
     *
     * @param source ContentDTO object
     *
     * @return ContentInfo object
     */
    @SuppressWarnings("checkstyle:methodlength")
    public static ContentInfo contentInfoAdapter(ContentDTO source) {
        return new ContentInfo() {
            /**
             * {@inheritDoc}
             */
            @Override
            public String getId() {
                return source.getId();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getType() {
                return source.getType();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getLabel() {
                return source.getLabel();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getName() {
                return source.getName();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getVendor() {
                return source.getVendor();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getContentUrl() {
                return source.getContentUrl();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getRequiredTags() {
                return source.getRequiredTags();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getReleaseVersion() {
                return source.getReleaseVer();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getGpgUrl() {
                return source.getGpgUrl();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getArches() {
                return source.getArches();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Long getMetadataExpiration() {
                return source.getMetadataExpire();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Collection<String> getRequiredProductIds() {
                return source.getModifiedProductIds();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Date getCreated() {
                return source.getCreated() != null ?
                    new Date(source.getCreated().toInstant().toEpochMilli()) : null;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Date getUpdated() {
                return source.getUpdated() != null ?
                    new Date(source.getUpdated().toInstant().toEpochMilli()) : null;
            }
        };
    }

    /**
     * This method adapts the NestedOwnerDTO
     * into OwnerInfo object.
     *
     * @param source NestedOwnerDTO object
     *
     * @return OwnerInfo object
     */
    public static OwnerInfo ownerInfoAdapter(NestedOwnerDTO source) {
        return new OwnerInfo() {
            @Override
            public String getKey() {
                return source.getKey();
            }

            @Override
            public Date getCreated() {
                return null;
            }

            @Override
            public Date getUpdated() {
                return null;
            }
        };
    }

    /**
     * This method adapts the NestedOwnerDTO
     * into OwnerInfo object.
     *
     * @param source NestedOwnerDTO object
     *
     * @return OwnerInfo object
     */
    public static OwnerInfo ownerInfoAdapter(OwnerDTO source) {
        return new OwnerInfo() {
            @Override
            public String getKey() {
                return source.getKey();
            }

            @Override
            public Date getCreated() {
                return null;
            }

            @Override
            public Date getUpdated() {
                return null;
            }
        };
    }

    /**
     * This method adapts the PermissionBlueprintDTO
     * into PermissionBlueprintInfo object.
     *
     * @param source PermissionBlueprintDTO object
     *
     * @return PermissionBlueprintInfo object
     */
    public static PermissionBlueprintInfo permissionBlueprintInfoAdapter(PermissionBlueprintDTO source) {

        return new PermissionBlueprintInfo() {

            /**
             * {@inheritDoc}
             */
            @Override
            public OwnerInfo getOwner() {
                return ownerInfoAdapter(source.getOwner());
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getTypeName() {
                return source.getType();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getAccessLevel() {
                return source.getAccess();
            }
        };
    }

    /**
     * This method adapts the ProductDTO
     * into ProductInfo object.
     *
     * @param source ProductDTO object
     *
     * @return ProductInfo object
     */
    @SuppressWarnings("checkstyle:methodlength")
    public static ProductInfo productInfoAdapter(ProductDTO source) {
        return new ProductInfo() {
            /**
             * {@inheritDoc}
             */
            @Override
            public String getId() {
                return source.getId();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getName() {
                return source.getName();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Long getMultiplier() {
                return source.getMultiplier();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Collection<String> getDependentProductIds() {
                return source.getDependentProductIds();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Map<String, String> getAttributes() {
                if (source.getAttributes() == null) {
                    return null;
                }
                return Util.toMap(source.getAttributes());
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getAttributeValue(String key) {
                if (source.getAttributes() == null || key == null) {
                    return null;
                }
                return source.getAttributes().stream()
                    .filter(attribute -> key.equals(attribute.getName()))
                    .findFirst()
                    .map(AttributeDTO::getValue)
                    .orElse(null);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Collection<? extends ProductContentInfo> getProductContent() {

                List<ProductContentInfo> productContentInfos = null;
                Collection<ProductContentDTO> productContentDTOs =
                    source.getProductContent();

                if (productContentDTOs != null) {
                    productContentInfos = new ArrayList<>();

                    for (ProductContentDTO pc : productContentDTOs) {
                        if (pc != null && pc.getContent() != null) {

                            productContentInfos.add(new ProductContentInfo() {
                                @Override
                                public ContentInfo getContent() {
                                    return contentInfoAdapter(pc.getContent());
                                }

                                @Override
                                public Boolean isEnabled() {
                                    return pc.getEnabled();
                                }
                            });
                        }
                    }
                }

                return productContentInfos != null ?
                    new ListView<>(productContentInfos) : null;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Collection<? extends BrandingInfo> getBranding() {
                Set<BrandingInfo> brandingInfoSet = null;
                Set<BrandingDTO> brandingDTOSet = source.getBranding();

                if (brandingDTOSet != null) {
                    brandingInfoSet = new HashSet<>();

                    for (BrandingDTO branding : brandingDTOSet) {
                        brandingInfoSet.add(brandingInfoAdapter(branding));
                    }
                }

                return brandingInfoSet != null ?
                    new SetView<>(brandingInfoSet) : null;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public ProductInfo getDerivedProduct() {
                if (source.getDerivedProduct() != null) {
                    return productInfoAdapter(source.getDerivedProduct());
                }
                return null;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Collection<? extends ProductInfo> getProvidedProducts() {
                Set<ProductInfo> productInfoSet = null;
                Set<ProductDTO> productDTOSet = source.getProvidedProducts();

                if (productDTOSet != null) {
                    productInfoSet = new HashSet<>();

                    for (ProductDTO productDTO : productDTOSet) {
                        productInfoSet.add(productInfoAdapter(productDTO));
                    }
                }

                return productInfoSet != null ?
                    new SetView<>(productInfoSet) : null;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Date getCreated() {
                return Util.toDate(source.getCreated());
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Date getUpdated() {
                return Util.toDate(source.getUpdated());
            }
        };
    }

    /**
     * This method adapts the RoleDTO into RoleInfo object.
     *
     * @param source RoleDTO object
     *
     * @return RoleInfo object
     */
    public static RoleInfo roleInfoAdapter(RoleDTO source) {
        return new RoleInfo() {

            @Override
            public Date getCreated() {
                return source.getCreated() != null ?
                    new Date(source.getCreated().toInstant().toEpochMilli()) : null;
            }

            @Override
            public Date getUpdated() {
                return source.getUpdated() != null ?
                    new Date(source.getUpdated().toInstant().toEpochMilli()) : null;
            }

            @Override
            public String getName() {
                return source.getName();
            }

            @Override
            public Collection<? extends UserInfo> getUsers() {
                Set<UserDTO> userDTOSet = source.getUsers();
                Set<UserInfo> userInfoSet = null;

                if (userDTOSet != null) {
                    userInfoSet = new HashSet<>();
                    for (UserDTO dto : userDTOSet) {
                        userInfoSet.add(userInfoAdapter(dto));
                    }
                }

                return userInfoSet;
            }

            @Override
            public Collection<? extends PermissionBlueprintInfo> getPermissions() {
                List<PermissionBlueprintDTO> blueprintDTOList = source.getPermissions();
                List<PermissionBlueprintInfo> blueprintInfoList = null;

                if (blueprintDTOList != null) {
                    blueprintInfoList = new ArrayList<>();
                    for (PermissionBlueprintDTO dto : blueprintDTOList) {
                        blueprintInfoList.add(permissionBlueprintInfoAdapter(dto));
                    }
                }
                return blueprintInfoList;
            }
        };
    }

    /**
     * This method adapts the UserDTO
     * into UserInfo object.
     *
     * @param source UserDTO object
     *
     * @return UserInfo object
     */
    public static UserInfo userInfoAdapter(UserDTO source) {
        return new UserInfo() {

            /**
             * {@inheritDoc}
             */
            @Override
            public Date getCreated() {
                return source.getCreated() != null ?
                    new Date(source.getCreated().toInstant().toEpochMilli()) : null;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Date getUpdated() {
                return source.getUpdated() != null ?
                    new Date(source.getUpdated().toInstant().toEpochMilli()) : null;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getUsername() {
                return source.getUsername();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getHashedPassword() {
                return source.getPassword() == null ? source.getPassword() :
                    Util.hash(source.getPassword());
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Boolean isSuperAdmin() {
                return source.getSuperAdmin();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Collection<? extends RoleInfo> getRoles() {
                return null;
            }
        };
    }

    /**
     * This method adapts the CdnDTO
     * into CdnInfo object.
     *
     * @param source CdnDTO object
     *
     * @return CdnInfo object
     */
    public static CdnInfo cdnInfoAdapter(CdnDTO source) {
        return new CdnInfo() {
            @Override
            public String getName() {
                return source.getName();
            }

            @Override
            public String getLabel() {
                return source.getLabel();
            }

            @Override
            public String getUrl() {
                return source.getUrl();
            }

            @Override
            public CertificateInfo getCertificate() {
                return source.getCertificate() != null ?
                    certificateInfoAdapter(source.getCertificate()) : null;
            }
        };
    }

    /**
     * This method adapts the CertificateDTO
     * into CertificateInfo object.
     *
     * @param source CertificateDTO object
     *
     * @return CertificateInfo object
     */
    public static CertificateInfo certificateInfoAdapter(CertificateDTO source) {
        return new CertificateInfo() {

            @Override
            public CertificateSerialInfo getSerial() {
                return source.getSerial() != null ? certificateSerialInfoAdapter(source.getSerial()) : null;
            }

            @Override
            public String getKey() {
                return source.getKey();
            }

            @Override
            public String getCertificate() {
                return source.getCert();
            }
        };
    }

    /**
     * This method adapts the CertificateSerialDTO
     * into CertificateSerialInfo object.
     *
     * @param source CertificateSerialDTO object
     *
     * @return CertificateSerialInfo object
     */
    public static CertificateSerialInfo certificateSerialInfoAdapter(CertificateSerialDTO source) {
        return new CertificateSerialInfo() {

            @Override
            public BigInteger getSerial() {
                return BigInteger.valueOf(source.getSerial());
            }

            @Override
            public Boolean isRevoked() {
                return source.getRevoked();
            }

            @Override
            public Boolean isCollected() {
                return source.getCollected();
            }

            @Override
            public Date getExpiration() {
                return Util.toDate(source.getExpiration());
            }
        };
    }

    /**
     * This method adapts the SubscriptionDTO
     * into SubscriptionInfo object.
     *
     * @param source SubscriptionDTO object
     *
     * @return SubscriptionInfo object
     */
    public static SubscriptionInfo subscriptionInfoAdapter(SubscriptionDTO source) {
        return new SubscriptionInfo() {

            @Override
            public String getId() {
                return source.getId();
            }

            @Override
            public OwnerInfo getOwner() {
                return source.getOwner() != null ? ownerInfoAdapter(source.getOwner()) : null;
            }

            @Override
            public ProductInfo getProduct() {
                return source.getProduct() != null ? productInfoAdapter(source.getProduct()) : null;
            }

            @Override
            public Long getQuantity() {
                return source.getQuantity();
            }

            @Override
            public Date getStartDate() {
                return Util.toDate(source.getStartDate());
            }

            @Override
            public Date getEndDate() {
                return Util.toDate(source.getEndDate());
            }

            @Override
            public Date getLastModified() {
                return Util.toDate(source.getLastModified());
            }

            @Override
            public String getContractNumber() {
                return source.getContractNumber();
            }

            @Override
            public String getAccountNumber() {
                return source.getAccountNumber();
            }

            @Override
            public String getOrderNumber() {
                return source.getOrderNumber();
            }

            @Override
            public String getUpstreamPoolId() {
                return source.getUpstreamPoolId();
            }

            @Override
            public String getUpstreamEntitlementId() {
                return source.getUpstreamEntitlementId();
            }

            @Override
            public String getUpstreamConsumerId() {
                return source.getUpstreamConsumerId();
            }

            @Override
            public CdnInfo getCdn() {
                return source.getCdn() != null ? cdnInfoAdapter(source.getCdn()) : null;
            }

            @Override
            public CertificateInfo getCertificate() {
                return source.getCertificate() != null ?
                    certificateInfoAdapter(source.getCertificate()) : null;
            }
        };
    }
}
