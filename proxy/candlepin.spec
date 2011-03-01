%global _binary_filedigest_algorithm 1
%global _source_filedigest_algorithm 1
%global _binary_payload w9.gzdio
%global _source_payload w9.gzdio

Name: candlepin
Summary: Candlepin is an open source entitlement management system.
Group: Internet/Applications
License: GLPv2
Version: 0.2.9
Release: 1%{?dist}
URL: http://fedorahosted.org/candlepin
# Source0: https://fedorahosted.org/releases/c/a/candlepin/%{name}-%{version}.tar.gz
Source: %{name}-%{version}.tar.gz
BuildRoot: %{_tmppath}/%{name}-%{version}-%{release}-buildroot
Vendor: Red Hat, Inc
BuildArch: noarch

Requires: candlepin-webapp
BuildRequires: java >= 0:1.6.0
BuildRequires: ant >= 0:1.7.0
BuildRequires: gettext
BuildRequires: candlepin-deps >= 0:0.0.13
%define __jar_repack %{nil}

%description
Candlepin is an open source entitlement management system.

%package tomcat5
Summary: Candlepin web application for tomcat5
Requires: tomcat5 >= 5.5
Provides: candlepin-webapp
Group: Internet/Applications

%description tomcat5
Candlepin web application for tomcat5

%package tomcat6
Summary: Candlepin web application for tomcat6
Requires: tomcat6
Provides: candlepin-webapp
Group: Internet/Applications

%description tomcat6
Candlepin web application for tomcat6

%package jboss
Summary: Candlepin web application for jboss
Requires: jbossas >= 4.3
Provides: candlepin-webapp
Group: Internet/Applications

%description jboss
Candlepin web application for jboss

%package devel
Summary: Development libraries for candlepin integration
Group: Development/Libraries

%description devel
Development libraries for candlepin integration

%prep
%setup -q 

%build
ant -Dlibdir=/usr/share/candlepin/lib/ clean package

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

# devel
install -d -m 755 $RPM_BUILD_ROOT/%{_datadir}/%{name}/lib/
install -m 644 target/%{name}-api-%{version}.jar $RPM_BUILD_ROOT/%{_datadir}/%{name}/lib/

# /var/lib dir for hornetq state
install -d -m 755 $RPM_BUILD_ROOT/%{_localstatedir}/lib/%{name}

install -d -m 755 $RPM_BUILD_ROOT/%{_localstatedir}/log/%{name}
install -d -m 755 $RPM_BUILD_ROOT/%{_localstatedir}/cache/%{name}

%clean
rm -rf $RPM_BUILD_ROOT

%files
%config(noreplace) %{_sysconfdir}/%{name}/%{name}.conf

%files jboss
%defattr(-,jboss,jboss,-)
%{_localstatedir}/lib/jbossas/server/production/deploy/%{name}*
%{_localstatedir}/lib/%{name}
%{_localstatedir}/log/%{name}
%{_localstatedir}/cache/%{name}

%files tomcat5
%defattr(644,tomcat,tomcat,775)
%{_localstatedir}/lib/tomcat5/webapps/%{name}*
%{_localstatedir}/lib/%{name}
%{_localstatedir}/log/%{name}
%{_localstatedir}/cache/%{name}

%files tomcat6
%defattr(644,tomcat,tomcat,775)
%{_localstatedir}/lib/tomcat6/webapps/%{name}*
%{_localstatedir}/lib/%{name}
%{_localstatedir}/log/%{name}
%{_localstatedir}/cache/%{name}

%files devel
%defattr(644,root,root,775)
%{_datadir}/%{name}/lib/%{name}-api-%{version}.jar

%changelog
* Tue Mar 01 2011 jesus m. rodriguez <jesusr@redhat.com> 0.2.9-1
- Fix OwnerInfo to account for entitlement quantity (jbowes@redhat.com)

* Tue Mar 01 2011 jesus m. rodriguez <jesusr@redhat.com> 0.2.8-1
- Make consumer entitlement loading lazy, and hide from json (jbowes@redhat.com)
- Add an OwnerInfo object (jbowes@redhat.com)
- Adding owner delete flag to not update the CRL. (jharris@redhat.com)
- allow characters for consumer name that user service allows (sans leading \#) (cduryee@redhat.com)
- Merge branch 'perf' (bkearney@redhat.com)
- Porting unbind to rspec. (jharris@redhat.com)
- Make the collection of entitlements on the pool lazy (bkearney@redhat.com)
- Remove the unnecessary join tables (bkearney@redhat.com)
- Don't serialize the cert/key as bytes stuff (jbowes@redhat.com)
- Calculate the quantity based on a formula as opposed to iterating over the
  collection of entitlements. (bkearney@redhat.com)
- workaround old javac bug wrt generics (jbowes@redhat.com)
- Add a contentPrefix to an owner, and use that in generation of the content
  certificates. (bkearney@redhat.com)
- Add concurrency test script. (dgoodwin@redhat.com)
- Convert subscription_token from cuke to spec (jbowes@redhat.com)
- Delete subscriptions cuke test (already in spec) (jbowes@redhat.com)
- Revert "Use a function to calculate the Pool quantity on the fetch." (bkearney@redhat.com)
- sefler likes small numbers of entitlements (bkearney@redhat.com)
- fix logdriver changes which caused eclipse task to add '.' to src path. (jesusr@redhat.com)
- Checkstyle. (jharris@redhat.com)
- Generating API json. (jharris@redhat.com)
- narrow cipher list to only include FIPS compliant ciphers (cduryee@redhat.com)
- Mis spelled OAuth (bkearney@redhat.com)

* Thu Feb 24 2011 jesus m. rodriguez <jesusr@redhat.com> 0.2.7-1
- bump candlepin-deps to 0.0.13 to include rhino (jesusr@redhat.com)
- Improve the error messages returned for OAuth errors (bkearney@redhat.com)
- Use a function to calculate the Pool quantity on the fetch. (bkearney@redhat.com)
- More entitlementz (bkearney@redhat.com)
- Checkstyle cleanup (jbowes@redhat.com)
- Remove unused RulesReaderProvider (jbowes@redhat.com)
- Recompile the rules when a new version is detected (jbowes@redhat.com)
- Pull out the quantity to an easily changeable constant (bkearney@redhat.com)
- Couple more indexes (bkearney@redhat.com)
- precompile and share our javascript rules across threads (jbowes@redhat.com)
- Use rhino instead of the java scripting api (jbowes@redhat.com)
- checkstyle: static final instead of final static (jesusr@redhat.com)
- Making apicrawler spit out a json file. (jharris@redhat.com)
- I18N update. (dgoodwin@redhat.com)
- Add add-on product to test data. (dgoodwin@redhat.com)
- Remove unneeded import (jbowes@redhat.com)
- Not exporting virt_only entitlements or entitlement certs.  (jharris@redhat.com)
- 676870: Fix entitling modifier and modifiee in the same call (jbowes@redhat.com)
- Make domain consumer spec use more randomness (jbowes@redhat.com)
- Add a couple of indexes based on what I think are common usge patterns (bkearney@redhat.com)
- Porting multiplier cuke features to rspec. (jharris@redhat.com)
- tech debt: checkstyle whitespace fix (jesusr@redhat.com)
- Small refactoring to use File.read (jharris@redhat.com)
- Porting domain consumer cuke test to rspec. (jharris@redhat.com)
- tech debt: adding unit tests (jmrodri@gmail.com)
- need full url to logdriver when using download. (jesusr@redhat.com)
- 677405: change error message to be more generic. (jesusr@redhat.com)

* Tue Feb 15 2011 jesus m. rodriguez <jesusr@redhat.com> 0.2.6-1
- Fix buildr deprecation warning. (dgoodwin@redhat.com)
- Small ApiCrawler output cleanup. (dgoodwin@redhat.com)
- Add missing resource class. (dgoodwin@redhat.com)
- Run API Crawler with buildr. (dgoodwin@redhat.com)
- autobind: prefer virt_only pools over regular (jbowes@redhat.com)
- Fix up the same data to show a bit better (bkearney@redhat.com)
- Adding test data for virt_limit and using parent subscription Id for virt
  pools. (jharris@redhat.com)
- remove some stray puts in our ruby (jbowes@redhat.com)
- 672233: allow &,?,(),{},[] (jesusr@redhat.com)
- Pretty big javascript overhaul. (jharris@redhat.com)
- 672233: allow @ symbol as a consumer name. (jesusr@redhat.com)
- Checkstyle cleanup (bkearney@redhat.com)
- Add a check for the NPE which is being seen by the rhsm-web folks
  (bkearney@redhat.com)
- 658683 - additional info would help person consumer to unsubscribe when
  blocked with "Cannot unbind due to outstanding sub-pool entitlements in ..."
  (cduryee@redhat.com)
- Stop showing SQL by default. (dgoodwin@redhat.com)
- Fix debug logging enabled by default. (dgoodwin@redhat.com)
- xnap is very inefficient if you do not have the locale. Added static caching
  to our provider to overcome this. (bkearney@redhat.com)
- Hard code the i18n basename to save some lookups. Still too much time in the
  i18n creation (bkearney@redhat.com)

* Fri Feb 11 2011 jesus m. rodriguez <jesusr@redhat.com> 0.2.5-1
- add a API method for regenerating entitlement certs by entitlement id
  (alikins@redhat.com)
- 671195: Use pessimistic locking on pools during bind. (dgoodwin@redhat.com)
- Ensure that the serializers are cached to save on the Bean scanning
  (bkearney@redhat.com)
- Teach our rspec target to output dots (jbowes@redhat.com)

* Thu Feb 10 2011 jesus m. rodriguez <jesusr@redhat.com> 0.2.4-1
- Update spec file to generate candlepin-devel package (jeckersb@redhat.com)

* Wed Feb 09 2011 jesus m. rodriguez <jesusr@redhat.com> 0.2.3-1
- fix highlander unit test (jbowes@redhat.com)
- 660516: override unitOfWork for pinsetter to bypass caching (jbowes@redhat.com)
- add ability to use logdriver during development. (jesusr@redhat.com)
- Modify candlpin so that the rules are not parsed or compiled unless they are
  used. (bkearney@redhat.com)
- Turn off the list owners until determine if it can be called
  (bkearney@redhat.com)
- 675473: i18n of the strings requires double quotes.. not single quotes.
  (bkearney@redhat.com)
- Allow for owner adminda to access getOwner and getOwners
  (bkearney@redhat.com)
- remove entitlement.isFree. (jesusr@redhat.com)
- 671195: remove consumed attr to avoid concurrency issue. (jesusr@redhat.com)
- 674078: Take a full ISO 8601 timestamp for pool search activeOn
  (jbowes@redhat.com)

* Thu Feb 03 2011 jesus m. rodriguez <jesusr@redhat.com> 0.2.2-1
- Building master (jesusr@redhat.com)
- Add a small metadata expiry import test. (dgoodwin@redhat.com)
- Add metadata expire to entitlement certificates. (dgoodwin@redhat.com)
- Add metadata expire seconds to the Content model. (dgoodwin@redhat.com)
- 672233: Fixing typo in consumer curator length check and removing from
  resource. (jharris@redhat.com)
- Add users to the trust auth logic. They can now be passed in with cp-user
  (bkearney@redhat.com)
- remove unused import (jesusr@redhat.com)
- Expand on modifier test spec. (dgoodwin@redhat.com)
- 658683: change the error message for failed unbinds (bkearney@redhat.com)
- 672438: Fix copying of provided products to a derived sub-pool.
  (dgoodwin@redhat.com)
- 672233: Limiting consumer names to alphanumeric, dot, dash and underscore
  (jharris@redhat.com)

* Wed Jan 26 2011 jesus m. rodriguez <jesusr@redhat.com> 0.2.1-1
- I18N update. (dgoodwin@redhat.com)
- Add support_* attributes to test data (jbowes@redhat.com)
- 664847: Enforce architecture when doing autobind (bkearney@redhat.com)
- Add modifier products to test data. (dgoodwin@redhat.com)
- Remove hardcoded product IDs from rspec. (dgoodwin@redhat.com)
- Fix some test failures. (dgoodwin@redhat.com)
- Refactor import products script and add test data. (dgoodwin@redhat.com)
- Adding modified scenarios to specs and refactoring a little. (jharris@redhat.com)
- One more checkstyle (bkearney@redhat.com)
- checkstyle issues with 92 characters (bkearney@redhat.com)
- 670344: Names of pools were defaulting to the parent product id (bkearney@redhat.com)
- Fix rspec fixture issue. (dgoodwin@redhat.com)
- Regenerate modifier certs on bind to the modified product. (dgoodwin@redhat.com)
- Add rspec tests for generation of modifier certs. (dgoodwin@redhat.com)
- Using provided products from pool for listing modifying products. (jharris@redhat.com)
- remove trailing slash on PUT /consumers/{consumer_uuid}/certificates/ (jesusr@redhat.com)
- Regenerating modifying entitlement certs on unbind. (jharris@redhat.com)
- Add modifier rspec test. (dgoodwin@redhat.com)
- Fix NPE when binding by products and no pools provide them. (dgoodwin@redhat.com)
- fix log statement to be useful. (jesusr@redhat.com)
- Fixing subscription unit tests. (jharris@redhat.com)
- Using the correct exception for activation and adding i18n. (jharris@redhat.com)
- add support level and support type to the cert (jbowes@redhat.com)
- Add lookup for entitlements which modify the content of another. (dgoodwin@redhat.com)
- EUS product import support for modifiers and dependences (cduryee@redhat.com)
- 646624: refresh pools for owner after owner creation. (jesusr@redhat.com)
- 665128: Store product name with pools reliably. (dgoodwin@redhat.com)
- Pool test fix. (dgoodwin@redhat.com)
- 663455: Fix multi-entitlement blocking rule. (dgoodwin@redhat.com)
- 665118: Copy provided products when refreshing pools. (dgoodwin@redhat.com)
- Adding permissions to subscription activation. (jharris@redhat.com)
- Making checkstyle happy. (jharris@redhat.com)
- Adding activation by consumer to subscription resource and service.
  (jharris@redhat.com)
- Adding unit tests around consumer activation. (jharris@redhat.com)
- Enriching consumer model with canActivate for OEM activation. (jharris@redhat.com)
- bump version for post-beta work, next tag 0.2.1 (jesusr@redhat.com)
- hack-fix for content import (need to add real test data still) (cduryee@redhat.com)
- allow rest api to be exercised for model obj additions, and fix an annotation
  typo (cduryee@redhat.com)
- Filter content sets based on modified products. (dgoodwin@redhat.com)
- List entitlements providing product part 2. (dgoodwin@redhat.com)
- eus model obj refactoring fixes (cduryee@redhat.com)
- Add query to list entitlements providing product. (dgoodwin@redhat.com)
- eus model obj refactoring (cduryee@redhat.com)

* Wed Jan 12 2011 jesus m. rodriguez <jesusr@redhat.com> 0.1.28-1
- findbugs: unread assignment (jesusr@redhat.com)

* Fri Jan 07 2011 jesus m. rodriguez <jesusr@redhat.com> 0.1.27-1
- remove rounding to midnight. (jesusr@redhat.com)
- remove rounding to midnight changes. (jesusr@redhat.com)
- 660713: add unit test and use local TZ when rounding. (jesusr@redhat.com)
- fix localization test to match new string (cduryee@redhat.com)
- Very minor code cleanup. (dgoodwin@redhat.com)
- Add a translation for the invalid credentials error (bkearney@redhat.com)
- Change the default error to not reference username/password (bkearney@redhat.com)
- spec: Fix owner resource parent test. (dgoodwin@redhat.com)
- spec: Fix pool resource expiry test. (dgoodwin@redhat.com)
- Support x86 variants in the arch rule. (dgoodwin@redhat.com)

* Tue Dec 21 2010 jesus m. rodriguez <jesusr@redhat.com> 0.1.26-1
- Break out the string manipulation to find an FTE (bkearney@redhat.com)
- Only return json for the status (bkearney@redhat.com)

* Mon Dec 20 2010 jesus m. rodriguez <jesusr@redhat.com> 0.1.25-1
- checkstyle fix (jesusr@redhat.com)
- findbugs: Method may fail to close stream (jesusr@redhat.com)
- findbugs: USELESS_STRING: Invocation of toString on an array (jesusr@redhat.com)
- findbugs: unwritten field. (jesusr@redhat.com)
- findbugs: should be a static inner class (jesusr@redhat.com)
- findbugs: USELESS_STRING: Invocation of toString on an array (jesusr@redhat.com)
- findbugs: inefficient use of Number constructor, use .valueOf (jesusr@redhat.com)
- stablize qpid jars by using my repo (jesusr@redhat.com)
- Updating coverity task in buildfile (jharris@redhat.com)

* Fri Dec 17 2010 jesus m. rodriguez <jesusr@redhat.com> 0.1.24-1
- end of sprint build (jesusr@redhat.com)
- merge configuration tests (jesusr@redhat.com)

* Thu Dec 16 2010 jesus m. rodriguez <jesusr@redhat.com> 0.1.23-1
- fix OAuth when using PUT + application/json. (jesusr@redhat.com)

* Tue Dec 14 2010 jesus m. rodriguez <jesusr@redhat.com> 0.1.22-1
- rebuild

* Tue Dec 14 2010 jesus m. rodriguez <jesusr@redhat.com> 0.1.21-1
- Initial oauth implementation (bkearney@redhat.com)

* Fri Dec 10 2010 jesus m. rodriguez <jesusr@redhat.com> 0.1.20-1
- 660713: round the subscription's end date to midnight (anadathu@redhat.com)
- 658683: replace entitlement's id with the pool id (anadathu@redhat.com)
- 658683: Improve error message thrown when outstanding sub-pool entitlements
  exists. (anadathu@redhat.com)
- fix checkstyle violations (anadathu@redhat.com)
- findbugs: Primitive value is boxed then unboxed to perform primitive coercion (jesusr@redhat.com)
- findbugs: should be a static inner class (jesusr@redhat.com)
- findbugs: unread field: should this field be static? yes (jesusr@redhat.com)
- findbugs: Dead store to local variable (jesusr@redhat.com)
- findbugs: Private method is never called (jesusr@redhat.com)
- findbugs: invokes inefficient new String(String) constructor (jesusr@redhat.com)
- findbugs: Dead store to local variable (jesusr@redhat.com)
- findbugs: Comparison of String objects using == (jesusr@redhat.com)
- findbugs: Null pointer dereference of o (jesusr@redhat.com)

* Fri Dec 03 2010 jesus m. rodriguez <jesusr@redhat.com> 0.1.19-1
- link Owner to parent by id not a bytearray. (jesusr@redhat.com)

* Wed Dec 01 2010 jesus m. rodriguez <jesusr@redhat.com> 0.1.18-1
- attempt to fix pre_arch rule (jbowes@redhat.com)
- Revert "Deleting pre-architecture rule which was preventing list or subscribe
  from working. TODO : diagnose & fix when AKUMAR returns." (jbowes@redhat.com)
- Deleting pre-architecture rule which was preventing list or subscribe from
  working. TODO : diagnose & fix when AKUMAR returns. (jomara@redhat.com)

* Wed Dec 01 2010 jesus m. rodriguez <jesusr@redhat.com> 0.1.17-1
- rules: remove a printf (jbowes@redhat.com)
- autobind: cache ReadOnlyProducts to reduce product adapter hits (jbowes@redhat.com)
- fix checkstyle (jesusr@redhat.com)
- add building instructions (jesusr@redhat.com)
- fix null in StatusResource (jesusr@redhat.com)
- increase unit test coverage (jesusr@redhat.com)
- If no quantity is provided for a derivced product, default to zero (bkearney@redhat.com)

* Tue Nov 30 2010 jesus m. rodriguez <jesusr@redhat.com> 0.1.16-1
- adding unit tests (jesusr@redhat.com)
- adding test for LDAPUserServiceAdapter (jesusr@redhat.com)
- autobind: add log to the js context to fix unit tests (jbowes@redhat.com)
- Add support for a 'management_enabled' attribute on the subscription (bkearney@redhat.com)
- autobind: when finding similar class pools, compare all relevant products (jbowes@redhat.com)
- autobind: Ignore pool combos with more pools than requested products (jbowes@redhat.com)
- added support for deletion of products (ddolguik@redhat.com)

* Tue Nov 23 2010 Devan Goodwin <dgoodwin@redhat.com> 0.1.15-1
- I18N string update. (dgoodwin@redhat.com)

* Tue Nov 23 2010 jesus m. rodriguez <jesusr@redhat.com> 0.1.14-1
- end of sprint tag (jesusr@redhat.com)
- 655835: pools are no longer removed after their expiration date (anadathu@redhat.com)
- don't use commons.logging when everything else uses log4j (jesusr@redhat.com)
- default logger to INFO (jesusr@redhat.com)
- 639434: attempts to unregister person consumer should fail when system
  consumer has a subs. (anadathu@redhat.com)

* Tue Nov 16 2010 jesus m. rodriguez <jesusr@redhat.com> 0.1.13-1
- 653495: send gpg_key_url with product.create/modified events (jesusr@redhat.com)
- remove MKT from import_products.rb (jesusr@redhat.com)
- remove assertion from DefaultEntitlementCertServiceAdapter (anadathu@redhat.com)
- Adding Gemfile for bundler support (gem management) (jharris@redhat.com)
- remove duplicate imports and shorten line (jesusr@redhat.com)
- use numeric ids (jesusr@redhat.com)
- keep id, name in order. Use numeric ids. (jesusr@redhat.com)
- remove MKT from code, use productid format. (jesusr@redhat.com)
- spec: use a random string suffix for multi arch test (jbowes@redhat.com)
- spec: use a random string suffix for refresh pools test (jbowes@redhat.com)
- import/export: bring down and import contract and account numbers (jbowes@redhat.com)
- 641155: candlepin should not generate certs that will be rejected by rhsm (anadathu@redhat.com)
- Add owner updates (bkearney@redhat.com)
- Minor fixes to tests for provided pools (calfonso@redhat.com)
- add support for multiple architectures as attributes of product(s) (anadathu@redhat.com)

* Wed Nov 10 2010 jesus m. rodriguez <jesusr@redhat.com> 0.1.12-1
- bump candlepin-deps to 0.0.11, new qpid and ldap (jesusr@redhat.com)
- fix small bugs with ProvidedProducts. (anadathu@redhat.com)
- Get the logging tests running again (bkearney@redhat.com)
- Adding a ProvidedProducts entity for Pools. The provided products now have a
  name associated with them (calfonso@redhat.com)
- remove annoying hibernateSessionId logs and improve request logs
  (anadathu@redhat.com)
- Use the latest qpidd (bkearney@redhat.com)
- add rspec test for list_users_by_owner (alikins@redhat.com)
- Add a getUser method to OwnerResource (alikins@redhat.com)
- autobind: speed things up by running fewer overlap checks (jbowes@redhat.com)
- Candlepin allows you create a hierarchy of owners (anadathu@redhat.com)
- add account number to the load so we can verify it (bkearney@redhat.com)
- Add a duplicate subscription at the current time for all subscriptions
  (bkearney@redhat.com)
- Add future dated subscriptions to the load (bkearney@redhat.com)
- The header for trusted was incorrect (bkearney@redhat.com)
- Removing build.yaml because no seems to be able to fix hudson.
  (jharris@redhat.com)
- A couple of coverity errors (bkearney@redhat.com)
- Add explicit date filtering for GET /pools. (dgoodwin@redhat.com)
- Added basic ldap authentication to the code base (bkearney@redhat.com)

* Wed Nov 03 2010 Devan Goodwin <dgoodwin@redhat.com> 0.1.11-1
- Update I18N strings. (dgoodwin@redhat.com)

* Wed Nov 03 2010 jesus m. rodriguez <jesusr@redhat.com> 0.1.10-1
- add URL to README (jesusr@redhat.com)

* Tue Nov 02 2010 jesus m. rodriguez <jesusr@redhat.com> 0.1.9-1
- Adding gem dependencies for buildr to make gem management a little easier.  (jharris@redhat.com)
- autobind: Iterator() doesn't work with all rhino versions (jbowes@redhat.com)
- Add trusted authentication for consumers. In this model, a header is used to
  pass along the consumer id. If it exits, the call is assumed to be trusted
  and the consumer principal is created (bkearney@redhat.com)
- Expose GET /products/id to consumers and owner admins. (dgoodwin@redhat.com)
- autobind: convert from js array to java array in js (jbowes@redhat.com)
- autobind: detect and remove pool combos with product overlap (jbowes@redhat.com)
- autobind: remove unused autobind rspec (jbowes@redhat.com)
- autobind: check all possible pool combinations (jbowes@redhat.com)
- autobind: compare dates for pools providing N products, too (jbowes@redhat.com)
- autobind: compare expiration dates for pool seelection (jbowes@redhat.com)
- autobind: use iterators over the collections provided to the js (jbowes@redhat.com)
- checkstyle: turn off illegalimport check (jbowes@redhat.com)
- autobind: js fully hooked up to returning multiple pools based on multiple
  products (jbowes@redhat.com)
- autobind: return an array of pools from select_best (jbowes@redhat.com)
- autobind: teach the enforcer that it can return multiple pools on selectBest
  (jbowes@redhat.com)
- autobind: ensure that the first applicable pool isn't always used for bind by
  product (jbowes@redhat.com)
- autobind: send multiple products to the js enforcer (jbowes@redhat.com)

* Mon Oct 25 2010 jesus m. rodriguez <jesusr@redhat.com> 0.1.8-1
- 646000: candlepin requires c3p0 from latest deps rpm (jesusr@redhat.com)
- Make status and root items unprotected (bkearney@redhat.com)

* Mon Oct 25 2010 jesus m. rodriguez <jesusr@redhat.com> 0.1.7-1
- 646000: add c3p0 dep and configuration. (jesusr@redhat.com)
- 646000: remove trailing whitespace (jesusr@redhat.com)
- candlepin url is now a configuration parameter (ddolguik@redhat.com)
- Fixing Order Name in entitlement certs. (jharris@redhat.com)
- Added link to the event in ATOM feed entries (ddolguik@redhat.com)
- 645567: Atom feed does not validate (ddolguik@redhat.com)
- 640463: Update the oids in the order namespace (bkearney@redhat.com)
- Add a comment to the schemaspy task so I dont have to look it up (bkearney@redhat.com)
- On bind by token, use the pool matching the sub matching the token (jbowes@redhat.com)
- Allow for multiple product ids to be sent during bind (jbowes@redhat.com)
- Adding contract number to pools (jharris@redhat.com)
- Adding the sku namespace (calfonso@redhat.com)
- Adding account number to subscription model & cert (jomara@redhat.com)

* Mon Oct 18 2010 jesus m. rodriguez <jesusr@redhat.com> 0.1.6-1
- brew prep - add Group to tomcat6 sub package (jesusr@redhat.com)

* Mon Oct 18 2010 jesus m. rodriguez <jesusr@redhat.com> 0.1.5-1
- brew prep - require 0.0.9, since that's the working one :) (jesusr@redhat.com)
- brew prep - require candlepin-deps >= 0.0.7 (jesusr@redhat.com)
- brew prep - ugh removing the commons-lang dep (jesusr@redhat.com)

* Mon Oct 18 2010 jesus m. rodriguez <jesusr@redhat.com> 0.1.4-1
- brew prep - depend on jakarta-commons-lang now (jesusr@redhat.com)

* Mon Oct 18 2010 jesus m. rodriguez <jesusr@redhat.com> 0.1.3-1
- brew prep - add Group field to sub-packages (jesusr@redhat.com)
- brew prep - add in dist to the release (jesusr@redhat.com)
* Mon Oct 18 2010 jesus m. rodriguez <jesusr@redhat.com> 0.1.2-1
- brew prep - require ant and candlepin-deps (jesusr@redhat.com)

* Fri Oct 15 2010 jesus m. rodriguez <jesusr@redhat.com> 0.1.1-1
- 642754: Remove the XML generate annotations from all the resources (ddolguik@redhat.com)
- Reversion for beta, next tag will be 0.1.1. (dgoodwin@redhat.com)
- Update I18N strings. (dgoodwin@redhat.com)
- add mysql tables for quartz (jmrodri@gmail.com)
- Allowing import script to create users under admin (jharris@redhat.com)
- Allow running of one rspec test only. (dgoodwin@redhat.com)
- Fix rspec cross-owner consumer creation test. (dgoodwin@redhat.com)

* Tue Oct 12 2010 jesus m. rodriguez <jesusr@redhat.com> 0.0.41-1
- Changing the product import to allow for owner/user hierarchy. (jharris@redhat.com)
- Making system-uuid generated id's db column length = 32 (calfonso@redhat.com)
- brew prep - add ant build file and trivial support scripts.  (jesusr@redhat.com)
- brew prep - script to download artifacts for cp-deps rpm. (jesusr@redhat.com)
- brew prep - remove unused buildr mod (jesusr@redhat.com)
- brew prep - remove unused buildfile.jpp (jesusr@redhat.com)
- brew prep - remove unused Makefile (jesusr@redhat.com)
- 640967: Added validation for the consumer name. (bkearney@redhat.com)
- Add logging around the session id (bkearney@redhat.com)
- Only filter facts once on an update (bkearney@redhat.com)
- 639320: patch to fix serial number formatting (anadathu@redhat.com)

* Thu Oct 07 2010 jesus m. rodriguez <jesusr@redhat.com> 0.0.40-1
- Adding the subscription end date to the logging statement (calfonso@redhat.com)
- compare ms on entitlement/subscription and not date.equals (anadathu@redhat.com)

* Thu Oct 07 2010 jesus m. rodriguez <jesusr@redhat.com> 0.0.39-1
- Fixing up import/export tests to create subscriptions (jharris@redhat.com)
- Adding entitlement permission test (jharris@redhat.com)
- Cleaning up stale code in entitlement resource. (jharris@redhat.com)
- 615333: Create human readable error messages for all entitlement failures (jbowes@redhat.com)
- 640484: consumer facts updated only when changed (ddolguik@redhat.com)

* Wed Oct 06 2010 jesus m. rodriguez <jesusr@redhat.com> 0.0.38-1
- Fixing up lambda syntax for rspec exceptions (jharris@redhat.com)
- Altering access control to consumer curator. (jharris@redhat.com)
- Subscription's end date should not be out of sync with that of entitlement/pool (anadathu@redhat.com)
- Fixing up ruby libs/tests (jharris@redhat.com)
- Add in some logging (bkearney@redhat.com)
- correct javadoc warnings (anadathu@redhat.com)
- Candlepin now returns 400 after processing a badly formed request (anadathu@redhat.com)
- Product changes detected during imports and triggers certificate regeneration. (anadathu@redhat.com)
- spec: fix subpool spec with new id changes (jbowes@redhat.com)
- fix subscription deletion (jbowes@redhat.com)
- Fix subscription token deletion (jbowes@redhat.com)
- Refactored generateUniqueLong to the util class (calfonso@redhat.com)
- Modified serial number generation to use number concat (calfonso@redhat.com)
- putting the postgres version back (calfonso@redhat.com)
- Fix a broken test (calfonso@redhat.com)
- All entity identifiers now use a uuid string. (calfonso@redhat.com)

* Thu Sep 30 2010 Devan Goodwin <dgoodwin@redhat.com> 0.0.37-1
- Setting user license check to be case insensitive (calfonso@redhat.com)
- Set 'Not After' to 23:59:59 (before next day). (anadathu@redhat.com)
- Add subscription modified/deleted events to importer. (jesusr@redhat.com)
- Add subscription deleted, moved subscription modified. (jesusr@redhat.com)
- 636843: NPE is no longer being thrown on reregister event
  (ddolguik@redhat.com)
- Added a spec for sub-pools - unregistering person consumer.
  (ddolguik@redhat.com)
- Change deprecated javadoc -> doc. (jesusr@redhat.com)
- Adding modified event. (jmrodri@gmail.com)

* Thu Sep 23 2010 jesus m. rodriguez <jesusr@redhat.com> 0.0.36-1
- change << HSQLDB to += artifacts(HSQLDB) (jesusr@redhat.com)
- spec: fix filter certificates by serial number (jbowes@redhat.com)
- spec: convert facts_update.feature to rspec (jbowes@redhat.com)
- cuke: remove consumer_pool_query.feature (its already covered in rspec) (jbowes@redhat.com)
- spec: convert authorization.feature to rspec (jbowes@redhat.com)
- cuke: remove unused rules.feature (jbowes@redhat.com)
- spec: convert entitlement_certificates.feature to rspec (jbowes@redhat.com)
- spec: convert status.feature to rspec (jbowes@redhat.com)
- cuke: remove unused virt feature (jbowes@redhat.com)
- spec: add missing localization_spec (forgot to git add) (jbowes@redhat.com)
- cuke: remove CRL tests (its in spec already) (jbowes@redhat.com)
- cuke: remove pool feature (its in spec already) (jbowes@redhat.com)
- cuke: remove an already ported test (jbowes@redhat.com)
- spec: remove stray puts (jbowes@redhat.com)

* Wed Sep 22 2010 jesus m. rodriguez <jesusr@redhat.com> 0.0.35-1
- Making checkstyle happy (jharris@redhat.com)
- Fixing date comparison rule in test-rules (jharris@redhat.com)
- Re-extract and merge I18N strings. (dgoodwin@redhat.com)
- I18N tweaks. (dgoodwin@redhat.com)
- Doing some refactoring of entitlement importer tests (jharris@redhat.com)
- Fixing EntitlementImporterTest (jharris@redhat.com)
- Forgot to clean out config collaborator (jharris@redhat.com)
- Fixing up unit tests (jharris@redhat.com)
- submit the cacert body not the path (jesusr@redhat.com)
- Internationalize import errors. (dgoodwin@redhat.com)
- Fix import failure if export has no entitlements/products. (dgoodwin@redhat.com)
- calculate topic based on event name, adding mapping map too (jesusr@redhat.com)
- add version and id to envelope (jesusr@redhat.com)
- 626509 - unentitle the consumer, but do not delete the pools (ddolguik@redhat.com)
- Adding an activation listener to send activation emails (jomara@redhat.com)
- Tweak deploy script env vars, set to 1 to enable. (dgoodwin@redhat.com)
- Fixing up rspecs (jharris@redhat.com)
- Regenerate entitlement certificates if a pool's provided product set has
  changed (jbowes@redhat.com)
- Lots of cuke fixes to get tests passing again. (jharris@redhat.com)
- 631878 - Remove the code and test cases (bkearney@redhat.com)
- fixed style problem (ddolguik@redhat.com)
- verify if sub-pools are present BEFORE deleting the pool (ddolguik@redhat.com)
- new Strings (bkearney@redhat.com)
- owner.* -> amqp in AMQPBusEventAdapter (anadathu@redhat.com)
- Do another string merge (bkearney@redhat.com)
- add subscription.created event during import (jesusr@redhat.com)
- send to the right topic (jesusr@redhat.com)
- add logging statements (jesusr@redhat.com)
- junit test cases for consumer.* events (anadathu@redhat.com)
- Track last checkin time for consumers. (dgoodwin@redhat.com)
- Introduce a configurable consumer facts filter (jbowes@redhat.com)
- Cleaning out error status message check to be compatible with newer versions
  of rest-client. (jharris@redhat.com)
- Injecting poolManager into the EntitlementResource (calfonso@redhat.com)
- mark event locations (jesusr@redhat.com)
- remove unused import (jesusr@redhat.com)
- remove importSubscriptionCerts method, certs coming from json. (jesusr@redhat.com)
- Expanding subscription creation event tests to cover modifications as well (jharris@redhat.com)
- Fixing checkstyle issues in EntitlementImporter (jharris@redhat.com)
- Fixing up consumer resource test (jharris@redhat.com)
- Adding tests and functionality for subscription creation events.  (jharris@redhat.com)
- fix failing unit tests (jbowes@redhat.com)
- 615362 - Fix i18n/l10n (jbowes@redhat.com)
- AMQPBusEventAdapter supports consumer events. (anadathu@redhat.com)
- import the certificates with the subscription (jesusr@redhat.com)
- added importing certs method (jesusr@redhat.com)
- Reuse cp_certificate table to store Subscription certs. (jesusr@redhat.com)
- Cleaning up imports (jharris@redhat.com)
- Redoing changes to AMQP event adapter. (jharris@redhat.com)
- Refactoring event adapter and adding unit tests. (jharris@redhat.com)
- Checkstyle fixes (jharris@redhat.com)
- Refactoring the event adapter and adding subscription event translation. (jharris@redhat.com)

* Mon Sep 13 2010 jesus m. rodriguez <jesusr@redhat.com> 0.0.34-1
- Disable HATEOAS for collections. (dgoodwin@redhat.com)
- 629747 - made the table names consistent (bkearney@redhat.com)
- Refreshed the string files (bkearney@redhat.com)

* Thu Sep 09 2010 jesus m. rodriguez <jesusr@redhat.com> 0.0.33-1
- Merge branch 'master' into hateros2 (dgoodwin@redhat.com)
- Identity Certificate's endDate is configurable. (anadathu@redhat.com)
- Injecting the product adapter in the EntitlementResource (calfonso@redhat.com)
- Moving the cache purging to the product/{product_id} api (calfonso@redhat.com)
- Fix owner rspec tests after merge. (dgoodwin@redhat.com)
- Fix JSON config discrepancy. (dgoodwin@redhat.com)
- Fix Cucumber tests after HATEOAS changes. (dgoodwin@redhat.com)
- Support pool deletion. (dgoodwin@redhat.com)
- Refractored the code in AMQPBusEventAdapter(FTW). (anadathu@redhat.com)
- Emit pool creation events. (dgoodwin@redhat.com)
- amqp: add bash script to configure ssl (jbowes@redhat.com)
- amqp: use ssl to connect to the message bus (jbowes@redhat.com)
- Fix Entitlement href, expose at top level URL. (dgoodwin@redhat.com)
- Fix consumer resource spec. (dgoodwin@redhat.com)
- Fix cert revocation spec. (dgoodwin@redhat.com)
- Repair export spec. (dgoodwin@redhat.com)
- Fix authorization spec. (dgoodwin@redhat.com)
- Repair entitlement cert spec. (dgoodwin@redhat.com)
- Add setHref to Linkable interface. (dgoodwin@redhat.com)
- Make owner pools and consumers XmlTransient once more. (dgoodwin@redhat.com)
- Randomize owner names. (dgoodwin@redhat.com)
- HATEOAS: Repair entitlement cert spec. (dgoodwin@redhat.com)
- Add top level linking for HATEOAS. (dgoodwin@redhat.com)
- Test entitlement serialization. (dgoodwin@redhat.com)
- Include "id" in HATEOAS serialized form. (dgoodwin@redhat.com)
- Test / cleanup Pool serialization. (dgoodwin@redhat.com)
- Test and touchup consumer serialization. (dgoodwin@redhat.com)
- Start serializing owner consumer/pool collections. (dgoodwin@redhat.com)
- Enable HATEOAS serialization for collections. (dgoodwin@redhat.com)
- Add link to Jackson BeanSerializerFactory source. (dgoodwin@redhat.com)
- Merge branch 'master' into hateros2 (dgoodwin@redhat.com)
- Add HATEOAS support. (dgoodwin@redhat.com)

* Wed Sep 01 2010 jesus m. rodriguez <jesusr@redhat.com> 0.0.32-1
- Better error for unbind/unregister as consumer with sub-pools in use.  (dgoodwin@redhat.com)
- Use owner keys instead of IDs in REST API. (dgoodwin@redhat.com)
- Regenerating entitlement certificates based on product_id works 'async' (anadathu@redhat.com)
- Fix junit failure (anadathu@redhat.com)
- continue cucumber -> rspec. (subscriptions.feature ->
  subscription_resource_spec + entitlement_certificate_spec)
  (anadathu@redhat.com)

* Thu Aug 26 2010 jesus m. rodriguez <jesusr@redhat.com> 0.0.31-1
- Adding additional specs for refresh_pools (jharris@redhat.com)
- cucumber -> rspec : certificate revocation scenario (anadathu@redhat.com)
- Change setUserName to setUsername for marshalling (calfonso@redhat.com)
- Added setSerial method to make json mashalling work (calfonso@redhat.com)
- make Principal Serializeable for quartz clustering. (jesusr@redhat.com)
- add QUARTZ to :genschema (jesusr@redhat.com)
- Adding new quartz-based async functionality. (jharris@redhat.com)
- Disable flex expiry for now. (dgoodwin@redhat.com)

* Thu Aug 19 2010 jesus m. rodriguez <jesusr@redhat.com> 0.0.30-1
- clean up export metadata on owner deletion (jbowes@redhat.com)
- cert: default to a warning period of 0 if none specified (jbowes@redhat.com)
- test ContentResource, AtomFeedResource, AdminResource (jesusr@redhat.com)
- Looking up the owner using the owner curator (calfonso@redhat.com)
- Adding support for warning period (jomara@redhat.com)
- amqp: generate queue configuration in code (jbowes@redhat.com)
- remove old validate method (jesusr@redhat.com)
- rework unit tests (jesusr@redhat.com)
- adding tests (jesusr@redhat.com)
- lookupByTypeAndOwner method (jesusr@redhat.com)
- add owner and new constructor (jesusr@redhat.com)
- rename TYPE_METADATA -> TYPE_SYSTEM, add Owner (jesusr@redhat.com)
- tabs are EVIL! (jesusr@redhat.com)
- Improve junit test coverage in audit package (anadathu@redhat.com)
- silence checkstyle/bump my commit metrics (jbowes@redhat.com)
- remove assertion checking cert expiration date vs revocation date (jbowes@redhat.com)
- Adding resteasy's async funcionality. (jharris@redhat.com)
- Fix still broken query for a users "person consumer". (dgoodwin@redhat.com)
- make sure all is well by fixing the test (jesusr@redhat.com)
- remove the metadatacurator from the exporter (jesusr@redhat.com)
- store off the last time the importer ran. (jesusr@redhat.com)
- rspec: convert entitlement regen tests from cukes to rspec (jbowes@redhat.com)
- Open GET /pools/id to consumers and owner admins. (dgoodwin@redhat.com)
- Contents: AMQP Integration with candlepin events using apache's qpid java
  client. (anadathu@redhat.com)
- ruby: Drop unused arg for update subscriptions. (dgoodwin@redhat.com)
- rspec: Flex expiry test for refresh pools. (dgoodwin@redhat.com)
- rspec: get rid of 'should' from our specs (jbowes@redhat.com)
- rspec: output colourized results (jbowes@redhat.com)
- Allow a super admin to create a consumer on behalf of another owner (jbowes@redhat.com)
- rspec: Test flex expiry after import. (dgoodwin@redhat.com)
- rspec: Test export and import process. (dgoodwin@redhat.com)
- Drop EntitlementDto for export. (dgoodwin@redhat.com)
- Fix date format in JSON export. (dgoodwin@redhat.com)
- Port export tests to rspec. (dgoodwin@redhat.com)
- Better support for pools not backed by a subscription. (dgoodwin@redhat.com)
- Allow consumer list filtering by type and username (jbowes@redhat.com)
- Purging product data caching when refreshing pools (calfonso@redhat.com)
- verify metadata version upon import. (jesusr@redhat.com)
- change setLong to setId (jesusr@redhat.com)
- pass config in (jesusr@redhat.com)
- Storing export metadata to the DB (jesusr@redhat.com)
- comment the validatemetajson file (jesusr@redhat.com)
- store off meta data to a tmp file (for now) (jesusr@redhat.com)
- move Meta out to a real class (jesusr@redhat.com)
- spec: Fix bad create_product variable name. (dgoodwin@redhat.com)
- ruby: Touchup create_pool method for optional args. (dgoodwin@redhat.com)
- ruby: Cleanup product creation. (dgoodwin@redhat.com)

* Tue Aug 10 2010 jesus m. rodriguez <jesusr@redhat.com> 0.0.29-1
- ruby: More consistent list method names. (dgoodwin@redhat.com)
- Support GET /owners?key=X. (dgoodwin@redhat.com)
- make EventSinkImpl singleton (anadathu@redhat.com)
- Add server side support for flex expiry. (dgoodwin@redhat.com)
- re-use session and producer in hornetq (anadathu@redhat.com)
- Fix bug with product import. (dgoodwin@redhat.com)
- store the order start and end date in iso8601 format (and utc) (jbowes@redhat.com)
- fix junit failure in PoolManagerTest (anadathu@redhat.com)
- rspec: Test touchups. (dgoodwin@redhat.com)
- Add some logging for ForbiddenExceptions. (dgoodwin@redhat.com)
- rspec: Support running specific spec files from buildr. (dgoodwin@redhat.com)
- rspec: Use nested output when running from buildr. (dgoodwin@redhat.com)
- Remove print from rules. (dgoodwin@redhat.com)
- Fix access control bug for consumers binding by pool. (dgoodwin@redhat.com)
- Modify rules to require system consumer type by default. (dgoodwin@redhat.com)
- schemadiff: fix diff ordering (jbowes@redhat.com)
- Adding some test coverage for JsonProvider. (jharris@redhat.com)
- 615362  -Translated strings were not being packaged correctly. Added them
  into the build as well as a sample test string to test against (bkearney@redhat.com)
- Fix product/pool attribute mappings. (dgoodwin@redhat.com)
- 615404 - changed the name (bkearney@redhat.com)
- Fix person consumer association. (dgoodwin@redhat.com)
- fixed resource leaks (ddolguik@redhat.com)
- replaced '==' comparison with a call to #equals (ddolguik@redhat.com)

* Mon Aug 02 2010 jesus m. rodriguez <jesusr@redhat.com> 0.0.28-1
- rename consumer -> consumerExporter avoids warning. (jesusr@redhat.com)
- don't bother with the assignment, just return it (jesusr@redhat.com)
- remove System.out.println, move int n closer to block. (jesusr@redhat.com)
- rename init (jesusr@redhat.com)
- test the LoggingConfig (jesusr@redhat.com)
- fix inner assignment complaint from checkstyle (jesusr@redhat.com)
- Name clean ups (bkearney@redhat.com)
- Add long asserts into the subscription token (bkearney@redhat.com)
- Add assertLong logic to the subscription manager token resource class (bkearney@redhat.com)
- missing files break the build (ddolguik@redhat.com)
- Move the long check to a util class so it can be used in other places (bkearney@redhat.com)
- a dummy PKIReader is used for majority of tests; ConsumerResourceTest.java
  and ProductCertCreationTest.java use test-specific ca certs and key (ddolguik@redhat.com)
- need to copy ca cert to create upstream ca cert, not the key (ddolguik@redhat.com)
- create upstream ca cert by copying ca cert. (ddolguik@redhat.com)
- export signature is now being correctly verified upon import (ddolguik@redhat.com)
- fixed upstream ca cert configuration (ddolguik@redhat.com)
- added hash check on import (ddolguik@redhat.com)
- export files are now signed with sha256 with CA certificate (ddolguik@redhat.com)
- added sha256rsa generation/verification (ddolguik@redhat.com)
- Show what user/owners we are creating on import (alikins@redhat.com)
- don't loose exceptions on import/export; checkstyle cleanup. fix eventfactory
  bug(s) (anadathu@redhat.com)
- unit test the CandlepinExceptionMapper (jesusr@redhat.com)
- make response builder methods private (jesusr@redhat.com)
- Reverting to resteasy 2.0-beta-4 to fix the exception mapper issue. (jharris@redhat.com)
- Add support for creating some users and owners in the import (alikins@redhat.com)

* Wed Jul 28 2010 jesus m. rodriguez <jesusr@redhat.com> 0.0.27-1
- change in subscription dates should be reflected in entitlement
  certificates (anadathu@redhat.com)
- change istype to use getLabel(), add unit test (jesusr@redhat.com)
- remove unused variables (jesusr@redhat.com)
- use static import (jesusr@redhat.com)
- sync: save entitlement certs as <serialId>.pem (jbowes@redhat.com)
- remove unnecessary casts, tests, javadoc and ; (jesusr@redhat.com)
- fix warning, reorg'd imports (jesusr@redhat.com)
- don't need the user for regenerating id certs, just consumer. (jesusr@redhat.com)
- I wish the line limit was 80 instead of 92.  (bkearney@redhat.com)
- Push a logger into the rules execution context (bkearney@redhat.com)
- import created event is emitted after import is complete (ddolguik@redhat.com)
- export created event is emitted after consumer export (ddolguik@redhat.com)
- remove duplicate changelog entry. (jesusr@redhat.com)
- put crl file in /var/lib/candlepin by default instead of /etc/candlepin
  (jesusr@redhat.com)
- Changing sub-pool binding to look up the originating subscription.
  (jharris@redhat.com)
- unit test regenerateIdentityCertificate using Mockito! (jesusr@redhat.com)
- reapply my changes from commit 126242b removed by Jython. (jesusr@redhat.com)
- cucumber test for entitlement certificate regeneration for a given product
  (ddolguik@redhat.com)
- added regenration of entitlement certificates for a specified product
  (ddolguik@redhat.com)
- remove stupid generated comment (jesusr@redhat.com)
- add a unit test (jesusr@redhat.com)
- handle null events (jesusr@redhat.com)
- test the logging wrapper (jesusr@redhat.com)
- test the DatabaseListener, handle nulls properly. (jesusr@redhat.com)
- fcs (bkearney@redhat.com)
- Test for the invalid poolIds (bkearney@redhat.com)
- Code, then test. Not the other way around (bkearney@redhat.com)
- 608005 - Allow strings to be passed in for pool ids and then validate
  internally (bkearney@redhat.com)
- Move candlepin to resteasy 2.0 GA (bkearney@redhat.com)
- test the CRLException case (jesusr@redhat.com)
- Make import_products be a little less chatty. (alikins@redhat.com)
- rework the CRL task and its unit test. (jesusr@redhat.com)
- jUnit failures fixed. Added cuke test cases around subscription updates.
  (anadathu@redhat.com)
- Add null check before call to avoid ORA-00932 (morazi@redhat.com)

* Thu Jul 22 2010 jesus m. rodriguez <jesusr@redhat.com> 0.0.26-1
- sync: make product cert export optional (jbowes@redhat.com)
- minor changes around restricted pool cleanup on consumer delete (ddolguik@redhat.com)
- consumers of sub-pool are being unentitled when the main consumer is deleted (ddolguik@redhat.com)

* Thu Jul 22 2010 Devan Goodwin <dgoodwin@redhat.com> 0.0.25-1
- Delete pools when their subscription is removed. (dgoodwin@redhat.com)
- PoolManager created. Changes in Subscription dates re-creates entitlement
  certs (anadathu@redhat.com)
- Remove old cleanup of pools via source entitlement. (dgoodwin@redhat.com)
- Fix bug with Pool -> Entitlement relationship. (dgoodwin@redhat.com)

* Wed Jul 21 2010 Devan Goodwin <dgoodwin@redhat.com> 0.0.24-1
- Fixing bind by pool without a subscription. (jharris@redhat.com)
- adding identity cert regeneration. (jesusr@redhat.com)
- merged revocation_rules into master (ddolguik@redhat.com)
- rules for consumer delete events (ddolguik@redhat.com)
- renamed pre- and post- methods in enforcer to preEntitlement- and
  postEntitlement- (ddolguik@redhat.com)
- introduced namespaces in js rules (ddolguik@redhat.com)
- Use the CN to get the UUID, instead of UID (bkearney@redhat.com)
- create the private key with a password (bkearney@redhat.com)
- Have tomcats keystore use the cert and password from candlepin
  (bkearney@redhat.com)
- Implement Pool cleanup. (dgoodwin@redhat.com)
- Make Pool -> Entitlement relationship bi-directional. (dgoodwin@redhat.com)
- Add a curator method to list pools by source entitlement.
  (dgoodwin@redhat.com)

* Fri Jul 16 2010 Devan Goodwin <dgoodwin@redhat.com> 0.0.23-1
- Add support for exporting data to downstream Candlepin. (jbowes@redhat.com)
- Add schemadiff, a script to check for schema changes over time
  (jbowes@redhat.com)
- Setting the CRL issuer dn using the CA certificate issuer
  (calfonso@redhat.com)
- CRL entries should use the current date of revocation rather than the serial
  number / certificate date of expiration. (calfonso@redhat.com)
- Drop the attribute hierarchy. (dgoodwin@redhat.com)
- Update logging filter to deal with application/zip (jbowes@redhat.com)
- Make Product -> Subscription relationship bi-directional.
  (dgoodwin@redhat.com)
- Fix orphaned attributes. (dgoodwin@redhat.com)
- Split Attributes into separate tables for Pools and Products.
  (dgoodwin@redhat.com)
* Mon Jul 12 2010 jesus m. rodriguez <jesusr@redhat.com> 0.0.22-1
- Adding a few more CRL functional tests. (jharris@redhat.com)
- Adding CRL functional test scenarios. (jharris@redhat.com)
- Modifying the CRL job schedule to once a day (calfonso@redhat.com)
- Rhino/JS != weirdness? (morazi@redhat.com)
- Adding CRL resource and matching ruby api (jharris@redhat.com)
- another resource leak - in CandlepinPKIReader.java (ddolguik@redhat.com)
- fixed a resource leak in CertificateRevocationListTask.java (ddolguik@redhat.com)
- fixed a small null-dereferencing issue (ddolguik@redhat.com)
- added buildr task for coverity report generation (ddolguik@redhat.com)
- Pulling the CRL generation task into a separate controller. (jharris@redhat.com)

* Thu Jul 08 2010 Adrian Likins <alikins@redhat.com> 0.0.21-1
- candlepin pki reader now caches certificate & keys (anadathu@redhat.com)
- * Adding bulk revocation script * Refactoring Pinsetter to use common config
  code (jharris@redhat.com)
- Added junit test cases and reduced logging level in CRLTask
  (anadathu@redhat.com)
- fix name to match others. (jesusr@redhat.com)
- * Adding functional tests around certificate revocation * Adding in cert
  revocation on unbind events * Lots of refactoring/reworking around cucumber
  tests * Various fixes for checkstyle and unit tests (jharris@redhat.com)
- Corrected checkstyle warnings! (anadathu@redhat.com)
- fix compilation issue in test cases (anadathu@redhat.com)
- auto_create_owner for refreshEntitlementPools and not createSubscriptions.
  (anadathu@redhat.com)
- Removed setId when creating owner. Id is autogenerated during
  createSubscription call. (anadathu@redhat.com)
- changed datasource back to postgresql (calfonso@redhat.com)
- removed duplicate constructor in CertificateSerial (anadathu@redhat.com)
- junit test cases, logging statements added. couple of bugs fixed
  (anadathu@redhat.com)
- Modifying entitlement cert functional test to account for new
  CertificateSerials. (jharris@redhat.com)
- Adding back in default constructor to fix things. (jharris@redhat.com)
- Removing instance injector (calfonso@redhat.com)
- crl read/write to/from file done. junits pending (anadathu@redhat.com)
- CRL creation/updation for certificates revoked - update 1
  (anadathu@redhat.com)
- Serial # records now carry meta data about the cert * revoked - if toggled to
  1, it means the serial number has been revoked * expiration - the expiration
  date of the associated certificate * collected - if the serial number record
  has been placed in the CRL (calfonso@redhat.com)
- Adding guice configuration to pinsetter tasks (calfonso@redhat.com)
- Auto create owner if he does not exist during createSubscription
  (anadathu@redhat.com)
- Refactoring consumer fact comparison and adding unit tests.
  (jharris@redhat.com)
- Adding 'consumer modified' events for fact changes. (jharris@redhat.com)
- Consumers unentitled when quantity of a subscription decreases implemented
  (anadathu@redhat.com)
- Update hornetq to 2.1.1.Final (jbowes@redhat.com)
- Merge branch 'hornetq-2.1.0.Final' (jbowes@redhat.com)
- Update jboss maven repo to new location (jbowes@redhat.com)
- Just handle DefaultOptionsMethodException (jbowes@redhat.com)
- Adding basic fact updating. (Events to come) (jharris@redhat.com)
- Revert "Just grab ApplicationException, so RESTeasy can handle its own
  exceptions" (jbowes@redhat.com)
- Just grab ApplicationException, so RESTeasy can handle its own exceptions
  (jbowes@redhat.com)
- Only update the pools if they have actually changed (bkearney@redhat.com)
- Allow for non-nested exceptions in the mapper (bkearney@redhat.com)
- Arch/version/variant are no longer in entitlement certs, so remove tests for
  it (adrian@alikins.usersys.redhat.com)
- Remove version/arch/variant info from product namespace in entitlement certs
  (adrian@alikins.usersys.redhat.com)
- Move to resteasy 2.0-beta-4 (bkearney@redhat.com)
- added EntitlementCertServiceAdapter#revokeEntitlementCertificates (the
  implementation is pending) (ddolguik@redhat.com)
- Farking checkstyle (bkearney@redhat.com)
- Trap all Runtime Errors. Non-caught runtime errors would result in no json in
  the ruturn body (bkearney@redhat.com)
- Had the enabled/disabled content flags backwards. Fix.
  (adrian@alikins.usersys.redhat.com)
- Allow for json reading the Atom Feed (bkearney@redhat.com)
- Add support for user_license_product attribute. (dgoodwin@redhat.com)
- Adding in a bit of a hack to get the derived pool to show up properly.
  (jharris@redhat.com)
- fixed a bug when an entitlement with quantity greater than 1 didn't update
  consumed property correctly (ddolguik@redhat.com)
- Fix ArrayOutOfBounds exception when candlepin is accessed using only
  username. (anadathu@redhat.com)
- Fix GENDB in deploy script. (dgoodwin@redhat.com)
- Adding TODO for default product adapter. (dgoodwin@redhat.com)
- Product import script output/formatting touchups. (dgoodwin@redhat.com)
- Fixed bug where password logged in the log files. (anadathu@redhat.com)
- Consumers were allowing blank uuids (bkearney@redhat.com)
- Attributes are not being pushed (bkearney@redhat.com)
- Fixing user_restricted feature to move away from using attributes.
  (jharris@redhat.com)
- Upgrade to hornetq 2.1.0 (jbowes@redhat.com)

* Fri Jun 18 2010 Devan Goodwin <dgoodwin@redhat.com> 0.0.18-1
- Add Consumer info to Event system. (dgoodwin@redhat.com)
- Add a UUID into the create if it is null (bkearney@redhat.com)
- Handle change of create_product api, support "multiplier" product data
  (adrian@alikins.usersys.redhat.com)
- Moving multiplier to product from subscription. (jharris@redhat.com)
- Performance fixes for PoolCurator. (dgoodwin@redhat.com)
- Fix a eager fetching bug. (dgoodwin@redhat.com)
- Add a ProductContent model/map, update ProductResource to use it.
  (adrian@alikins.usersys.redhat.com)
- Carry provided Product objects on a Subscription, not just their IDs.
  (dgoodwin@redhat.com)
- Introduce product adapter method to determine top level product.
  (dgoodwin@redhat.com)
- Add support for Subscription providing multiple product IDs.
  (dgoodwin@redhat.com)

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
