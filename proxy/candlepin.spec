%global _binary_filedigest_algorithm 1
%global _source_filedigest_algorithm 1
%global _binary_payload w9.gzdio
%global _source_payload w9.gzdio

Name: candlepin
Summary: Candlepin is an open source entitlement management system.
Group: Internet/Applications
License: GLPv2
Version: 0.0.17
Release: 1
URL: http://fedorahosted.org/candlepin
# Source0: https://fedorahosted.org/releases/c/a/candlepin/%{name}-%{version}.tar.gz
Source: %{name}-%{version}.tar.gz
BuildRoot: %{_tmppath}/%{name}-%{version}-%{release}-buildroot
Vendor: Red Hat, Inc
BuildArch: noarch

Requires: candlepin-webapp
BuildRequires: java >= 0:1.6.0
#BuildRequires: rubygem-buildr
%define __jar_repack %{nil}

%description
Candlepin is an open source entitlement management system.

%package tomcat5
Summary: Candlepin web application for tomcat5
Requires: tomcat5 >= 5.5
Provides: candlepin-webapp

%description tomcat5
Candlepin web application for tomcat5

%package tomcat6
Summary: Candlepin web application for tomcat6
Requires: tomcat6
Provides: candlepin-webapp

%description tomcat6
Candlepin web application for tomcat6

%package jboss
Summary: Candlepin web application for jboss
Requires: jbossas >= 4.3
Provides: candlepin-webapp

%description jboss
Candlepin web application for jboss

%prep
%setup -q 

%build
buildr clean test=no package

%install
rm -rf $RPM_BUILD_ROOT
# Create the directory structure required to lay down our files
# common
install -d -m 755 $RPM_BUILD_ROOT/%{_sysconfdir}/%{name}/
touch $RPM_BUILD_ROOT/%{_sysconfdir}/%{name}/%{name}.conf

# tomcat5
install -d -m 755 $RPM_BUILD_ROOT/%{_localstatedir}/lib/tomcat5/webapps/
install -d -m 755 $RPM_BUILD_ROOT/%{_localstatedir}/lib/tomcat5/webapps/%{name}/
unzip target/%{name}-%{version}.war -d $RPM_BUILD_ROOT/%{_localstatedir}/lib/tomcat5/webapps/%{name}/

# tomcat6
install -d -m 755 $RPM_BUILD_ROOT/%{_localstatedir}/lib/tomcat6/webapps/
install -d -m 755 $RPM_BUILD_ROOT/%{_localstatedir}/lib/tomcat6/webapps/%{name}/
unzip target/%{name}-%{version}.war -d $RPM_BUILD_ROOT/%{_localstatedir}/lib/tomcat6/webapps/%{name}/

# jbossas
install -d -m 755 $RPM_BUILD_ROOT/%{_localstatedir}/lib/jbossas/server/production/deploy/
install -d -m 755 $RPM_BUILD_ROOT/%{_localstatedir}/lib/jbossas/server/production/deploy/%{name}.war
unzip target/%{name}-%{version}.war -d $RPM_BUILD_ROOT/%{_localstatedir}/lib/jbossas/server/production/deploy/%{name}.war/

# /var/lib dir for hornetq state
install -d -m 755 $RPM_BUILD_ROOT/%{_localstatedir}/lib/%{name}

install -d -m 755 $RPM_BUILD_ROOT/%{_localstatedir}/log/%{name}

%clean
rm -rf $RPM_BUILD_ROOT

%files
%config(noreplace) %{_sysconfdir}/%{name}/%{name}.conf

%files jboss
%defattr(-,jboss,jboss,-)
%{_localstatedir}/lib/jbossas/server/production/deploy/%{name}*
%{_localstatedir}/lib/%{name}
%{_localstatedir}/log/%{name}

%files tomcat5
%defattr(644,tomcat,tomcat,775)
%{_localstatedir}/lib/tomcat5/webapps/%{name}*
%{_localstatedir}/lib/%{name}
%{_localstatedir}/log/%{name}

%files tomcat6
%defattr(644,tomcat,tomcat,775)
%{_localstatedir}/lib/tomcat6/webapps/%{name}*
%{_localstatedir}/lib/%{name}
%{_localstatedir}/log/%{name}

%changelog
* Fri Jun 11 2010 jesus m. rodriguez <jesusr@redhat.com> 0.0.17-1
- Adding email and locale to the bind by regtoken api (calfonso@redhat.com)
- Trace displayed when using invalid user name or password for consumer atom
  caused because, MediaType.APPLICATION_ATOM_XML_TYPE was not added to
  CandlepinExceptionMapper's desired response types. (anadathu@redhat.com)
- Ruby/hibernate login -> username fixes. (dgoodwin@redhat.com)
- Refactor User.login to User.username. (dgoodwin@redhat.com)
- Switch userName to username. (dgoodwin@redhat.com)
- Add javascript rule for user restricted pools. (dgoodwin@redhat.com)
- Correct Pool.attributes matching to match Product. (dgoodwin@redhat.com)
- Checkstyle fix. (dgoodwin@redhat.com)
- Pulling out consumer parent logic and trimming Consumer some. (jharris@redhat.com)
- Create a sub-pool for user license subscriptions. (dgoodwin@redhat.com)
- Begin usage of Pool attributes for triggering rules. (dgoodwin@redhat.com)
- Adding basic username test to consumer (jharris@redhat.com)
- email address and locale are being submitted to subscription adapter when
  bind by token is used (and email and locale were provided) (ddolguik@redhat.com)
- Adding username when registering a new consumer. (jharris@redhat.com)
- Fixing checkstyle errors (calfonso@redhat.com)
- Adding XMLTransient on Owner.getConsumers (calfonso@redhat.com)
- Adding the extended key usage for web authentication (calfonso@redhat.com)
- toUpperCase to avoid arch mismatch (morazi@redhat.com)
- Adding in a check_all buildr task. (jharris@redhat.com)
- Enable creation of a consumer-specific pool after entitlement.  (dgoodwin@redhat.com)
- Make unit tests use "arch" as the attribute.  (alikins@redhat.com)
- Only create subscriptions for mkt products on product import (alikins@redhat.com)
- If we haven't populated the arch/variant/version attributes, dont include
  them. (alikins@redhat.com)
- Move the arch,type,variant,and version attribs from the Product to attributes
  table (alikins@redhat.com)
- If we haven't populated the arch/variant/version attributes, dont include
  them. (alikins@redhat.com)
- Change rules to look for "arch" product attribute.  (alikins@redhat.com)
- Update ruby api and product import script to use the new format for Product (alikins@redhat.com)
- Move the arch,type,variant,and version attribs from the Product to attributes
  table (alikins@redhat.com)
- domain consumers can only consume domain products (jbowes@redhat.com)
- rules: always run the global rule (jbowes@redhat.com)
- Fixing ConsumerResource unit test. (jharris@redhat.com)
- Refactoring consumer resource to use service adapter to lookup users (jomara@redhat.com)
- cuke: add tests for domain consumer type (jbowes@redhat.com)
- added a rule to limit IPA products to domains (ddolguik@redhat.com)
- Remove setting of parent fact from cuke tests. (dgoodwin@redhat.com)
- Detect consumer parent/child relationships on child's register.  (dgoodwin@redhat.com)
- Add Consumer curator method to find a consumer bound to a specific user.
  (dgoodwin@redhat.com)
- fixed a ridiculous bit where the product id from the retrived product was
  used to retrieve it again (ddolguik@redhat.com)
- Use the import_products.rb from candlepin to optionally import product
  information. (alikins@redhat.com)
- Adding cuke tests for consumer parent/child relationships.  (jharris@redhat.com)
- fixed a whole slew of broken tests (after the inclusion of product into
  subscription) (ddolguik@redhat.com)
- removed SubscriptionProductWrapper; Subscription now has one-to-one mapping
  to Product (ddolguik@redhat.com)
- Bumping checkstyle max params to a method from 12 to 15.  (dgoodwin@redhat.com)
- Make EventCurator.listMostRecent a little more type safe.  (dgoodwin@redhat.com)
- Fix owner/consumer atom feeds. (dgoodwin@redhat.com)
- Owner/consumer atom feed code cleanup. (dgoodwin@redhat.com)
- Enable access control on consumer atom feed. (dgoodwin@redhat.com)
- Fixed junit testcases in OwnerResourceTest (anadathu@redhat.com)
- Adding cuke tests for registering as a person consumer type.  (jharris@redhat.com)
- Fix unintentional ForbiddenException. (dgoodwin@redhat.com)
- Minor test updates. (dgoodwin@redhat.com)
- Adding in single person type check plus unit tests. (jharris@redhat.com)
- Add consumer specific atom feed. (dgoodwin@redhat.com)
- Update Owner atom feed tests. (dgoodwin@redhat.com)
- Add the atom resource to the API generation (bkearney@redhat.com)
- Changing to cuke buildr tasks to be a little more discretionary about calling
  deploy. (jharris@redhat.com)
- When generating ent certs, skip creating an extention for any MKT products.  (alikins@redhat.com)
- Refactoring consumer types. (jharris@redhat.com)
- added createOwnerFeed to OwnerResource.java (anadathu@redhat.com)
- More consumer/owner atom feed tests. (dgoodwin@redhat.com)
- added createOwnerFeed to OwnerResource.java (anadathu@redhat.com)
- added createOwnerFeed to OwnerResource.java (anadathu@redhat.com)
- Add some tests for owner specific atom feeds. (dgoodwin@redhat.com)
- XML Serialization Fixes (bkearney@redhat.com)
- Make the string version of the cert and key be primary, byte secondary (bkearney@redhat.com)
- Adding deploy task to wrap the deploy script. (jharris@redhat.com)
- Turn upload_products back on. Wasn't supposed to be disabled in the first
  place. (alikins@redhat.com)
- Adding functional tests for product cert creation. (jharris@redhat.com)
- Shut up checkstyle (alikins@redhat.com)
- Update Rules and rules test cases to use consumer fact consumer uses.  (alikins@redhat.com)
- De-hash Content. Make the id be the content hash.  (alikins@redhat.com)
- add missing bit from sat-cert-nuke merge (jbowes@redhat.com)
- Drop Product.label. (dgoodwin@redhat.com)
- fixed a bug in default-rules.js - sockets and cpu.cpu_sockets were compared
  as strings, leading to 2 > 128. (ddolguik@redhat.com)
- Make the socket compare cast to int. It was doing a string cmp before.  (alikins@redhat.com)
- added entitlement quantity to the entitlement certificate (order namespace,
  oid 13) (ddolguik@redhat.com)
- Make Subscription -> Token relationship bi-directional. (dgoodwin@redhat.com)
- Cleanup more product hash spread. (dgoodwin@redhat.com)
- Remove a couple mentions of product hash. (dgoodwin@redhat.com)
- Remove Product.getAllChildProducts. (dgoodwin@redhat.com)
- Add a test for recursive Product.provides(). (dgoodwin@redhat.com)
- Merge fuzzy product matching approaches. (dgoodwin@redhat.com)
- Move ProductAdapter.provides() to Product class. (dgoodwin@redhat.com)
- Rename some methods for clarity. (dgoodwin@redhat.com)
- cuke: fix failing pool feature (jbowes@redhat.com)
- Drop the product hash column and use it as id (jbowes@redhat.com)
- Fix EntitlerTest (jbowes@redhat.com)
- Getting started ripping out sat cert stuff. (jharris@redhat.com)

* Wed Jun 02 2010 jesus m rodriguez <jmrodri@gmail.com> 0.0.16-1
- Adding exception class to wrap HTTP 202 * This exception is intended to be
  used for non error state messages. (calfonso@redhat.com)
- checkstyle fixes (jbowes@redhat.com)
- in default-rules.js changed the name of the consumer fact from sockets to
  cpu.cpu_sockets (ddolguik@redhat.com)
- Adding product cert unit tests and refactoring config system.  (jharris@redhat.com)
- added a test for consumption of an entitlement with a quantity greater than 1
  (ddolguik@redhat.com)
- Entitler now uses quantity parameter passed from bind calls (ddolguik@redhat.com)
- introduced quantity parameter in ConsumerResource#bind call (ddolguik@redhat.com)
- cuke: fix entitlement_certificates when run on its own (jbowes@redhat.com)
- cuke: get pool feature working (jbowes@redhat.com)
- cuke: get subscriptions feature passing (jbowes@redhat.com)
- Rename some methods for clarity. (dgoodwin@redhat.com)
- removed Principal from the parameters in ConsumerReource#bind call (and
  related curator methods) (ddolguik@redhat.com)
- audit: don't make a new json serializer for each event (jbowes@redhat.com)
- Adding unit tests for ProductCertificateCurator (jharris@redhat.com)
- get unit tests passing (jbowes@redhat.com)
- checkstyle fixups (jbowes@redhat.com)
- Fixing the product id and hash lookups on bind by product (calfonso@redhat.com)
- Iterating across all product for Engineering product hashes (calfonso@redhat.com)
- Implementing bind by product.  Doing some general refactoring to clean up the
  use of product id versus product hashes.  Product id's should only refer to
  SKUs.  Product hashes are the Engineering product hashes. Engineering product
  hashes can map to many SKUs. (calfonso@redhat.com)
- adding a subscription production wrapper for calls to the subscription
  product. the products at entitlement binding time can only be fully realized
  by looking at the subscription object instead of querying the product
  directly. (jomara@redhat.com)
- Checkstyle fixes. (alikins@redhat.com)
- Remove psql import call from deploy, it's not needed anymore (alikins@redhat.com)
- Change CN name to use product.getLabel() intead of product.getName() (alikins@redhat.com)
- refactoring various service adapter code for clarity, performance, and
  logical soundness (jomara@redhat.com)
- Changing the refresh logic to account for map entries that need to be removed (mhicks@redhat.com)
- Getting a first cut of product cert generation. (jharris@redhat.com)
- rules: check for existance of consumer facts before reading them
  (jbowes@redhat.com)
- audit: get principal from provider for ownerDeleted (jbowes@redhat.com)
- Change content cuke tests to use new add_content_to_product by label (alikins@redhat.com)
- Product -> Content model needs to be ManyToMany (alikins@redhat.com)
- Change the REST api for associating content with a product to work by
  content label. (alikins@redhat.com)

* Thu May 27 2010 jesus m. rodriguez <jesusr@redhat.com> 0.0.15-1
- removed Principal parameter from EventFactory methods (ddolguik@redhat.com)
- pool quantity change event emitted when quantity field changes (ddolguik@redhat.com)
- Bring back the EventResource. (dgoodwin@redhat.com)
- Call import_product.rb with path to import_products.json (alikins@redhat.com)
- added event to emit on pool quantity changes (ddolguik@redhat.com)
- This reverts commit 14eec6341636a6fc78e389bcc105c6795a1c5986. (bkearney@redhat.com)
- audit: don't log event id (it's null now anyways) (jbowes@redhat.com)
- Emit entitlement deletion events. (unbind) (dgoodwin@redhat.com)
- an event is emitted on pool creation (ddolguik@redhat.com)
- event is created now before the entity is deleted (during Delete Owner
  operation) (ddolguik@redhat.com)
- Turn default build level back to "INFO" so tests pass atm.  (alikins@redhat.com)
- add the new childId's arg to createProduct (alikins@redhat.com)
- Adding product creation to set childIds via query params.  (jharris@redhat.com)
- added event for deletion of an owner (ddolguik@redhat.com)
- added rules for 'architecture' and 'sockets' attributes (ddolguik@redhat.com)
- PoolCurator#listAvailableEntitlementPools will not return pools that
  triggered warnings during rules evaluation (ddolguik@redhat.com)
- Drop concept of unique event IDs prior entering the queue.  (dgoodwin@redhat.com)
- Dispatch entitlement event. (dgoodwin@redhat.com)
- audit: hook up a testing eventsink (jbowes@redhat.com)
- audit: store event type and target as strings in the db (jbowes@redhat.com)
- Revert accidental commit of hash drop (jbowes@redhat.com)
- Remove the product utils from deploy (bkearney@redhat.com)
- clean up checkstyle issues (alikins@redhat.com)
- audit: add owner created event (jbowes@redhat.com)
- audit: change LoggingListener to not show entity details (configurable) (jbowes@redhat.com)
- audit: add owner id to event (jbowes@redhat.com)
- audit: hook up consumer delete notification (jbowes@redhat.com)
- audit: split EventType enum into Type and Target (jbowes@redhat.com)
- shouldn't hardcode /etc/tomcat in JBOSS section (jesusr@redhat.com)
- fix formatting to appease checkstyle (jesusr@redhat.com)
- Adding in first cut at ruby cli for debugging/data loading.  (jharris@redhat.com)
- Add the ability for exceptions to set headers in the response. This allows me
  to use basic auth from the browser (bkearney@redhat.com)
- Add /atom feed for events. (dgoodwin@redhat.com)
- Mark Event new/old object fields XmlTransient. (dgoodwin@redhat.com)
- Expose Events over REST. (dgoodwin@redhat.com)
- Fix test NPE. (dgoodwin@redhat.com)
- Make Event JSON fields transient. (dgoodwin@redhat.com)
- Format Principal sub-class toString() methods. (dgoodwin@redhat.com)
- Add cuke test case for associated a Product and a Content (alikins@redhat.com)
- Add query param for enabling/disabling the content product relation (alikins@redhat.com)
- removed resource leak in LoggingResponseWrapper.java (ddolguik@redhat.com)
- fixed a resource leak in CandlepinPKIReader.java (ddolguik@redhat.com)
- fixed a resource leak in ConfigurationFileLoader (ddolguik@redhat.com)
- Adjust for change to Jackson json encoding format (alikins@redhat.com)
- Add method to associate a Content with a Product (alikins@redhat.com)
- A few slices of Content for the cuke tests (alikins@redhat.com)
- Add getContent to ContentResource (alikins@redhat.com)
- ConsumerResource.getProduct() is never used (and just returned null). Remove.
  (alikins@redhat.com)
- Add ContentResource (alikins@redhat.com)
- Check enabledContent contains the content before adding to entitlement cert
  (alikins@redhat.com)
- Remove "enabled" flag from Content model (alikins@redhat.com)
- Add some content test cases and a Content curator (alikins@redhat.com)
- attempt getting product->content uploading as a chunk (alikins@redhat.com)
- make /var/log/candlepin on deploy (jbowes@redhat.com)
- Store object state with @Lob so the db can choose (jbowes@redhat.com)
- audit: hook up jackson for object state serialization (jbowes@redhat.com)
- audit: remove old example listeners (jbowes@redhat.com)
- Add an audit listener to write events to audit.log (jbowes@redhat.com)
- Consumer creation events now being stored in database. (dgoodwin@redhat.com)
- First draft of DatabaseListener. (dgoodwin@redhat.com)
- oops, update candlepin listener to call the other listeners properly
  (jbowes@redhat.com)
- Don't pass ServletContextEvent to our listeners; they don't need it
  (jbowes@redhat.com)
- initialize pinsetter from the candlepin context, too (jbowes@redhat.com)
- audit: reuse a single injector for event listeners and resteasy
  (jbowes@redhat.com)
- Guice up the event listeners. (dgoodwin@redhat.com)
- Create events with unique IDs. (dgoodwin@redhat.com)
- Hookup consumer creation event to the REST API. (dgoodwin@redhat.com)
- checkstyle fixes (jbowes@redhat.com)
- audit: allow for configurable listeners (jbowes@redhat.com)
- hornetq: stop and close the queue reaper session (jbowes@redhat.com)
- hornetq: teach the persistance directory to be configurable
  (jbowes@redhat.com)
- hornetq: silence cluster warning on startup (jbowes@redhat.com)
- audit: split EventHub into Event[Source|Sink] (jbowes@redhat.com)
- hornetq: clean up old empty queues on startup (jbowes@redhat.com)
- Add EventFactory. (dgoodwin@redhat.com)
- Move event code into the audit package, and use the existing event class
  (jbowes@redhat.com)
- hornetq: checkstyle cleanup (jbowes@redhat.com)
- hornetq: use a seperate queue for each event listener (jbowes@redhat.com)
- Get an embedded HornetQ server running (jbowes@redhat.com)
- json: use jaxb bindings as well (but prefer jackson) (jbowes@redhat.com)
- json: use iso8601 datetimes (jbowes@redhat.com)
- guice: cleanup unused jackson config bits (jbowes@redhat.com)
- apicrawler: print schema of some json (jbowes@redhat.com)
- Switch to jackson for json (jbowes@redhat.com)

* Mon May 24 2010 jesus m. rodriguez <jesusr@redhat.com> 0.0.14-1
- remove unused import (jesusr@redhat.com)
- add @return tag, fix typo (jesusr@redhat.com)
- Add product_hash interfaces (morazi@redhat.com)
- Adding ruby client lib to be used by cuke tests. (jharris@redhat.com)
- Ruby lib move. (jharris@redhat.com)
- Putting explicit update timestamp in product test too. (jharris@redhat.com)
- Moving xml annotation to allow for setter methods. (jharris@redhat.com)
- Checkstyle for zeus. (jharris@redhat.com)
- Changing pool update timestamp test to explicitly set the initial update
  time. (jharris@redhat.com)
- cuke: Minor test cleanup. (dgoodwin@redhat.com)
- added role checks on RulesResource and RuleCurator (ddolguik@redhat.com)
- checkstyle fixups (jbowes@redhat.com)
- add JACKSON to genschema dep (jesusr@redhat.com)
- Add a few more test cases to cover productCurator.update and the childContent
  blkUpdate methods (alikins@redhat.com)
- added ability to pass attributes during product creation. by default,
  atributte with the name of the product is created (ddolguik@redhat.com)
- no longer relying on a concrete implementation of List interface in
  select_pool_global() (ddolguik@redhat.com)
- added test for pool selection when multiple rules match (ddolguik@redhat.com)
- best pool selection now relies on product attributes instead of product
  names. (ddolguik@redhat.com)
- changed entitlement rules to use product attributes instead of product names
  (ddolguik@redhat.com)
- Removing StatusResourceTest. (dgoodwin@redhat.com)
- First draft of Event model. (dgoodwin@redhat.com)
- corrected checkstyle errors. (anadathu@redhat.com)
- refractored base classes to extend AbstractHibernateObject.java
  (anadathu@redhat.com)
- refractored model objects to include AbstractHibernateObject as base class.
  (anadathu@redhat.com)
- Adding a system principle for super admin rights for system calls
  (calfonso@redhat.com)
- Added an owner lookup / creation during authentication (calfonso@redhat.com)
- Change product parent/child relationship to many-to-many (jbowes@redhat.com)
- Add test for bind by token when pool already exists. (dgoodwin@redhat.com)
- Fixing owner param for subscription api call. (jharris@redhat.com)
- Adding an owner to the api for getting subscriptions for a token
  (calfonso@redhat.com)
- Removing consumer check from the Principal. (jharris@redhat.com)
- cuke: Fix failures from merge. (dgoodwin@redhat.com)
- Create pools during bind by token if a new subscription is returned.
  (dgoodwin@redhat.com)
- cuke: Add tests for token sub to pool changes. (dgoodwin@redhat.com)
- Cleanup tokens when deleting an owner. (dgoodwin@redhat.com)
- Drop Subscription attributes foreign key. (dgoodwin@redhat.com)
- cuke: Drop unused Satellite cert steps. (dgoodwin@redhat.com)
- cuke: Add more assumptions on use of the test owner. (dgoodwin@redhat.com)
- fix checkstyle (jbowes@redhat.com)
- add a listall boolean param to GET /pools (jbowes@redhat.com)
- cuke: add basic test for listing all pools as a consumer (jbowes@redhat.com)
- fixed failing ConsumerResource tests (ddolguik@redhat.com)
- added access control tests for EntitlementCurator (ddolguik@redhat.com)
- added access control tests for PoolCurator#listAvailableEntitlementPools
  (ddolguik@redhat.com)
- cuke: fix up entitlement access control (jbowes@redhat.com)
- Adding in product definitions for entitlement and unregister functional
  tests. (jharris@redhat.com)
- rename shouldGrantAcessTo -> shouldGrantAccessTo (jesusr@redhat.com)
- removed an unused variable (ddolguik@redhat.com)
- access control inteceptor now verifies if find() return a null before
  attempting validation (ddolguik@redhat.com)
- access control interceptor now verifies that the object returned by find()
  implements AccessControlEnforced interface. (ddolguik@redhat.com)
- enabled access control interceptor in all tests in
  ConsumerCuratorAccessControlTest (ddolguik@redhat.com)
- added access control around AbstractHibernateCurator#find
  (ddolguik@redhat.com)
- added lower-level tests to verify access control for Entitlements for list
  operations (ddolguik@redhat.com)
- checkstyle fixes (alikins@redhat.com)
- apicrawler: print out return type (jbowes@redhat.com)
- apicrawler: print out query param type (jbowes@redhat.com)
- apicrawler: eliminate duplicate /'s (jbowes@redhat.com)
- cuke: Rename current owner var to match current consumer.
  (dgoodwin@redhat.com)
- added access control for entitlements (ddolguik@redhat.com)
- Getting the unbind feature back in shape. (jharris@redhat.com)
- added access control around CosumerResource#getEntitlementCertificates() & a
  bunch of @Transactional & @AccessControlSecured annotations on methods in
  ConsumerCurator and EntitlementCertificateCurator (ddolguik@redhat.com)

* Tue May 11 2010 jesus m. rodriguez <jesusr@redhat.com> 0.0.13-1
- fix jboss deployment (jesusr@redhat.com)
- checkstyle fix for OwnerResource (jbowes@redhat.com)
- another small fix in AccessControlInterceptor (ddolguik@redhat.com)
- cuke: get subscription_tokens feature passing (jbowes@redhat.com)
- EntitlementCertificate.java now has a filter for Owner role (ddolguik@redhat.com)
- fix to make AccessControlInterceptor to work with NoAuthPrincipal (ddolguik@redhat.com)
- fixes to get cucumber tests to pass (ddolguik@redhat.com)
- added crud access control for EntitlementCertificate (ddolguik@redhat.com)
- fix checkstyle (jbowes@redhat.com)
- fix failing ProductResourceTest (jbowes@redhat.com)
- Adding unit tests for default user service. (jharris@redhat.com)
- Renaming config user service test to be right. (jharris@redhat.com)
- Add the test cases for product creation as well (alikins@redhat.com)
- Add a POST /product resource for creating objects (alikins@redhat.com)
- added access control filters for Consumer (ddolguik@redhat.com)
- remove unused imports (jesusr@redhat.com)

* Mon May 10 2010 jesus m. rodriguez <jesusr@redhat.com> 0.0.12-1
- Add ContractNumber to the ent cert if it exists for the subscription.  (alikins@redhat.com)
- renamed AbstractHibernateCurator#findAll to listAll (ddolguik@redhat.com)
- cuke: get virtualization passing (jbowes@redhat.com)
- Moving the OIDUtil into the org.fedoraproject.candlepin.util (calfonso@redhat.com)
- small cleanups in AccessControlInterceptor (ddolguik@redhat.com)
- renamed CRUDInterceptor to AccessControlInterceptor (ddolguik@redhat.com)
- removed FilterInterceptor (ddolguik@redhat.com)
- merged FilterInterceptor into CRUDInterceptor (ddolguik@redhat.com)
- Delete CertificateResourceTest (jbowes@redhat.com)
- add some more info to deploy about cp_product_utils (alikins@redhat.com)
- Turn running unit tests back on by default (alikins@redhat.com)
- fixed broken ConsumerResourceTest (ddolguik@redhat.com)
- cukes: fixing subscription feature (jharris@redhat.com)
- change pools test to check for inclusion, not just first entry (alikins@redhat.com)
- cuke: get unbind passing (jbowes@redhat.com)
- More "unmerging" getting thigns working again (alikins@redhat.com)
- rename all the product names back to human names.  (alikins@redhat.com)
- one more using product name directly (alikins@redhat.com)
- ReadOnlyConsumer.hasEntitlements was checking product label against product
  id. Change to check product id. (alikins@redhat.com)
- Change entitlement_certificates to use product hash (alikins@redhat.com)
- Change entitlement tests to use product has/id's instead of string labels.  (alikins@redhat.com)
- import prodcut data as the candlepin user (jbowes@redhat.com)
- Make pools.features work (walk over list returned of pools, looking for the
  name we are checking for instead of assuming first one is a specific name)
  (alikins@redhat.com)
- Change to use product.id hashes (alikins@redhat.com)
- Change to using hash id's. Ugly, but we'll fix it soon.  (alikins@redhat.com)
- first pass at updating cucumber tests for new product names.  (alikins@redhat.com)
- Just a simple wrapper script to rebuild/reinstall/retest from scratch (alikins@redhat.com)
- Example of a rule that checks for virt entitlement by product.id (alikins@redhat.com)
- URI.escape all urls (alikins@redhat.com)
- use the default user/pass we create in the deploy (alikins@redhat.com)
- stop importing sat cert, instead import product data from product certs.  (alikins@redhat.com)
- Fix a NPE when a subscription.product_id points to a non existing product (alikins@redhat.com)
- make deploy import product certs to populate user data (alikins@redhat.com) 
* Fri May 07 2010 jesus m. rodriguez <jesusr@redhat.com> 0.0.9-1
- pulled in fixes
- unit tests still fail

* Fri May 07 2010 jesus m. rodriguez <jesusr@redhat.com> 0.0.7-1
- added role-based access control on OwnerCurator#create and
  ConsumerCurator#create (ddolguik@redhat.com)
- cuke: Getting authentication feature green (jharris@redhat.com)
- cuke: entitlement.feature working - had to move around subscription creation
  uri (jharris@redhat.com)
- cuke: store consumer uuid on api object, if available. (jbowes@redhat.com)
- removed empty lines at the end of the file (ddolguik@redhat.com)
- cuke: get status feature passing (jbowes@redhat.com)
- Getting started with subscription creation steps. (jharris@redhat.com)
- cuke: work on getting unregister working; add @consumer_cp
  (jbowes@redhat.com)
- Removing the oracle datasource because it's not maintained.
  (calfonso@redhat.com)
- adding contract number to subscription (jomara@redhat.com)
- cuke: register.feature now passing. (dgoodwin@redhat.com)
- cuke: Introduce auth_steps.rb. (dgoodwin@redhat.com)
- Cuke: Fix test for checking UUID on identity cert. (dgoodwin@redhat.com)
- added crud access control to Pools (ddolguik@redhat.com)
- Cuke: Stop re-initlializing Candlepin connections on register.
  (dgoodwin@redhat.com)
- Get register.feature working again (jbowes@redhat.com)
- Remove unused step (jbowes@redhat.com)
- Add a shortcut syntax for running a specific feature (jbowes@redhat.com)
- Fix failing unit test (jbowes@redhat.com)
- Get one scenario passing for the register feature (jbowes@redhat.com)
- Beginnings of fixes for register.feature. (dgoodwin@redhat.com)
- Adding a subscription terms check when doing a bind, default implementation
  always passes back an answer of false (calfonso@redhat.com)
- First pass at implementing the default curator-backed user service.
  (jharris@redhat.com)
- Allow Ruby Candlepin API to be initialized with credentials or certs.
  (dgoodwin@redhat.com)
- Add a new role to indicate which paths can use the NoAuth principal
  (jbowes@redhat.com)
- Create a test owner and delete during cucumber teardown.
  (dgoodwin@redhat.com)
- Change default candlepin admin credentials. (dgoodwin@redhat.com)
- Add a cucumber test configuration file. (dgoodwin@redhat.com)
- Move a semi-bad TestUtil method to where it's needed only.
  (dgoodwin@redhat.com)
- Remove dead unbind by serials path. (dgoodwin@redhat.com)
- Add the API crawler. (dgoodwin@redhat.com)
- Resurrect owner resource test. (dgoodwin@redhat.com)
- added access control enforcement to PoolCurator#listByOwner
  (ddolguik@redhat.com)
- Comment out failing test for merge. (dgoodwin@redhat.com)
- Disable rule upload cucumber tests. (dgoodwin@redhat.com)
- Test fixes. (dgoodwin@redhat.com)
- Test fixes. (dgoodwin@redhat.com)
- Implement DELETE /owners/id. (dgoodwin@redhat.com)
- access control is enforced on PoolCurator#listAvailableEntitlementPools for
  Consumer role now (ddolguik@redhat.com)
- a couple of refactorings in FilterInterceptor (ddolguik@redhat.com)
- access control is enforced on PoolCurator#listAvailableEntitlementPools now
  (ddolguik@redhat.com)
- added hibernate filter limiting the pools for the specified owner
  (ddolguik@redhat.com)
- Replace refreshPools with a REST call to trigger the refresh
  (jbowes@redhat.com)
- Cleanup refs to test principal singleton. (dgoodwin@redhat.com)
- Add PoolResource security tests. (dgoodwin@redhat.com)
- Add OwnerResource security tests. (dgoodwin@redhat.com)
- Cleanup creation of test principals. (dgoodwin@redhat.com)
- added granular access control check on delete operations on Consumers
  (ddolguik@redhat.com)
- added a test to verify that consumer can access its own entitlments
  (ddolguik@redhat.com)
- Remove past attempt at consumer access enforcement. (dgoodwin@redhat.com)
- pulled role enforcement and granular access control separately.
  (ddolguik@redhat.com)
- first cut at access control for list() methods via dynamic hibernate filters
  (ddolguik@redhat.com)
- removing logging from product model (calfonso@redhat.com)
- move old python test client code to client/python (jbowes@redhat.com)
- Add an owner/consumer security test. (dgoodwin@redhat.com)
- Enable security on all Resource tests. (dgoodwin@redhat.com)
- Add security to ConsumerResource and update tests. (dgoodwin@redhat.com)
- Add AllowRoles annotation, implement in PoolResource. (dgoodwin@redhat.com)
- fix failing test from last commit. oops. (jbowes@redhat.com)
- Add more logging for auth (jbowes@redhat.com)
- Rename ConsumerEnforcer to SecurityInterceptor. (dgoodwin@redhat.com)
- Fixed for Products having recursize sub products (alikins@redhat.com)
- Namespace the gettext tasks, and glob for po file (jbowes@redhat.com)
- Switch to generating gettext message bundles at compile time
  (jbowes@redhat.com)
- Fix PoolResource security testing. (dgoodwin@redhat.com)
- Move StatusResourceTest to Cucumber. (dgoodwin@redhat.com)
- Support enforcing consumer QueryParam matching. (dgoodwin@redhat.com)
- Add a utf-8 translation for client testing (jbowes@redhat.com)
- 586563 - Add more certificate related options to the deploy script
  (jbowes@redhat.com)
- Remove unneeded Product add, clean up comments (alikins@redhat.com)
- Add support for following Product hiearchy so we can do sub products.
  (alikins@redhat.com)
- in progress of entCertAdapter changing to using products and sub products
  (alikins@redhat.com)
- Add type to Content and Product model, update ent cert generation to use it
  (alikins@redhat.com)
- Test changes for Principal dependency injection. (dgoodwin@redhat.com)

* Thu Apr 29 2010 jesus m. rodriguez <jesusr@redhat.com> 0.0.6-1
- 582223 - don't allow the same product to be consumed more than once (jbowes@redhat.com)
- More serial BigInteger/Long fixes. (dgoodwin@redhat.com)
- Revert to using BigInteger for certificate serials. (dgoodwin@redhat.com)
- Allow roles to be specified in candlepin.conf. (dgoodwin@redhat.com)
- Move the client bits into their own directory (bkearney@redhat.com)
- getEntitlementCertificateSerials was returning a entCert's id instead of it's
  serial number (alikins@redhat.com)
- added tests around content set extensions in entitlement certificate.
  (ddolguik@redhat.com)
- Adding a catch clause for CandlepinExceptions, which have localized messages
  (calfonso@redhat.com)
- Remove stray print from cucumber tests (jbowes@redhat.com)
- Move exceptions to their own package, and add them to the api
  (jbowes@redhat.com)
- First cut of writing out the entitlement certificates (bkearney@redhat.com)
- Add additional setters to the certificate class so that resteasy will de-
  serialize it correctly (bkearney@redhat.com)
- added create_pool and create_product methods. added PoolResource#createPool()
  (ddolguik@redhat.com)
- buildfile with a different set of versions, for building against jpackage
  repos. (ayoung@redhat.com)
- No longer ignores versions. Minor cleanup (ayoung@redhat.com)
- Generate unique cert serials from a db sequence. (dgoodwin@redhat.com)
- added create_product method (ddolguik@redhat.com)
- Adding crud methods for owners and consumer_types to ruby lib.  (jharris@redhat.com)
- i18nize AuthInterceptor (jbowes@redhat.com)
- Rename SSLAuthFilterTest -> SSLAuthTest (jbowes@redhat.com)
- Basic list commands work from the command line (bkearney@redhat.com)
- Adding in the product name for poolCurator .find() and .findAll() (jharris@redhat.com)
- Initial client code (bkearney@redhat.com)
- 579913 - clearing out old manager files, adding productname to pool (jharris@redhat.com)
- Replace servlet filter authentication with resteasy interceptors (jbowes@redhat.com)
- Switch from constructor injection to method injection for principal (jbowes@redhat.com)
- Add fuzzy product matching for subscriptions/pools. (dgoodwin@redhat.com)
- Fix up the failing loggingfiltertests (jbowes@redhat.com)
- implmented ConsumerResource#unbindBySerial() (ddolguik@redhat.com)
- Add status output to the logging filter (bkearney@redhat.com)
- Script to generate a sql script to import a product cert as a Product (alikins@redhat.com)
- Add a "type" to the content model (aka, 'yum', 'file').  (alikins@redhat.com)
- Silence ruby warning about argument parenthesis (jbowes@redhat.com)
- chop lambda usage out of cucumber tests (jbowes@redhat.com)
- added retrieval of entitlements by certificate serial number. (ddolguik@redhat.com)
- Fix an NPE with 404s and no media types. (bkearney@redhat.com)
- Add sub/product adapter methods for matching product provides. (dgoodwin@redhat.com)
- move over to use OIDUtil for contept->number mapping instead of hardcoding it (alikins@redhat.com)
- Do not import the sat cert thats full of bogus product info (alikins@redhat.com)
- Need a id seq here (alikins@redhat.com)
- add hash to product and content models (alikins@redhat.com)
- Fix up product/content model mappings. (alikins@redhat.com)
- more roughing out the content info (alikins@redhat.com)
- Add getter/setters for arch/variant/version (alikins@redhat.com)
- Start adding stuff to the product model for the oid product model
- Bundle OIDUtil for now (alikins@redhat.com)
- Change deploy and gen-cert scripts to use the hostname of the machine instead
  of localhost (alikins@redhat.com)

* Fri Apr 23 2010 jesus m. rodriguez <jesusr@redhat.com> 0.0.4-1
- logging cleanup (jbowes@redhat.com)
- Remove untranslated de strings (jbowes@redhat.com)
- lot's of code cleanup, remove dead code, add unit tests (jesusr@redhat.com)
- Protecting all consumer-related methods with @EnforceConsumer (jharris@redhat.com)
- don't allow a duplicate satellite cert to be uploaded (jbowes@redhat.com)
- Search for version specific jar.  If that is mising, go for version agnostic. (ayoung@redhat.com)
- adding a localbuild.rb script, to allow us to fetch only from a local maven repo. (ayoung@redhat.com)
- move localized_resources to po, which is the more standard dir name (jbowes@redhat.com)
- Allow all cucumber tasks to run with specified features (jbowes@redhat.com)
- fix ALL emma reports (jesusr@redhat.com)
- change satellite certificate test to look for 'at least' x products (for multiple runs) (jbowes@redhat.com)
- Convert entitlement certificate tests to cucumber (jbowes@redhat.com)
- remove :fixeclipse hack. Extended the eclipse task instead.  (jesusr@redhat.com)
- fix the emma reports without requiring seperate task. (jesusr@redhat.com)
- Convert subscription token tests to cucumber (jbowes@redhat.com)
- Fix some tests. (dgoodwin@redhat.com)
- Associate uploaded subscription certificates with owners.  (dgoodwin@redhat.com)
- Add setters for the getXXAsString methods so that deserialization works (bkearney@redhat.com)
- Log out the response which is sent to the client (bkearney@redhat.com)
- fixed two broken tests (ddolguik@redhat.com)
- localized jsenforcer error messages (ddolguik@redhat.com)
- updated exceptions thrown from Resources to be serializable to JSON/XML; use
  localized messages (ddolguik@redhat.com)
- remove old java functional tests, use cucumber (jesusr@redhat.com)
- Convert subscription tests to cucumber (jbowes@redhat.com)
- fix whitespace around = (jesusr@redhat.com)
- Rename Certificate to SubscriptionCertificate. (dgoodwin@redhat.com)
- checkstyle needs to depend on compile to get access to Exception info (jesusr@redhat.com)
- fix checkstyle static final NOT final static (jesusr@redhat.com)
- Adding consumer interceptor for validating consumer resources (jharris@redhat.com)
- Remove duplicate nosetest (jbowes@redhat.com)
- allow register by uuid cucumber tests to run without cleaning the db first (jbowes@redhat.com)
- Drop the static encoded cert in CertificateResource. (dgoodwin@redhat.com)
- Convert nosetests to cucumber tests (jbowes@redhat.com)

* Thu Apr 15 2010 jesus m. rodriguez <jesusr@redhat.com> 0.0.3-1
- Change the default SSLProtocol we deploy server.xml with to "all" instead of
  "TLS" (alikins@redhat.com)
- Identity certs should be deleted by their id, not by consumer id (jbowes@redhat.com)
- added support for localization of user-visible messages (ddolguik@redhat.com)
- teach the deploy script to get war version from the spec file (jbowes@redhat.com)
- support for parsing of accept-language headers (ddolguik@redhat.com)

* Wed Apr 14 2010 jesus m. rodriguez <jesusr@redhat.com> 0.0.2-1
- Add support for log4j settings in the candlepin.conf file (bkearney@redhat.com)
- remove BuildRequires altogether (jesusr@redhat.com)
- A bit more easy emma love (bkearney@redhat.com)
- Remove the test resource (jbowes@redhat.com)
- remove changelog, bump down version to 0.0.0 (jesusr@redhat.com)
- Fixing up checkstyle  and cucumber test.  (jharris@redhat.com)
