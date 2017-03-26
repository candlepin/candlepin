-- useful script for cleaning old database backups, making it useful for internal testing.
-- beware that there may be edge cases this script does not accommodate for. For example,
-- entitlements may have end date overrides over the pool's end date.

-- sql for purging pools and its' related entities.
-- helpful for database backups where ExpiredPoolsJob has not run for a long time.
SET FOREIGN_KEY_CHECKS=0;

DELETE FROM cp_pool_attribute WHERE pool_id = (SELECT id FROM cp_pool WHERE enddate < now() AND id = cp_pool_attribute.pool_id);
DELETE FROM cp_pool_branding WHERE pool_id = (SELECT id FROM cp_pool WHERE enddate < now() AND id = cp_pool_branding.pool_id);
DELETE FROM cp2_pool_derprov_products WHERE pool_id = (SELECT id FROM cp_pool WHERE enddate < now() AND id = cp2_pool_derprov_products.pool_id);
DELETE FROM cp2_pool_provided_products WHERE pool_id = (SELECT id FROM cp_pool WHERE enddate < now() AND id = cp2_pool_provided_products.pool_id);
DELETE FROM cp2_pool_source_sub WHERE pool_id = (SELECT id FROM cp_pool WHERE enddate < now() AND id = cp2_pool_source_sub.pool_id);
DELETE FROM cp_pool_products WHERE pool_id = (SELECT id FROM cp_pool WHERE enddate < now() AND id = cp_pool_products.pool_id);
DELETE FROM cp_pool_source_sub WHERE pool_id = (SELECT id FROM cp_pool WHERE enddate < now() AND id = cp_pool_source_sub.pool_id);

-- These are empty for our test data
--DELETE FROM cp_cert_serial cs WHERE EXISTS (SELECT serial_id FROM cp_certificate cert WHERE EXISTS (SELECT certificate_id FROM cp_pool WHERE enddate < now()));
--DELETE FROM cp_certificate cert WHERE id IN (SELECT certificate_id FROM cp_pool WHERE enddate < now() AND certificate_id IS NOT NULL);
--DELETE FROM cp_entitlement ent WHERE id = (SELECT sourceentitlement_id FROM cp_pool WHERE enddate < now() AND sourceentitlement_id IS NOT NULL);


DELETE FROM cp_entitlement WHERE pool_id = (SELECT id FROM cp_pool WHERE enddate < now() AND id = cp_entitlement.pool_id);
DELETE FROM cp_pool WHERE enddate < now();

SET FOREIGN_KEY_CHECKS=1;


-- sql for postponing cert exirations.
-- helpful for database backups where CertificateRevocationListTask has not run for a long time.
update cp_cert_serial  set  expiration = DATE_ADD(now(), INTERVAL 1 YEAR) where expiration < DATE_ADD(now(), INTERVAL 30 DAY);
