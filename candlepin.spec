%global _binary_filedigest_algorithm 1
%global _source_filedigest_algorithm 1
%global _binary_payload w9.gzdio
%global _source_payload w9.gzdio

%global selinux_variants mls strict targeted
%global selinux_policyver %(%{__sed} -e 's,.*selinux-policy-\\([^/]*\\)/.*,\\1,' /usr/share/selinux/devel/policyhelp || echo 0.0.0)
%global modulename candlepin

# This is technically just a temporary directory to get us through
# the compilation phase. It is later destroyed and the spec file will
# re-call initjars with the correct destination for both tomcat and jboss.
%define distlibdir $RPM_BUILD_ROOT/%{_tmppath}/distlibdir/
%define libdir %{_datadir}/java/

# We require the Candlepin SCL, but because we are not an SCL package
# ourselves, we need to point to deps in the expected location.
%define scllibdir /opt/rh/candlepin-scl/root

%if 0%{?fedora}
%define reqcpdeps 1
%endif

%if 0%{?fedora} >= 19
%define tomcat tomcat
%else
%define tomcat tomcat6
%endif

Name: candlepin
Summary: Candlepin is an open source entitlement management system
Group: System Environment/Daemons
License: GPLv2
Version: 0.8.28
Release: 1%{?dist}
URL: http://fedorahosted.org/candlepin
# Source0: https://fedorahosted.org/releases/c/a/candlepin/%{name}-%{version}.tar.gz
Source: %{name}-%{version}.tar.gz
BuildRoot: %{_tmppath}/%{name}-%{version}-%{release}-buildroot
Vendor: Red Hat, Inc.
BuildArch: noarch

BuildRequires: java-devel >= 0:1.6.0
BuildRequires: ant >= 0:1.7.0
BuildRequires: ant-nodeps >= 0:1.7.0
BuildRequires: gettext
BuildRequires: selinux-policy-doc


%if 0%{?reqcpdeps}
%define distlibdir %{_datadir}/%{name}/lib/
%define libdir %{_datadir}/%{name}/lib/
%define usecpdeps "usecpdeps"
BuildRequires: candlepin-deps >= 0:0.1.5
%else
%define usecpdeps ""

# Require the candlepin software collection for packages we use that may
# conflict with other projects/releases:
BuildRequires: scl-utils-build
BuildRequires: candlepin-scl

BuildRequires: bouncycastle
BuildRequires: hibernate3 >= 3.3.2
BuildRequires: hibernate3-annotations >= 0:3.4.0

# for schema
BuildRequires: hibernate3-entitymanager >= 0:3.4.0
BuildRequires: hibernate3-commons-annotations

BuildRequires: google-collections >= 0:1.0
BuildRequires: resteasy >= 0:2.3.1
BuildRequires: hornetq >= 0:2.2.11
BuildRequires: google-guice >= 0:3.0
BuildRequires: log4j
BuildRequires: jakarta-commons-lang
BuildRequires: jakarta-commons-io
BuildRequires: apache-commons-codec
BuildRequires: codehaus-jackson >= 0:1.9.2

# Configure Datasources
BuildRequires: codehaus-jackson-core-lgpl
BuildRequires: codehaus-jackson-mapper-lgpl
BuildRequires: codehaus-jackson-xc
BuildRequires: codehaus-jackson-jaxrs
BuildRequires: jakarta-commons-httpclient
BuildRequires: jpa_1_0_api
BuildRequires: netty
BuildRequires: glassfish-jaxb
BuildRequires: jms >= 0:1.1
BuildRequires: oauth
BuildRequires: slf4j >= 0:1.6.1

# needed to setup runtime deps, not for compilation
BuildRequires: c3p0
BuildRequires: scannotation
BuildRequires: postgresql-jdbc
BuildRequires: servlet
BuildRequires: gettext-commons

# resteasy multipart requires this at runtime
BuildRequires: apache-mime4j

%endif

# Common requires go here
Requires: java >= 0:1.6.0
#until cpsetup is removed
Requires: wget
Requires: liquibase >= 2.0.5
Requires: postgresql-jdbc

# specific requires
# if not using cpdeps, we'll need real requires
%if !0%{?reqcpdeps}
# candlepin webapp requires
Requires: bouncycastle
Requires: hibernate3 >= 3.3.2
Requires: hibernate3-annotations >= 0:3.4.0
Requires: hibernate3-entitymanager >= 0:3.4.0
Requires: candlepin-scl
Requires: c3p0
Requires: resteasy >= 0:2.3.1
Requires: google-guice >= 0:3.0
Requires: codehaus-jackson >= 0:1.9.2
Requires: codehaus-jackson-xc
Requires: codehaus-jackson-core-lgpl
Requires: codehaus-jackson-mapper-lgpl
Requires: codehaus-jackson-jaxrs
Requires: hornetq >= 0:2.2.11
Requires: netty
Requires: oauth
Requires: log4j
Requires: glassfish-jaxb
Requires: scannotation
Requires: slf4j >= 0:1.6.1
Requires: jakarta-commons-lang
Requires: jakarta-commons-io
Requires: apache-commons-codec
Requires: jakarta-commons-httpclient
Requires: google-collections >= 0:1.0
Requires: apache-mime4j
Requires: gettext-commons
%endif
%define __jar_repack %{nil}

%description
Candlepin is an open source entitlement management system.

%package %{tomcat}
Summary: Candlepin web application for tomcat
Requires: %{tomcat}
Requires: candlepin = %{version}

%description %{tomcat}
Candlepin web application for tomcat

%package devel
Summary: Development libraries for candlepin integration
Group: Development/Libraries

%description devel
Development libraries for candlepin integration

%package certgen-lib
Summary: candlepin certgen library for use by other apps

%description certgen-lib
candlepin library for use by other apps


%package selinux
Summary:        SELinux policy module supporting candlepin
Group:          System Environment/Base
BuildRequires:  checkpolicy
BuildRequires:  selinux-policy-devel
BuildRequires:  /usr/share/selinux/devel/policyhelp
BuildRequires:  hardlink

%if "%{selinux_policyver}" != ""
Requires:       selinux-policy >= %{selinux_policyver}
%endif
Requires:       %{name} = %{version}-%{release}
Requires(post):   /usr/sbin/semodule
Requires(post):   /sbin/restorecon
Requires(postun): /usr/sbin/semodule
Requires(postun): /sbin/restorecon

%description selinux
SELinux policy module supporting candlepin

%prep
%setup -q
mkdir -p %{distlibdir}

%build
ant -Dlibdir=%{libdir} -Ddistlibdir=%{distlibdir} -Dscllibdir=%{scllibdir}/%{_datadir}/java/ clean %{usecpdeps} package

cd selinux
for selinuxvariant in %{selinux_variants}
do
  make NAME=${selinuxvariant} -f /usr/share/selinux/devel/Makefile
  mv %{modulename}.pp %{modulename}.pp.${selinuxvariant}
  make NAME=${selinuxvariant} -f /usr/share/selinux/devel/Makefile clean
done
cd -

%install
rm -rf $RPM_BUILD_ROOT
# Create the directory structure required to lay down our files
# common
install -d -m 755 $RPM_BUILD_ROOT/%{_sysconfdir}/%{name}/certs/
install -d -m 755 $RPM_BUILD_ROOT/%{_sysconfdir}/%{name}/certs/upstream/
install -m 644 conf/candlepin-redhat-ca.crt %{buildroot}%{_sysconfdir}/%{name}/certs/upstream/
install -d -m 755 $RPM_BUILD_ROOT/%{_sysconfdir}/%{name}/
install -d -m 755 $RPM_BUILD_ROOT/%{_datadir}/%{name}/
install -m 755 code/setup/cpsetup $RPM_BUILD_ROOT/%{_datadir}/%{name}/cpsetup
install -m 755 code/setup/cpdb $RPM_BUILD_ROOT/%{_datadir}/%{name}/cpdb
touch $RPM_BUILD_ROOT/%{_sysconfdir}/%{name}/%{name}.conf

# tomcat
install -d -m 755 $RPM_BUILD_ROOT/%{_localstatedir}/lib/%{tomcat}/webapps/
install -d -m 755 $RPM_BUILD_ROOT/%{_localstatedir}/lib/%{tomcat}/webapps/%{name}/
install -d -m 755 $RPM_BUILD_ROOT/%{_sysconfdir}/%{tomcat}/
unzip target/%{name}-%{version}.war -d $RPM_BUILD_ROOT/%{_localstatedir}/lib/%{tomcat}/webapps/%{name}/


%if !0%{?reqcpdeps}
#remove the copied jars and resymlink
rm $RPM_BUILD_ROOT/%{_localstatedir}/lib/%{tomcat}/webapps/%{name}/WEB-INF/lib/*.jar
ant -Ddistlibdir=$RPM_BUILD_ROOT/%{_localstatedir}/lib/%{tomcat}/webapps/%{name}/WEB-INF/lib/ -Dscllibdir=%{scllibdir}/%{_datadir}/java/ initjars

%endif
ln -s /etc/candlepin/certs/keystore $RPM_BUILD_ROOT/%{_sysconfdir}/%{tomcat}/keystore

# devel
install -d -m 755 $RPM_BUILD_ROOT/%{_datadir}/%{name}/lib/
install -m 644 target/%{name}-api-%{version}.jar $RPM_BUILD_ROOT/%{_datadir}/%{name}/lib/

# jar
install -d -m 755 $RPM_BUILD_ROOT/usr/share/java
install -m 644 target/%{name}-certgen-%{version}.jar $RPM_BUILD_ROOT/usr/share/java/
ln -s /usr/share/java/candlepin-certgen-%{version}.jar $RPM_BUILD_ROOT/usr/share/java/candlepin-certgen.jar

# /var/lib dir for hornetq state
install -d -m 755 $RPM_BUILD_ROOT/%{_localstatedir}/lib/%{name}

install -d -m 755 $RPM_BUILD_ROOT/%{_localstatedir}/log/%{name}
install -d -m 755 $RPM_BUILD_ROOT/%{_localstatedir}/cache/%{name}

cd selinux
for selinuxvariant in %{selinux_variants}
do
  install -d $RPM_BUILD_ROOT/%{_datadir}/selinux/${selinuxvariant}
  install -p -m 644 %{modulename}.pp.${selinuxvariant} \
    $RPM_BUILD_ROOT/%{_datadir}/selinux/${selinuxvariant}/%{modulename}.pp
done
cd -
/usr/sbin/hardlink -cv $RPM_BUILD_ROOT/%{_datadir}/selinux

%clean
rm -rf $RPM_BUILD_ROOT
rm -rf %{_tmppath}/distlibdir

%post selinux
for selinuxvariant in %{selinux_variants}
do
  /usr/sbin/semodule -s ${selinuxvariant} -i \
    %{_datadir}/selinux/${selinuxvariant}/%{modulename}.pp &> /dev/null || :
done
/sbin/restorecon %{_localstatedir}/cache/thumbslug &> /dev/null || :
/sbin/restorecon -R %{_sysconfdir}/%{name}/ &> /dev/null || :

%postun selinux
if [ $1 -eq 0 ] ; then
  for selinuxvariant in %{selinux_variants}
  do
     /usr/sbin/semodule -s ${selinuxvariant} -r %{modulename} &> /dev/null || :
  done
  /sbin/restorecon %{_localstatedir}/cache/thumbslug &> /dev/null || :
  /sbin/restorecon -R %{_sysconfdir}/%{name}/ &> /dev/null || :
fi


%files
%defattr(-,root,root)
%dir %{_datadir}/%{name}/
%{_datadir}/%{name}/cpsetup
%{_datadir}/%{name}/cpdb
%{_sysconfdir}/%{name}/certs/
%{_sysconfdir}/%{name}/certs/upstream
%ghost %attr(644, root, root) %{_sysconfdir}/%{name}/certs/candlepin-ca.crt
# Default is to track the rpm version of this cert for manifest signatures.
# If a deployment is managing their own, they will need to restore from the
# .rpmsave backup after upgrading the candlepin rpm.
%config %attr(644, root, root) %{_sysconfdir}/%{name}/certs/upstream/candlepin-redhat-ca.crt
%doc LICENSE
%doc README

%files %{tomcat}
%defattr(644,tomcat,tomcat,775)
%{_localstatedir}/lib/%{tomcat}/webapps/%{name}/*
%{_localstatedir}/lib/%{name}/
%{_localstatedir}/log/%{name}
%{_localstatedir}/cache/%{name}
%config(noreplace) %{_sysconfdir}/%{tomcat}/keystore
%defattr(600,tomcat,tomcat,-)
%config(noreplace) %{_sysconfdir}/%{name}/%{name}.conf

%files devel
%defattr(644,root,root,775)
%{_datadir}/%{name}/lib/%{name}-api-%{version}.jar

%files certgen-lib
%defattr(644,root,root,775)
/usr/share/java/%{name}-certgen-%{version}.jar
/usr/share/java/%{name}-certgen.jar

%files selinux
%defattr(-,root,root,0755)
%doc selinux/*
%{_datadir}/selinux/*/%{modulename}.pp


%changelog
* Mon Sep 23 2013 jesus m. rodriguez <jesusr@redhat.com> 0.8.28-1
- 1007836: calculate suggested quantity with only matching stacking_ids (ckozak@redhat.com)

* Thu Sep 12 2013 jesus m. rodriguez <jesusr@redhat.com> 0.8.27-1
- 845600, 996672: fix suggested quantities (ckozak@redhat.com)
- Strings update. (dgoodwin@redhat.com)
- Change brand attribute from 'os' to 'brand_type' (alikins@redhat.com)
- Spec test cleanup (awood@redhat.com)
* Fri Sep 06 2013 jesus m. rodriguez <jesusr@redhat.com> 0.8.26-1
- 1004780: truncate result string to fit in db (jesusr@redhat.com)
- 1002946: Fix entitlement dates not being updated when pool dates change.  (dgoodwin@redhat.com)
- Run spec tests in parallel (awood@redhat.com)
- Numerous spec test changes to work in parallel (awood@redhat.com)
- adding f19 for katello (jesusr@redhat.com)

* Tue Sep 03 2013 jesus m. rodriguez <jesusr@redhat.com> 0.8.25-1
- 994853: Fix installed product date range. (dgoodwin@redhat.com)
- 998317: check delete for null (jesusr@redhat.com)
- 1003079: Allow autobind to select pools with unlimited quantity (mstead@redhat.com)
- Simplify spec test for role listing by user. (alikins@redhat.com)
- Remove double sorting in get installed product date range.  (dgoodwin@redhat.com)
- Fix unlimited stackable pool autobinding, fix unlimited pool quantity (ckozak@redhat.com)
- fix validation of null params (ckozak@redhat.com)
- Add os oid to Product certificates (alikins@redhat.com)
- Added Satellite version 5.6 to distributor versions (wpoteat@redhat.com)

* Wed Aug 28 2013 jesus m. rodriguez <jesusr@redhat.com> 0.8.24-1
- 996925 - Exception while deleting manifest (wpoteat@redhat.com)

* Mon Aug 26 2013 jesus m. rodriguez <jesusr@redhat.com> 0.8.23-1
- 1000444: Fixed find by stack id query (mstead@redhat.com)

* Fri Aug 23 2013 jesus m. rodriguez <jesusr@redhat.com> 0.8.22-1
- 750872: complete partial stacks with no installed products (ckozak@redhat.com)
- 876758 - String Updates: Entitlement -> Subscription updates (wpoteat@redhat.com)
- 998317: NPE in refreshpools prevents refreshing pools (jesusr@redhat.com)
- Pushing latest strings (jesusr@redhat.com)
- Fix autoheal entire org (ckozak@redhat.com)
- Filter distributor versions when querying  (wpoteat@redhat.com)
- Fix bug in get_owner() (awood@redhat.com)

* Wed Aug 21 2013 jesus m. rodriguez <jesusr@redhat.com> 0.8.21-1
- 990639: date ranges for partial products (ckozak@redhat.com)
- 997970: For v1 certs, skip unknown content types (alikins@redhat.com)
- Feature: One sub pool per stack (mstead@redhat.com)
- Increased autobind performance (ckozak@redhat.com)
* Wed Aug 14 2013 Devan Goodwin <dgoodwin@rm-rf.ca> 0.8.20-1
- extract and merge strings (jesusr@redhat.com)
- 994711: protect against consuming other org ents (jesusr@redhat.com)
- dont list expired (ckozak@redhat.com)
- Async binds should have the same behavior as regular binds.
  (awood@redhat.com)
- 988549: Let CandlepinPoolManager decide which products to bind.
  (awood@redhat.com)
- 989698: Attempted fix for hornetq journal errors. (dgoodwin@redhat.com)
- 990728: Refresh Manifest fails when the upstream distributor has all the
  subscriptions removed (wpoteat@redhat.com)
- 990113: '500 Internal Server Error' importing manifest from stage
  (wpoteat@redhat.com)
- Fixed typo in string (cschevia@redhat.com)

* Wed Jul 31 2013 Devan Goodwin <dgoodwin@rm-rf.ca> 0.8.19-1
- Strings update. (dgoodwin@redhat.com)
- Allow calls to /owners/{owner_key}/consumers to accept a list of consumer
  UUIDs. (awood@redhat.com)
- Handle empty or null lists being sent in to the SecurityInterceptor.
  (awood@redhat.com)
- Add method to search by consumer owner and UUID list. (awood@redhat.com)
- Add method to list compliance by consumer UUID. (awood@redhat.com)
- Allow GET /consumers to accept a list of uuids to look up. (awood@redhat.com)
- Remove checks on deleted consumers for bulk verification of consumers.
  (awood@redhat.com)
- Modify @Verify annotation to accept collections. (awood@redhat.com)
- Remove unused EnforceAccessControl annotation. (awood@redhat.com)

* Tue Jul 23 2013 jesus m. rodriguez <jesusr@redhat.com> 0.8.18-1
- 876764: String updates: consumer -> unit (cschevia@redhat.com)
- 914827: Do not insert new consumer types if they are already present. (bkearney@redhat.com)
- misc spec test and doc cleanup (ckozak@redhat.com)
- Updated spec test and syntax in file (cschevia@redhat.com)
- Track owner key and owner displayname on deletedconsumers (cduryee@redhat.com)
- add getComplianceStatus with date, reasons with date (ckozak@redhat.com)
- Make candlepin work on f19 (ckozak@redhat.com)
- allow user defined ruby versions (ckozak@redhat.com)
- Add F19 releaser, drop F16. (dgoodwin@redhat.com)
- set all awesomeos content enabled=0 by default (alikins@redhat.com)
- Fixed update product code (cschevia@redhat.com)
- remove unused method (jesusr@redhat.com)

* Wed Jul 10 2013 Devan Goodwin <dgoodwin@rm-rf.ca> 0.8.17-1
- Strings update. (dgoodwin@redhat.com)
- make deleted consumers call more predictable (jesusr@redhat.com)
- Add cert v3 capability. (awood@redhat.com)
- 980640: fix stacked ent providing logic (ckozak@redhat.com)
- update_product single attribute update support (cschevia@redhat.com)
- 971174: Support for updating products (cschevia@redhat.com)

* Tue Jul 02 2013 jesus m. rodriguez <jesusr@redhat.com> 0.8.16-1
- 837151: Fix activation key including virtual type subscriptions (wpoteat@redhat.com)
- 888866: Old sm can use subs with new attributes (ckozak@redhat.com)
- 971445: fix importing manifest from same distributor for the same org (wpoteat@redhat.com)
- 976089: Expose new api to get entitlements for a pool (bkearney@redhat.com)
- latest strings from zanata (alikins@redhat.com)
- Add Arch.parseArches test cases (alikins@redhat.com)
- Do not create virt sub-pool when a guest binds to main pool. (dgoodwin@redhat.com)
- update class javadoc (jmrodri@gmail.com)
- Allow hypervisor types to also consume system subscriptions. (bkearney@redhat.com)
- Sub-pool data feature (mstead@redhat.com)
- Add support for sub-product data on subs and pools. (dgoodwin@redhat.com)

* Wed Jun 19 2013 Devan Goodwin <dgoodwin@rm-rf.ca> 0.8.15-1
- Latest translations from zanata. (dgoodwin@redhat.com)
- Extract latest strings. (dgoodwin@redhat.com)

* Wed Jun 19 2013 Devan Goodwin <dgoodwin@rm-rf.ca> 0.8.14-1
- make Content arch compares more specific (alikins@redhat.com)
- new deleted_consumers resource (cduryee@redhat.com)
- Added empty hash for the new opts param for JSON parsing.
  (cschevia@redhat.com)
- group if statements and log.debug instead of warn. (jesusr@redhat.com)
- Small fix to arch content filter (wpoteat@redhat.com)
- Add missing capability indexes/fkeys for Oracle. (dgoodwin@redhat.com)
- Fix recent content arch changeset to work on Oracle. (dgoodwin@redhat.com)
- pmd: remove unused parameters (jmrodri@gmail.com)
- pmd: duplicate code: refactor to minimize dupe code (jmrodri@gmail.com)
- remove delay on single job scheduling. (jesusr@redhat.com)
- pmd: duplicate code: removed unused test (jmrodri@gmail.com)
- 966430: Don't suggest quantities we can't actually have.
  (dgoodwin@redhat.com)
- Fix sporadically failing unit test. (awood@redhat.com)
- Push post-filtering logic into AbstractHibernateCurator. (awood@redhat.com)
- 972752: Correct stacked marketing names (ckozak@redhat.com)
- Must set page results to the filtered list. (awood@redhat.com)
- Adding pagination spec tests. (awood@redhat.com)
- Adding a few more assertions to paging tests. (awood@redhat.com)
- Add pagination to listing entitlements for a consumer. (awood@redhat.com)
- Move takeSubList method up to AbstractHibernateCurator. (awood@redhat.com)
- Default page should be 1 not 0. (awood@redhat.com)
- Fix bug in determining last page. (awood@redhat.com)
- Add pagination to pool listings. (awood@redhat.com)
- Add paging to additional resources. (awood@redhat.com)
- Fix Content and Product with no arch. (alikins@redhat.com)
- 971121: Candlepin Lists Derived Pools For Distributors (wpoteat@redhat.com)
- 963535: Fix instance quantity increment of 2 on virt guests.
  (dgoodwin@redhat.com)

* Tue Jun 04 2013 jesus m. rodriguez <jesusr@redhat.com> 0.8.13-1
- heal entire org (jesusr@redhat.com)
- Drop the Arch table/model (alikins@redhat.com)
- Include arches inherited from product on contents (alikins@redhat.com)
- Update the last checkin date in the consumer update json only (wpoteat@redhat.com)
- translate errors for supported calculations (ckozak@redhat.com)

* Thu May 30 2013 jesus m. rodriguez <jesusr@redhat.com> 0.8.12-1
- add paging package to candlepin-api.jar (jesusr@redhat.com)

* Thu May 30 2013 jesus m. rodriguez <jesusr@redhat.com> 0.8.11-1
- pmd: various code clean up (jmrodri@gmail.com)
- Convert new Integer calls to Integer.valueOf(). (awood@redhat.com)
- Rename DataPresentation to PageRequest. (awood@redhat.com)
- Remove checkstyle specific hack. (awood@redhat.com)
- findbugs: don't use != on Strings. (jmrodri@gmail.com)

* Wed May 29 2013 jesus m. rodriguez <jesusr@redhat.com> 0.8.10-1
- Arch-based content sets (alikins@redhat.com)
- Added pagination support (awood@redhat.com)
- Handle specified null list for capabilities (wpoteat@redhat.com)
- Instanced based spec tests (dgoodwin@redhat.com)
- 966069: only stack valid ents (ckozak@redhat.com)
- 959967: calculate installed prods correctly (jesusr@redhat.com)

* Fri May 24 2013 jesus m. rodriguez <jesusr@redhat.com> 0.8.9-1
- 966860: handle older manifests with no id cert (jesusr@redhat.com)
- remove unused variables (jesusr@redhat.com)

* Thu May 23 2013 Devan Goodwin <dgoodwin@rm-rf.ca> 0.8.8-1
- Add support for distributor capabilities. (wpoteat@redhat.com)
- 965310: Fix broken import of identity cert. (dgoodwin@redhat.com)
- use ConfigProperties enums for requesting configs (alikins@redhat.com)
- Ignore unknown properties in rules responses. (dgoodwin@redhat.com)
- get javascript logging working with buildr (jesusr@redhat.com)
- Added version to installed products (ckozak@redhat.com)
- installed info no longer transient (ckozak@redhat.com)
- Fix values for host_limited attribute. (dgoodwin@redhat.com)
- Better method of detecting when to apply instance multiplier.
  (dgoodwin@redhat.com)
- CalculatedAttributesUtil should return a value instead of relying on side-
  effects. (awood@redhat.com)

* Fri May 10 2013 Michael Stead <mstead@redhat.com> 0.8.7-1
- Merge pull request #248 from candlepin/alikins/syntastic_classpath
  (mstead@redhat.com)
- Add buildfile target to generate a .syntastic_class_path (alikins@redhat.com)
- Update generate export script to work on arbitrary owner.
  (dgoodwin@redhat.com)
- latest strings from zanata (alikins@redhat.com)
- Merge pull request #247 from candlepin/zeus/instancebased
  (mstead@redhat.com)
- minor version bump for rules.js (jesusr@redhat.com)
- ensure virt guests are not blocked with odd quantity (jesusr@redhat.com)
- move string to constants (jesusr@redhat.com)
- remove left over System.out (jesusr@redhat.com)
- Merge pull request #246 from candlepin/awood/server-side-quantity (dgoodwin
  @rm-rf.ca)
- Bump version of JS rules. (awood@redhat.com)
- Removing dead JS code. (awood@redhat.com)
- Move security constraints into the Resource layer. (awood@redhat.com)
- Block physical binds with quantities not multiples of the instance
  multiplier. (jesusr@redhat.com)
- Correcting some failing unit tests. (awood@redhat.com)
- Add calls to stackTracker's updateAccumulatedFromEnt. (awood@redhat.com)
- Use CoverageCalculator to determine quantity suggested. (awood@redhat.com)
- Fetch quantity_increment from product attributes. (awood@redhat.com)
- Adding spec test for calculated attributes from owner resource.
  (awood@redhat.com)
- Remove some extra code from pool resource spec test. (awood@redhat.com)
- Remove requirement that consumers must be in a pool to get calculated
  attributes. (awood@redhat.com)
- Adding calculated attributes to OwnerResource. (awood@redhat.com)
- Move calculated attributes out to a separate class. (awood@redhat.com)
- Adding spec test for calculated attributes. (awood@redhat.com)
- Initial attempt at moving quantity calculations into Candlepin.
  (awood@redhat.com)

* Wed May 08 2013 jesus m. rodriguez <jesusr@redhat.com> 0.8.6-1
- Fix rules guest detection. (dgoodwin@redhat.com)
- Virt-limit sub-pool quantity should no longer use entitlement quantity.  (dgoodwin@redhat.com)
- Make manifest rules much faster. (dgoodwin@redhat.com)
- Entitlement rules refactor. (dgoodwin@redhat.com)
- 892696: Turn down the logging so that missing rules are infos instead of
  warns. (bkearney@redhat.com)
- Move coverage adjustment inside a more generic method. (dgoodwin@redhat.com)
- Change assumption about default quantity during autobind.
  (dgoodwin@redhat.com)
- 956367: do not update quantities for host_limited pools (mstead@redhat.com)
- Instance based autobind cleanup. (dgoodwin@redhat.com)
- Do not enforce attributes in some situations. (dgoodwin@redhat.com)
- Autobind correct quantities for instance based subs. (dgoodwin@redhat.com)
- fixed translations (ckozak@redhat.com)
- 958182: Fix the prefix logic to not append hte prefix if the url starts with
  a normal url prefix (bkearney@redhat.com)
  (ckozak@redhat.com)
- changed reason messages, add reason attribute name (ckozak@redhat.com)
- 957218: Require 3.2 certs for cores enabled subscriptions (mstead@redhat.com)
- 956200: Enable the owner default SLA usage if none is provided or defined on
  the consumer (bkearney@redhat.com)
- Bumping rules version to 3.0 (mstead@redhat.com)
- System is partial with partial entitlement and no products (mstead@redhat.com)
- make the next int more random (jesusr@redhat.com)
- fixed compliance calculation (ckozak@redhat.com)
- cleaned up ComplianceStatus constructors (ckozak@redhat.com)
- status is valid if there are no reasons.  This makes the system yellow if
  there's a partial stack (ckozak@redhat.com)
- changed messages slightly again for RAM.  SUB covers xGB of yGB of RAM.
  (reoved word systems to be consistent) (ckozak@redhat.com)
- changed messages slightly for QE (ckozak@redhat.com)
- fixed more styling (ckozak@redhat.com)
- Return compliance status reasons for Compliance namespace (mstead@redhat.com)
- rearranged StatusMreasonMessageGenerator setup helpers to be more generic
  (ckozak@redhat.com)
  StatusReasonMessageGenerator (ckozak@redhat.com)
- fixed getting non-compliant product names (ckozak@redhat.com)
- fixed multiple subscription names (ckozak@redhat.com)
- removed StatusReasonMessageGenerator setter from compliancerules, inject
  instead.  Added slash-separated subscription names in stack
  (ckozak@redhat.com)
- performance improvement on ComplianceRulesTest.  (ckozak@redhat.com)
- marked helper fields xmltransient (ckozak@redhat.com)
- refactored ComplianceReason, added StatusReasonMessageGenerator to help build
  messages (ckozak@redhat.com)
- candlepin accepts reason structures from javascript and builds translated
  messages (ckozak@redhat.com)

* Mon Apr 29 2013 Bryan Kearney <bkearney@redhat.com> 0.8.5-1
- 956873: Fix broken rules on older Candlepin servers. (dgoodwin@redhat.com)
- Add additional EmptyStringInterceptor test. (awood@redhat.com)
- Remove the term 'cnsmr' to the extent possible. (awood@redhat.com)
- Consolidate Oracle dependencies. (awood@redhat.com)
- Refactoring deploy script to remove dependency on external file.
  (awood@redhat.com)
- Add Quartz's Oracle JAR to the buildfile. (awood@redhat.com)
- Add Oracle support to cpsetup. (awood@redhat.com)
- Add Oracle support to cpdb. (awood@redhat.com)
- Add refresh pools support for instance based subscriptions.
  (dgoodwin@redhat.com)
- web and api url transposed. (jesusr@redhat.com)
- Pull in the latest strings (bkearney@redhat.com)
- Updating Oracle schema creation script. (awood@redhat.com)
- Small corrections to deployment script. (awood@redhat.com)
- Add unit tests for EmptyStringUserType. (awood@redhat.com)
- Add unit tests for EmptyStringInterceptor. (awood@redhat.com)
- Require newer version of Liquibase (awood@redhat.com)
- Add Oracle as a deployment option. (awood@redhat.com)
- Set empty string values in the database to null with liquibase.
  (awood@redhat.com)
- For Content objects, read content and GPG URLs stored as null as the empty
  string. (awood@redhat.com)
- Adding Hibernate interceptor to prevent writing empty strings to the
  database. (awood@redhat.com)
- Add UserType that will convert nulls to empty strings on database reads.
  (awood@redhat.com)
- Removing code that is a no-op in Oracle. (awood@redhat.com)
- Handle null Content paths when writing to a V3 certificate.
  (awood@redhat.com)
- The name 'fk_product_id' was being used twice. (awood@redhat.com)
- The word 'access' is an Oracle reserved word. (awood@redhat.com)
- Shorten cp_consumer_installed_products table name to less than 30 characters.
  (awood@redhat.com)
- Add a comment explaining the consequences of using HBM2DDL for Oracle.
  (awood@redhat.com)
- Allow the ownerId in the cp_event table to be null. (awood@redhat.com)
- Create the Oracle schema and reconcile the PostgreSQL schema.
  (awood@redhat.com)
- Add upstream consumer foreign key. (awood@redhat.com)
- Configure existing changesets to run only on PostgreSQL. (awood@redhat.com)

* Thu Apr 18 2013 jesus m. rodriguez <jesusr@redhat.com> 0.8.4-1
- also copy over created/updated. (jesusr@redhat.com)
- Bumped minor version of the rules. (mstead@redhat.com)
- typo: Chagned -> Changed (jmrodri@gmail.com)
- 949684: Fix unit test failures due to copy paste error when refactoring the
  code for checkstyle (bkearney@redhat.com)
- 949684: Update the contract information on pools when subcriptions changed.
  (bkearney@redhat.com)
- ensure null/empty contentPrefix handled. remove endless loop.
  (jmrodri@gmail.com)
- Adding test data for multi-attribute stacking (mstead@redhat.com)
- When determining coverage skip prod attributes not set. (mstead@redhat.com)
- Properly track arch on stacks and calculate compliance. (mstead@redhat.com)
- In pre_cores use FactValueCalculator to get consumer cores.
  (mstead@redhat.com)
- Removed debugging debug statements that were cluttering logs.
  (mstead@redhat.com)
- 907315: Added capability to stack on RAM. (mstead@redhat.com)
- Moved rules namespaces to top of file (mstead@redhat.com)
- Added comments to rules file. (mstead@redhat.com)
- A product attribute that has a value of '0' is considered not set.
  (mstead@redhat.com)
- Adding cores fact calculation to FactValueCalculator (mstead@redhat.com)
- Unit and spec tests with corrections to code (wpoteat@redhat.com)
- Properly support multi-attribute compliance/stacking (mstead@redhat.com)
- Include cores when finding stacking pools (mstead@redhat.com)
- Basic cores check for entitlements (mstead@redhat.com)
- 952735: Add additional checks with content prefixes with many trailing / and
  content urls with many leading / (bkearney@redhat.com)
- 952735: Ensure that prefixes plus content urls do not result in double
  slashes (bkearney@redhat.com)
- 950462: do not expect numa cpu list to be an int (alikins@redhat.com)
- only get latest import record (jesusr@redhat.com)

* Tue Apr 16 2013 jesus m. rodriguez <jesusr@redhat.com> 0.8.3-1
- Hard code product attribute separator into rules.js (ckozak@redhat.com)
- Change to use correct REST call (wpoteat@redhat.com)
- fix parsing of entitlements with multiple architectures (ckozak@redhat.com)
- 928045: delete excessive fails to remove all excessive entitlements (wpoteat@redhat.com)
- 952681: look at upstream_name *NOT* upstream_id (jesusr@redhat.com)
- Updating the tests to be more robust in Hudson (wpoteat@redhat.com)
- Removing the Cert V3 Enable flag from configuration (wpoteat@redhat.com)
- False the boolean not "False" the string (jesusr@redhat.com)
- add relink option (jesusr@redhat.com)

* Fri Apr 05 2013 jesus m. rodriguez <jesusr@redhat.com> 0.8.2-1
- remove Fedora 16 and Fedora 17 releaser for Katello (msuchy@redhat.com)
- require candlepin-deps 0.1.5 or greater (jesusr@redhat.com)
- Proper comparison between 2 strings of json data (wpoteat@redhat.com)
- 909467: Now checks stacked entitlements.  Added tests (ckozak@redhat.com)
- fix scl deps in spec file (cduryee@redhat.com)
- fix file docstring (jesusr@redhat.com)
- A developer script used to attach idcert to upstream consumer (jesusr@redhat.com)
- 909467: warning on architecture mismatch (ckozak@redhat.com)
- Updates to manifest data. (wpoteat@redhat.com)
- findbugs: make inner classes static (jmrodri@gmail.com)
- findbugs: remove unread field: poolManager (jmrodri@gmail.com)
- findbugs: Possible null pointer dereference of user (jmrodri@gmail.com)
- findbugs: implement equals() when implementing compareTo (jmrodri@gmail.com)
- Changes to database update scripts (wpoteat@redhat.com)
- 914717: rct cat-manifest fails to report Contract from the embedded
  entitlement cert (wpoteat@redhat.com)

* Mon Apr 01 2013 William Poteat <wpoteat@redhat.com> 0.8.1-1
- Enable host to guest mapping for hosted mode via an attribute.
  (awood@redhat.com)
- Updates can now be emitted from RulesImporter (uploaded manifests).  Events
  now stored in the database (fixed bug with non-nullable fields set null)
  (ckozak@redhat.com)
- New signature checking for manifests (wpoteat@redhat.com)
- 916467: disable update checks in quartz (jesusr@redhat.com)

* Wed Mar 13 2013 Devan Goodwin <dgoodwin@rm-rf.ca> 0.8.0-1
- Introduce candlepin software collection. (dgoodwin@rm-rf.ca)
- converted != to equals instead of !...equals (jesusr@redhat.com)
- findbugs: Suspicious comparison of Long references (jesusr@redhat.com)
- New versioned rules v2 implementation. (dgoodwin@redhat.com / mstead@redhat.com)
- Removed ReadOnly* objects as they are no longer used in rules
  (mstead@redhat.com)
- Change of Autobind namespace to use JSON objects (wpoteat@redhat.com)
- Return JSON from PreEntitlement rules. (mstead@redhat.com)
- Entitlement rules namespace now supports JSON in (mstead@redhat.com)
- Add rules version to server status API. (dgoodwin@redhat.com)
- Move select best pools to it's own Autobind namespace. (dgoodwin@redhat.com)
- Bump rhino requirement to 0.7R3. (dgoodwin@redhat.com)
- Moved Pool rules namespace back to Java code (mstead@redhat.com)
- Move export/criteria rules to Java. (dgoodwin@redhat.com)
- Move consumer delete namespace back to java. (dgoodwin@redhat.com)
- More post-bind/unbind logic back into Java. (dgoodwin@redhat.com)
- Define better filters on model objects. (dgoodwin@redhat.com)
- Filter timestamps for attributes in rules serialization.
  (dgoodwin@redhat.com)
- Add support for skipping attributes when serializating for rules.
  (dgoodwin@redhat.com)
- Rename SkipExport to ExportExclude for consistency. (dgoodwin@redhat.com)
- Export both new and old rules. (dgoodwin@redhat.com)
- Import specific rules file, not any. (dgoodwin@redhat.com)
- Add versioning of rules files. (dgoodwin@redhat.com)

* Fri Mar 08 2013 jesus m. rodriguez <jesusr@redhat.com> 0.7.29-1
- pair down the classes that go in jar to match buildr generated jar (jesusr@redhat.com)

* Fri Mar 08 2013 jesus m. rodriguez <jesusr@redhat.com> 0.7.28-1
- get resteasy and jackson classes in candlepin-api.jar of rpm (jesusr@redhat.com)

* Thu Mar 07 2013 William Poteat <wpoteat@redhat.com> 0.7.27-1
- Update of zanata strings (wpoteat@redhat.com)
- add packages to candlepin_api (dcrissma@redhat.com)
- Make JsonProvider more re-usable (dcrissma@redhat.com)
- increase test coverage (jmrodri@gmail.com)

* Fri Mar 01 2013 jesus m. rodriguez <jesusr@redhat.com> 0.7.26-1
- 909495: Virt-only subscriptions are not exportable (jesusr@redhat.com)
- add F18 support (jesusr@redhat.com)
- 908671: Add pool ID to entitlement certificate. (awood@redhat.com)
- Improve the performance is checking for guests for a given host. (bkearney@redhat.com)

* Thu Feb 14 2013 William Poteat <wpoteat@redhat.com> 0.7.25-1
- 908483: Add consumer types for katello. (bkearney@redhat.com)
- 886726: Add translation calls to two exceptions which can be thrown during
  import (bkearney@redhat.com)
- 906438: prevent saving status result from killing job (jmrodri@gmail.com)
- 887113: Katello adds subscriptions twice (wpoteat@redhat.com)
- Method for updating manifests in distributor systems (wpoteat@redhat.com)
- 902804: Do not include new consumer in result.updated (mstead@redhat.com)
- 902804: Properly init consumer guestIds to empty (mstead@redhat.com)
- 864605: Add cores to subscription information (wpoteat@redhat.com)
- 877007: String Updates - Product string cleanups (wpoteat@redhat.com)
- Ability to make 'relies on' relationships between products
  (wpoteat@redhat.com)
- 876758: String Updates: Entitlement -> Subscription updates
  (wpoteat@redhat.com)

* Wed Jan 23 2013 William Poteat <wpoteat@redhat.com> 0.7.24-1
- 892027: OID Order namespace reflects subscription ID rather than Order Number
  (wpoteat@redhat.com)
- Removed DoubleCheckedLocking checkstyle module. (dgoodwin@redhat.com)
- 875940:  String Updates: distributor -> subscription management application
  (wpoteat@redhat.com)
- allow to build F18 in Koji (msuchy@redhat.com)
- Facts and Attributes should be non-negative instead of positive
  (wpoteat@redhat.com)
- 835977: Re-enable manifest signature checking. (dgoodwin@redhat.com)
- 803757: Users should not be able to enter anything other than positive
  integers for sockets 858286: Type checking for product attributes
  (wpoteat@redhat.com)
- 886211: Fix duplicate pinsetter jobs on every config change.
  (dgoodwin@redhat.com)
- 873808: Weird strings featuring '[' in CP from OwnerResource.java
  (wpoteat@redhat.com)
- 889512: Look for getters which use getProperty and isProperty
  (bkearney@redhat.com)
- 873776: Duplicate String: Candlepin has messages for "No such environment :
  {0}" and "No such environment: {0}" (wpoteat@redhat.com)
- 888849: Fixed stalled jobs on async bind (mstead@redhat.com)
- 888035: Update messages for invalid certificates. (mstead@redhat.com)
- 886211: Add transactional annotations for CrlGenerator task.
  (dgoodwin@redhat.com)
- 887287: Detect when virt_limit is removed from subscriptions.
  (dgoodwin@redhat.com)
- 721282: Enhance the documentation delivered with the rpm.
  (bkearney@redhat.com)
- 858759: Do not remove guest virt ents when no host required
  (mstead@redhat.com)

* Thu Dec 13 2012 Devan Goodwin <dgoodwin@rm-rf.ca> 0.7.23-1
- 886211: Fix a deadlock in mysql. (dgoodwin@redhat.com)
- 884973: Make guest/host UUID comparisons case insensitive.
  (dgoodwin@redhat.com)

* Tue Dec 11 2012 jesus m. rodriguez <jesusr@redhat.com> 0.7.22-1
- 885857: Fix missing dependencies in non-cpdeps builds. (dgoodwin@redhat.com)
- 837655: no longer bundle dependencies
- 840086: no longer bundle dependencies
- Cert V3 path tree condensing not properly assessing equivalent path nodes (wpoteat@redhat.com)
- 884694: Fix import of manifests into older candlepin. (dgoodwin@redhat.com)
- Adding exception class inclusion to build.xml as well (cduryee@redhat.com)
- Filering -> Filtering typo (jesusr@redhat.com)
- Overconsumption check performance fix. (dgoodwin@redhat.com)
- Add a setter for ID on ProductContent, and a buildfile fix (cduryee@redhat.com)
- 879022: Add message for too many content sets when V3 is disabled.  (mstead@redhat.com)
- Revert "Move auth related interceptors to their own subpackage" (dgoodwin@redhat.com)
- remove redudant imports (jesusr@redhat.com)
- Move auth related interceptors to their own subpackage (jbowes@redhat.com)

* Fri Nov 30 2012 William Poteat <wpoteat@redhat.com> 0.7.21-1
- Add back missing todo (jbowes@redhat.com)
- Alter entitlement quantites (wpoteat@redhat.com)
- add LICENSE file to candlepin rpm (jmrodri@gmail.com)
- 877697: Localize the GoneException. (bkearney@redhat.com)
- adding license file (jmrodri@gmail.com)
- 879022: Fix too many V1 content sets across multiple products.
  (dgoodwin@redhat.com)
- Fix symlinking jars message in build.xml (jbowes@redhat.com)
- 873655: Don't bundle the jar deps (jbowes@redhat.com)
- Refactored ProductVersionValidator to require product attributes.
  (mstead@redhat.com)
- 830896: Improve error detection and handling of manifest import
  (wpoteat@redhat.com)
- 874785: null pointer while migrating owner (jesusr@redhat.com)
- selectBestPools now filters any pools whos versions are not supported
  (mstead@redhat.com)
- Add script to clean up the content ID changed breakage. (dgoodwin@redhat.com)
- 874041: fix for a performance issue selecting the set of SLA's for an owner.
  (bkearney@redhat.com)
- Sync system RAM calculation with that of the client (mstead@redhat.com)
- Implemented Autobind/Heal for RAM products (mstead@redhat.com)
- Consider RAM when determining status (mstead@redhat.com)
- Rethrow CertVersionConflictException so it is visible to callers
  (mstead@redhat.com)
- Only check config option when checking server cert support.
  (mstead@redhat.com)
- Removed RAM from test product as it is no longer supported.
  (mstead@redhat.com)
- Removed stackable RAM (no longer supporting) (mstead@redhat.com)
- Ensure RAM certs can not be created with V3 disabled (mstead@redhat.com)
- Check cert version of consumer against ram (mstead@redhat.com)
- Added ram attribute to V3 certificates (mstead@redhat.com)

* Fri Nov 30 2012 William Poteat <wpoteat@redhat.com>
- Alter entitlement quantites (wpoteat@redhat.com)
- add LICENSE file to candlepin rpm (jmrodri@gmail.com)
- 877697: Localize the GoneException. (bkearney@redhat.com)
- adding license file (jmrodri@gmail.com)
- 879022: Fix too many V1 content sets across multiple products.
  (dgoodwin@redhat.com)
- Fix symlinking jars message in build.xml (jbowes@redhat.com)
- 873655: Don't bundle the jar deps (jbowes@redhat.com)
- Refactored ProductVersionValidator to require product attributes.
  (mstead@redhat.com)
- 830896: Improve error detection and handling of manifest import
  (wpoteat@redhat.com)
- 874785: null pointer while migrating owner (jesusr@redhat.com)
- selectBestPools now filters any pools whos versions are not supported
  (mstead@redhat.com)
- Add script to clean up the content ID changed breakage. (dgoodwin@redhat.com)
- 874041: fix for a performance issue selecting the set of SLA's for an owner.
  (bkearney@redhat.com)
- Sync system RAM calculation with that of the client (mstead@redhat.com)
- Implemented Autobind/Heal for RAM products (mstead@redhat.com)
- Consider RAM when determining status (mstead@redhat.com)
- Added spec tests for RAM limiting cert creation (mstead@redhat.com)
- Rethrow CertVersionConflictException so it is visible to callers
  (mstead@redhat.com)
- Added test product with both sockets and ram (mstead@redhat.com)
- Only check config option when checking server cert support.
  (mstead@redhat.com)
- Removed RAM from test product as it is no longer supported.
  (mstead@redhat.com)
- Removed stackable RAM (no longer supporting) (mstead@redhat.com)
- Ensure RAM certs can not be created with V3 disabled (mstead@redhat.com)
- Check cert version of consumer against ram (mstead@redhat.com)
- Added ram attribute to V3 certificates (mstead@redhat.com)

* Thu Nov 08 2012 William Poteat <wpoteat@redhat.com> 0.7.19-1
- Performance improvements around anonymous entry points. (bkearney@redhat.com)
- Improve performance of subscribe action in standalone mode.
  (bkearney@redhat.com)

* Fri Nov 02 2012 William Poteat <wpoteat@redhat.com> 0.7.18-1
- Add findbugs target (alikins@redhat.com)
- Add a build target to build candlepin as a jar, so it can be used by other
  apps. Also, create a new rpm for the jar. (cduryee@redhat.com)
- allow to build in katello koji (msuchy@redhat.com)

* Mon Oct 29 2012 jesus m. rodriguez <jesusr@redhat.com> 0.7.17-1
- Allow retrieval of upstream subscription certificate via entitlement id
  (wpoteat@redhat.com)
- variables should point to correct paths (jesusr@redhat.com)
- Properly rename recursiveCombination to powerSet (jbowes@redhat.com)
- Ignore old rules in database after a Candlepin upgrade. (dgoodwin@redhat.com)
- 820630: Fix the pa.po translations where a trailing \ was added.
  (bkearney@redhat.com)
- 820630: Replace the string uuid with UUID (bkearney@redhat.com)
- Add a test for consumer updated timestamp on bind. (dgoodwin@redhat.com)
- Fix a bug with distributor manifest conflicts. (dgoodwin@redhat.com)
- 864508: Service level {0} is not available to consumers (wpoteat@redhat.com)

* Mon Oct 15 2012 Devan Goodwin <dgoodwin@rm-rf.ca> 0.7.16-1
- 860773: Create import records when manifests are deleted.
  (bkearney@redhat.com)
- Slight text improvement for same manifest conflict message.
  (dgoodwin@redhat.com)

* Thu Oct 11 2012 Devan Goodwin <dgoodwin@rm-rf.ca> 0.7.15-1
- Revert bad request on unsuccessful autobind. (dgoodwin@redhat.com)

* Thu Oct 11 2012 Devan Goodwin <dgoodwin@rm-rf.ca> 0.7.14-1
- Add new mechanism for detecting/overriding import conflicts.
  (dgoodwin@redhat.com)
- Allow updating consumer names (jbowes@redhat.com)

* Wed Oct 10 2012 William Poteat <wpoteat@redhat.com> 0.7.13-1
- 863518: put unitOfWork.end() in a finally block (jesusr@redhat.com)
- Save the name of the import file uploaded in the event history
  (wpoteat@redhat.com)
- 857494: Add DB password to liquibase command. (awood@redhat.com)
- Added candlepin.enable_cert_v3 config property. (mstead@redhat.com)
- 800145: Update pools across all owners on product import (jbowes@redhat.com)
- 857918: Add quotes around the invalid service level (bkearney@redhat.com)
- changed ContentResource#update to use contentId in the resource path
  (dmitri@redhat.com)
- Return a proper response when asking to subscribe to an product with no pool
  (brad@redhat.com)

* Thu Oct 04 2012 Devan Goodwin <dgoodwin@rm-rf.ca> 0.7.12-1
- added support for updating of Content (dmitri@redhat.com)
- Remove botched curator package move. (jbowes@redhat.com)
- fixed Release object serialization issue - 'id' field shouldn't be serialized
  (dmitri@redhat.com)

* Wed Sep 19 2012 jesus m. rodriguez <jesusr@redhat.com> 0.7.11-1
- certv3: sort path names alphabetically (jbowes@redhat.com)
- latest strings from zanata (alikins@redhat.com)

* Wed Sep 19 2012 jesus m. rodriguez <jesusr@redhat.com> 0.7.10-1
- 858286: don't generate detached cert data for certv1 (jbowes@redhat.com)
- Use proper unix style line endings in our pem encoding (jbowes@redhat.com)
- Improve logging for poolcurator failures (jbowes@redhat.com)
- certv3: store payload and sig in the cert column (jbowes@redhat.com)
- 857494: Allow cpdb to accept a password from the command line.  (awood@redhat.com)
- add builder.mock_args to rhel releaser (jesusr@redhat.com)
- add reqcpdeps macro and set reqcpdeps to 1 when building fedora (jesusr@redhat.com)
- We need java-devel to build, not java (jbowes@redhat.com)
- certv3: start counting from 1 for node weights (jbowes@redhat.com)
- ProductCache: configure max prods, limit number of products (mstead@redhat.com)
- Further update to make all references and names V3 (wpoteat@redhat.com)
- wrap pool results in a isDebugEnabled() (alikins@redhat.com)
- certv3: encode tree for URL, update compression, build huffman tries (wpoteat@redhat.com)
* Wed Sep 12 2012 Alex Wood <awood@redhat.com> 0.7.9-1
- Fix and clarify performance script CLI help. (dgoodwin@redhat.com)
- Remove some useless and verbose JS logging. (dgoodwin@redhat.com)
- Add some CLI options for performance dataload script. (dgoodwin@redhat.com)
- Improvements for the performance test script. (dgoodwin@redhat.com)
- Let refresh_pools finish in job_status_spec (jbowes@redhat.com)
- add some javadoc for RulesCriteria (alikins@redhat.com)
- unneeded comment removed (alikins@redhat.com)
- checkstyle cleanup (alikins@redhat.com)
- pass in the hostConsumer instead of consumerCurator (alikins@redhat.com)
- spec files fixes for entitlement bind changes (alikins@redhat.com)
- fix spec expecting a http error, now expect null (alikins@redhat.com)
- Fix criteria rules when guest has no registered host consumer.
  (dgoodwin@redhat.com)
- revert unneeded changes (alikins@redhat.com)
- clean up (alikins@redhat.com)
- Fix test cases for cases where criteria filter all pools (alikins@redhat.com)
- Add criteria create for requires_host (alikins@redhat.com)
- Add a pysical pool to virtOnlyProductAttributeFiltering (alikins@redhat.com)
- properly init rulesLogger (alikins@redhat.com)
- Pass in a ConsumerCurator so we can look up consumers (alikins@redhat.com)
- move rules criteria into a Consumer null check (alikins@redhat.com)
- Fix a pool criteria test to fail until we implement. (dgoodwin@redhat.com)
- Tests for PoolCriteria rules. (dgoodwin@redhat.com)
- Fix the criteria to match against Pool from main criteria
  (alikins@redhat.com)
- Drop pure js filters, move to db criteria based filtering
  (alikins@redhat.com)
- Add js support for pool filtering (alikins@redhat.com)

* Fri Aug 31 2012 jesus m. rodriguez <jesusr@redhat.com> 0.7.8-1
- Pull curator classes out into their own package. (jbowes@redhat.com)
- Fix certv1 content filtering on autobinds. (dgoodwin@redhat.com)
- Performance fix for select best pools. (dgoodwin@redhat.com)
- Removed unnecessary comments (mstead@redhat.com)
- Adding comments to classes. (mstead@redhat.com)
- Removed unnecessary product lookup for RO Pool provided products.  (mstead@redhat.com)
- Added distinct to service level for owner query (mstead@redhat.com)
- Improved query for retreiving service levels for owner. (mstead@redhat.com)
- remove unnecessary assignment to null (jesusr@redhat.com)
- Performance improvements when selecting best pools (mstead@redhat.com)
- various findbugs cleanup (jesusr@redhat.com)
- Don't use the real /etc/candlepin/candlepin.conf during testing (jbowes@redhat.com)
- Add null check for Entitlement.getProductId (alikins@redhat.com)

* Tue Aug 28 2012 Alex Wood <awood@redhat.com> 0.7.7-1
- 851512: add restorecon -R to %%post (alikins@redhat.com)
- 851512: add certs_rw and candlepin-ca.certs file context (alikins@redhat.com)
- remove FileUtils.cp calls used for debugging (jesusr@redhat.com)
- ownerinfo: replace pool iteration with hql queries (jbowes@redhat.com)
- ownerinfo: use pool.getProductAttribute rather than the product adapter
  (jbowes@redhat.com)
- remove ownerinfo.poolNearestToExpiry; it's not used. (jbowes@redhat.com)
- Add ability to remove a cert from the certificate revocation list (CRL).
  (jesusr@redhat.com)
- Bind a LoggingConfig here so we set log levels for tests (alikins@redhat.com)
- Add API method to refresh pools for owner of specific products.
  (awood@redhat.com)
- Renaming method for getting owners of active products. (awood@redhat.com)
- 842450: Fix newline issues in candlepin translations (bkearney@redhat.com)
- First draft of script to mass load data for performance testing.
  (dgoodwin@redhat.com)

* Mon Aug 13 2012 William Poteat <wpoteat@redhat.com> 0.7.6-1
- Disable certificate v2 ability (wpoteat@redhat.com)
- Change apidiff to format with python json.tool (alikins@redhat.com)

* Wed Aug 08 2012 William Poteat <wpoteat@redhat.com> 0.7.5-1
- Update to the .po files (wpoteat@redhat.com)
- 732538: Fix error message adding pools to activation keys.
  (dgoodwin@redhat.com)
- Remove total sub count and total subs consumed from owner info.
  (dgoodwin@redhat.com)
- Reduce amount of data in GET /consumers. (dgoodwin@redhat.com)
- 832528: New API method to return all owners of specified products.
  (awood@redhat.com)
- Allow server to create version 2 certificates when requested.
  (wpoteat@redhat.com)

* Fri Jul 27 2012 jesus m. rodriguez <jesusr@redhat.com> 0.7.4-1
- add f17 to releasers (jesusr@redhat.com)
- Add indexes for all foreign keys (bkearney@redhat.com)
- apidoc: add options for template selection, and offline mode (jbowes@redhat.com)
- Update string catalog (alikins@redhat.com)
- Added requirement of rubygems to lint.rb & apidoc.rb (mstead@redhat.com)
- apidoc: add a base template like the website (jbowes@redhat.com)
- Add buildr targets for api doc and lint (jbowes@redhat.com)
- Add an apidoc lint script (jbowes@redhat.com)
- Add summary and return description to apidoc (jbowes@redhat.com)
- Add apidoc/apidoc.rb script to generate (ugly) html apidocs (jbowes@redhat.com)
- Fix 'class variable access from toplevel' spec warning (jbowes@redhat.com)
- Improve performance of getting the owner info. (bkearney@redhat.com)
- tools.jar not needed for 'doc' after client split (alikins@redhat.com)
- Make i18nProvider share its cache across threads (jbowes@redhat.com)
- Remove the java client code. It has its own repo now. (jbowes@redhat.com)
- Utility cpsetup now runs also without sudo (lzap+git@redhat.com) (cduryee@redhat.com)
- 835161: regenerate on-disk CRL on requests to crl resource (cduryee@redhat.com)
- Make zanata.xml more inline with upstream (alikins@redhat.com)
- Fix up test case for new buildr (jbowes@redhat.com)
- Remove clobbering of SLA level case (jbowes@redhat.com)
- make deploy script work without a DBPASSWORD passed to it (alikins@redhat.com)
- getString strips the prefix, expect in the test (alikins@redhat.com)
- Add support for passing a DBUSER/DBPASSWORD (alikins@redhat.com)
- Handle katello-passwd style obscured passwords. (alikins@redhat.com)

* Tue Jun 26 2012 James Bowes <jbowes@redhat.com> 0.7.3-1
- 834684: minor changes to checkstyle.xml to bring it up to date with
  Checkstyle 5.5 (dmitri@redhat.com)
- 834684: updated bundler to version 1.4.7 (dmitri@redhat.com)
- 834591: 0 or no sockets count as infinite on products (jbowes@redhat.com)
- 827035: regenerate identity certificate if within threshold
  (jesusr@redhat.com)
- 804555: The entire prefix for an owner should be url encoded.
  (bkearney@redhat.com)
- 820630: Update some typos which have been found (bkearney@redhat.com)
- Support for lazy regeneration of entitlement certificates.
  (dgoodwin@redhat.com, awood@redhat.com)
- Support for editing of consumer environment (dmitri@appliedlogic.ca)
- sync: keep recursed consumer json out of entitlements (jbowes@redhat.com)
  (awood@redhat.com)

* Wed Jun 06 2012 Chris Duryee (beav) <cduryee@redhat.com>
- latest strings from zanata (cduryee@redhat.com)
- remove unused class. dgoodwin removed references in a previous commit.
  (jesusr@redhat.com)

* Mon Jun 04 2012 Chris Duryee (beav) <cduryee@redhat.com>
- Capture additional data in manifest files from originating Candlepin
  (wpoteat@redhat.com)
- Support for partial owner updates. (wpoteat@redhat.com)
- Quartz 2 merge (cduryee@redhat.com)
- clear the values of Calender before creating a Date. (jesusr@redhat.com)
- unit test DateRange (jesusr@redhat.com)
- unit testing utility class (jesusr@redhat.com)
- unit test filter (jesusr@redhat.com)

* Wed May 23 2012 Devan Goodwin <dgoodwin@rm-rf.ca> 0.6.5-1
- 821532: Fix db create error from previous upgrade fix. (dgoodwin@redhat.com)

* Wed May 23 2012 Chris Duryee (beav) <cduryee@redhat.com>
- 821532: if keystore already exists, do not overwrite (cduryee@redhat.com)
- 821532: Add db upgrade script for owner default SLA. (dgoodwin@redhat.com)
- 818473: Fedora releases require liquibase and postgresl-jdbc
  (jesusr@redhat.com)
- various findbugs fixes (jesusr@redhat.com)
* Wed May 16 2012 jesus m. rodriguez <jesusr@redhat.com> 0.6.3-1
- remove unused signature verification call for now (cduryee@redhat.com)
- findbugs: Field isn't final but should be, made private instead.
  (jesusr@redhat.com)
- findbugs: Write to static field from instance method (jesusr@redhat.com)
- findbugs: Suspicious reference comparison of Boolean values
  (jesusr@redhat.com)
- findbugs: Method invokes inefficient Number ctor; use valueOf instead
  (jesusr@redhat.com)
- findbugs: Unread field should be static (jesusr@redhat.com)
- findbugs: Dead store to local variable (jesusr@redhat.com)
- findbugs: No relationship between generic parameter and method argument
  (jesusr@redhat.com)
- findbugs: redundant comparison to null (jesusr@redhat.com)
- findbugs: possible null pointer dereference (jesusr@redhat.com)
- Fix deploy check in buildfile for f17 (jbowes@redhat.com)
- F17 fixup: disable cert verification differently (cduryee@redhat.com)
- Let running jobs finish before shutting down pinsetter (cduryee@redhat.com)
- Fix an equals issue with manifest import distributor check.
  (dgoodwin@redhat.com)
- unbindAll should return a JSON object. (awood@redhat.com)
- Fixups for F17/ruby 1.9 (jbowes@redhat.com)
- Make return list of service levels by owner all caps. (wpoteat@redhat.com)

* Fri May 04 2012 jesus m. rodriguez <jesusr@redhat.com> 0.6.2-1
- require apache-mime4j (jesusr@redhat.com)
- Remove unused import to silence Checkstyle. (awood@redhat.com)
- 812388: Return the number of entitlements removed or revoked.
  (awood@redhat.com)

* Thu May 03 2012 jesus m. rodriguez <jesusr@redhat.com> 0.6.1-1
- remove wideplay persist (jesusr@redhat.com)
- bump version and use correct dist-git branch (jesusr@redhat.com)
- 817323: Fix race condition on pools for entitlement deletion (jbowes@redhat.com)
- Add a null check for consumer.setReleaseVer (alikins@redhat.com)
- 818040: rhsm release combobox doesn't reset release now (alikins@redhat.com)
- slf4j 1.6.1 required to run. (jesusr@redhat.com)
- use dist.lib to compile against as well (jesusr@redhat.com)
- require gettext-commons (jesusr@redhat.com)
- use correct servlet.jar (jesusr@redhat.com)
- require glassfish-jaxb since jaxb_api doesn't work (jesusr@redhat.com)
- remove commons-pool and commons-dbcp (jesusr@redhat.com)
- buildrequires netty and jaxb_api (jesusr@redhat.com)
- remove beanutils (jesusr@redhat.com)
- require apache-commons-codec instead of jakarta-commons-codec (jesusr@redhat.com)
- allow use of cpdeps for fedora (jesusr@redhat.com)
- use different locations for building on fedora vs rhel (jesusr@redhat.com)
- remove freemarker from build.xml (jesusr@redhat.com)
- remove the distlibdir at start (jesusr@redhat.com)
- fix hibernate3-commons-annotation location (jesusr@redhat.com)
- remove genschema from spec file (jesusr@redhat.com)
- resource should use guice persist (jesusr@redhat.com)
- remove commented freemarker (jesusr@redhat.com)
- remove schema generation (jesusr@redhat.com)
- use candlepin-deps in fedora (jesusr@redhat.com)
- remove mockito (jesusr@redhat.com)
- put in versions for Requires: (jesusr@redhat.com)
- rpmlint: remove period from summary (jesusr@redhat.com)
- fix up comments (jesusr@redhat.com)
- enforce a quartz that's good enough (jesusr@redhat.com)
- format dist.lib (jesusr@redhat.com)
- forgot jaxrs-api from resteasy (jesusr@redhat.com)
- oops forgot jpa and jboss-logging (jesusr@redhat.com)
- Messed up the conflict fix (jesusr@redhat.com)
- use new distlibdir for build.xml packaging (jesusr@redhat.com)
- comment hibernate-tools (jesusr@redhat.com)
- use jpackage to copy jars for war (jesusr@redhat.com)
- update buildrequires and requires (jesusr@redhat.com)
- remove commons-cli (jesusr@redhat.com)
- don't include the world, just certain jars. (jesusr@redhat.com)
- require: scannotation and bouncycastle (jesusr@redhat.com)
- remove qpid from spec and build.xml (jesusr@redhat.com)
- upgrade jackson to match buildfile (jesusr@redhat.com)
- requires: jaxb_api (jesusr@redhat.com)
- remove ldap (jesusr@redhat.com)
- comment out freemarker for now (jesusr@redhat.com)
- fix up jars for hibernate-tools (jesusr@redhat.com)
- genschema: hibernate-commons-annotations is required (jesusr@redhat.com)
- add jpa to genschema (jesusr@redhat.com)
- cleanup genschema classpath (jesusr@redhat.com)
- build requires: hibernate-entitymanager to genschema (jesusr@redhat.com)
- requires: rhino, quartz, & log4j (jesusr@redhat.com)
- include specific jars, not ALL jars (jesusr@redhat.com)
- require: hibernate3-entitymanager, c3p0, hornetq, oauth, netty (jesusr@redhat.com)
- fix up classpath (jesusr@redhat.com)
- build require jms for qpid (jesusr@redhat.com)
- servlet-api -> servlet_api (jesusr@redhat.com)
- build requires qpid-java-client (jesusr@redhat.com)
- log4j is different too (jesusr@redhat.com)
- replace persistence-api with jpa_1_0_api version (jesusr@redhat.com)
- build require: jpa_1_0_api (jesusr@redhat.com)
- only require what's needed for genschema (jesusr@redhat.com)
- Include specific jars, generated by buildr. (jesusr@redhat.com)
- Add buildr task to output the Ant-style classpath. (awood@redhat.com)
- update to quartz 1.8.4 (jesusr@redhat.com)
- Move dom4j under the hibernate group in our buildfile (jbowes@redhat.com)
- Remove unused jdom dep (jbowes@redhat.com)
- update jackson to 1.6.3 (jbowes@redhat.com)
- bump google-collections to 1.0 (jesusr@redhat.com)
- set min message size only once at creation of queue (jesusr@redhat.com)
- update resteasy to 2.3.1 (jesusr@redhat.com)
- BuildRequires guice 3.0 (jesusr@redhat.com)
- upgrade google-collection 1.0 and remove Nullable (jesusr@redhat.com)
- move from warp-persist to guice-persist (jbowes@redhat.com)
- switch to guice 3 (jbowes@redhat.com)
- upgrade to hornetq 2.2.11.Final (jesusr@redhat.com)
- jakarta-commons-httpclient (jesusr@redhat.com)
- apache-commons-codec (jesusr@redhat.com)
- comment out quartz for now (jesusr@redhat.com)
- codehaus-jackson-core-lgpl (jesusr@redhat.com)
- google-collections (jesusr@redhat.com)
- jakarta-commons-io (jesusr@redhat.com)
- add bouncycastle (jesusr@redhat.com)
- quartz (jesusr@redhat.com)
- jackson (jesusr@redhat.com)
- codehaus-jackson (jesusr@redhat.com)
- jakarta-commons-lang (jesusr@redhat.com)
- hornetq (jesusr@redhat.com)
- log4j (jesusr@redhat.com)

* Thu May 03 2012 jesus m. rodriguez <jesusr@redhat.com> 0.5.32-1
- Add a null check for consumer.setReleaseVer (alikins@redhat.com)
- 818040: rhsm release combobox doesn't reset release now (alikins@redhat.com)

* Wed May 02 2012 Bryan Kearney <bkearney@redhat.com> 0.5.31-1
- Allow the keystore password to be passed in (bkearney@redhat.com)

* Tue May 01 2012 jesus m. rodriguez <jesusr@redhat.com> 0.5.30-1
- Add postgresql-jdbc driver to the requires. (bkearney@redhat.com)
- Only update releasever if it's changed. (alikins@redhat.com)

* Tue May 01 2012 jesus m. rodriguez <jesusr@redhat.com> 0.5.29-1
- 799979: allow katello to set the allow consumer name pattern
  (alikins@redhat.com)
- 814385: fix releasever schema to be varchar instead of bytea
  (alikins@redhat.com)
- remove use of * imports, and some import sorting (alikins@redhat.com)
- 813529: Refresh pool failure for null pointer exception caused by null
  attribute value. (wpoteat@redhat.com)
- latest strings from zanata (alikins@redhat.com)

* Wed Apr 25 2012 jesus m. rodriguez <jesusr@redhat.com> 0.5.28-1
- support_level_exempt attribute allows products to be service level agnostic.
  service level is case insensitive in all scenarios. (wpoteat@redhat.com)
- Fix bad assumption on constraint exceptions. (dgoodwin@redhat.com)
- 807468: Fix content import error if label has changed. (dgoodwin@redhat.com)
- 805027: Do not include uebercert consumers in ownerinfo compliance counts.
  (mstead@redhat.com)
- 805690: add some content with empty/null gpgkeys (alikins@redhat.com)
- 802263: autodetection of deleted hypervisor cleanup (cduryee@redhat.com)
- Load default consumer types via database instead of init URL.
  (dgoodwin@redhat.com)
- Fix quartz locks db error. (dgoodwin@redhat.com)
- 804071: CRL Revocation Task was not running in hosted. (wpoteat@redhat.com)
- Add the missing quartz lock rows for new databases. (dgoodwin@redhat.com)
- Remove the changeset template, not used. (dgoodwin@redhat.com)
- Remove changelog-create inclusion in changelog-update. (dgoodwin@redhat.com)
- Add script for devs to generate db changelog templates. (dgoodwin@redhat.com)
- Update database during dev deploy if GENDB not set. (dgoodwin@redhat.com)
- Add a small changeset template. (dgoodwin@redhat.com)
- Add rpm dependency on liquibase. (dgoodwin@redhat.com)
- Add cpdb database create/update utility. (dgoodwin@redhat.com)
- Integrate liquibase with dev deploy script. (dgoodwin@redhat.com)
- Integrate liquibase with cpsetup. (dgoodwin@redhat.com)
- Add initial liquibase schema XML. (dgoodwin@redhat.com)
- 811581: cannot unregister hypervisor when >1 guests are consuming bonus subs
  (cduryee@redhat.com)
- latest strings from zanata (alikins@redhat.com)

* Tue Apr 03 2012 Chris Duryee (beav) <cduryee@redhat.com>
- bump candlepin-deps version for new jackson (cduryee@redhat.com)
- 807452: Null pointer check added on attribute value. Was causing NPE in hash
  code. (wpoteat@redhat.com)
- 796468: Owner with id FOO could not be found. (wpoteat@redhat.com)
- Pools with Duplicate ProductPoolAttributes cannot be deleted
  (wpoteat@redhat.com)
- Move apicrawl code to its own java package (jbowes@redhat.com)
- Fix apicrawl json schema generation for output types (jbowes@redhat.com)
- 807009: rpmdiff warning for Vendor name (cduryee@redhat.com)
- 803814: Make registration transactional. (dgoodwin@redhat.com)
- 804227: add simple model for Release (alikins@redhat.com)
- Defer the creation of the simple date format until it is used.
  (bkearney@redhat.com)
- Add default service level for an org. (dgoodwin@redhat.com)
- 805608: Enhance cpsetup so that different usernames and passwords can be used
  (bkearney@redhat.com)
- New implementation of HATEOAS serialization. (dgoodwin@redhat.com)
- Add API call to "undo" all imports for an org (dgoodwin@redhat.com)

* Wed Mar 14 2012 jesus m. rodriguez <jesusr@redhat.com> 0.5.26-1
- latest strings from zanata (alikins@redhat.com)
- add releasever (alikins@redhat.com)

* Wed Mar 14 2012 jesus m. rodriguez <jesusr@redhat.com> 0.5.25-1
- 800652: Dry-run autobind errors when all pools are unavailable for a consumer (wpoteat@redhat.com)
- 719743: Modified the error message when there are no entitlements for a pool (bkearney@redhat.com)
- Perfomance enhancement for consumer list. (wpoteat@redhat.com)

* Wed Mar 07 2012 Chris Duryee (beav) <cduryee@redhat.com>
- Performance improvement for OwnerInfo REST query (wpoteat@redhat.com)
- 798227: revoke guest certs from previous host on migration
  (jbowes@redhat.com)
- 785170: Prevent duplicate entitlement regenerations on import.
  (awood@redhat.com)
- Allow all pool information to be returned in JSON (wpoteat@redhat.com)
- 796468: Ensure that the word Organization is translated (bkearney@redhat.com)
- 798430: Make the config file read only by the tomcat/jboss user
  (bkearney@redhat.com)
- 798372: Lower the log severity of passing in an incorrect org id
  (bkearney@redhat.com)
- 795798: Improve the error message for invalid service levels
  (bkearney@redhat.com)
- 796468: Use the term Organization instead of Owner when passing in incorrect
  Owner keys. (bkearney@redhat.com)

* Mon Feb 27 2012 Chris Duryee (beav) <cduryee@redhat.com>
- 788940: allow products to have the same name (cduryee@redhat.com)
- Ability to dry-run an autocommit and present a pool/quantity response
  (wpoteat@redhat.com)
- 784665: remove activation key -> pool association before removing pool
  (cduryee@redhat.com)

* Wed Feb 22 2012 jesus m. rodriguez <jesusr@redhat.com> 0.5.22-1
- 754369: A single quote causes the french string replacement to not be replaced (bkearney@redhat.com)
- Update DeletedConsumer when identical UUID is used for deleted consumer.  (cduryee@redhat.com)
- latest translations from zanata (alikins@redhat.com)
- Make import certificate spec more resilient (jbowes@redhat.com)
- 795431: add source_pool_id attribute on bonus pools (cduryee@redhat.com)
- 789127: ignore multiplier on imported products (jbowes@redhat.com)
- Filter select best pools by SLA if set on consumer. (mstead@redhat.com)
- 787278: import: also capture ConstraintViolation directly.  (jesusr@redhat.com)
- 790751: validate older import check against export creation date (cduryee@redhat.com)

* Fri Feb 17 2012 jesus m. rodriguez <jesusr@redhat.com> 0.5.21-1
- 794852: Add the filtering check to the per environment enable section (bkearney@redhat.com)
- Use a config value to enable or disable content filtering by environment. (bkearney@redhat.com)
- import: ensure upstream uuid is unique (jesusr@redhat.com)
- Compare SLA strings by string value, not object id. (mstead@redhat.com)
- Allow consumer principals to list owner service levels (dgoodwin@redhat.com)

* Thu Feb 16 2012 Chris Duryee (beav) <cduryee@redhat.com>
- 786730: multi-entitlement guests were not migrating properly, and logging fix
  (cduryee@redhat.com)
- Infrastructure for SLA for owner and consumer (wpoteat@redhat.com)
- Return a 410 from the ConsumerAuth auth provider. (awood@redhat.com)
- Return a 410 from the SecurityInterceptor. (awood@redhat.com)
- class to track deleted consumers. (jmrodri@gmail.com)
* Wed Feb 15 2012 Devan Goodwin <dgoodwin@rm-rf.ca> 0.5.19-1
- 790417: Use environment name instead of ID in content URLs
  (dgoodwin@redhat.com)
- Properly set start/end dates for Installed Products (mstead@redhat.com)
- fix virt_limit case where value is not unlimited, and <=1 (jbowes@redhat.com)
- populate test subscriptions for all test owners (jbowes@redhat.com)
- 786730: occasional NPE from virt-who when reporting new guest IDs
  (cduryee@redhat.com)

* Thu Feb 09 2012 Chris Duryee (beav) <cduryee@redhat.com>
- 786730: occasional NPE from virt-who when reporting new guest IDs
  (cduryee@redhat.com)

* Thu Feb 09 2012 Devan Goodwin <dgoodwin@rm-rf.ca> 0.5.17-1
- 789034: Replace $env if possible when generating ent certs.
  (dgoodwin@redhat.com)
- better log statements for debugging migration issues (cduryee@redhat.com)
- Ignore quantity when checking sockets for non-stacked entitlements.
  (dgoodwin@redhat.com)
- make zanata translations compile (alikins@redhat.com)
- translations from zanata (alikins@redhat.com)
- Check sockets on non-stacked entitlements as well. (dgoodwin@redhat.com)

* Fri Feb 03 2012 jesus m. rodriguez <jesusr@redhat.com> 0.5.16-1
- 786963: allow manifest imports to handle complex version numbers (jesusr@redhat.com)
- 784905: Fix deletion of orgs with an ueber certificate. (dgoodwin@redhat.com)
- Fix a migration bug with product attributes on pools. (dgoodwin@redhat.com)

* Wed Feb 01 2012 jesus m. rodriguez <jesusr@redhat.com> 0.5.15-1
- Merge branch 'status' (dgoodwin@redhat.com)
- 768872: do not remove entitlements when host reports zero guests (cduryee@redhat.com)
- 741931: Make i18n provider as slim as possible (bkearney@redhat.com)
- Bump max jvm mem to 2gig for testing (jbowes@redhat.com)
- Clear up some database related memory leaks in our tests (jbowes@redhat.com)
- Change API to demote multiple content sets from env. (dgoodwin@redhat.com)
- Improve promotion ruby wrapper and spec test. (dgoodwin@redhat.com)
- Allow promotion of multiple content sets at once. (dgoodwin@redhat.com)
- Support environment descriptions. (dgoodwin@redhat.com)
- Support lookup of environment by it's friendly name. (dgoodwin@redhat.com)
- Test environment content filtering. (dgoodwin@redhat.com)
- Respect overridden enabled setting on content. (dgoodwin@redhat.com)
- Exclude non-promoted content during cert generation. (dgoodwin@redhat.com)
- Support environment registration. (dgoodwin@redhat.com)
- Add names for environments, needed by client. (dgoodwin@redhat.com)
- Add config to hide supported resources. (dgoodwin@redhat.com)
- Only expose environment REST calls in Katello module. (dgoodwin@redhat.com)
- Add support for promoting content into an environment. (dgoodwin@redhat.com)
- Add REST API for environment management. (dgoodwin@redhat.com)
- Add Environment and curator classes. (dgoodwin@redhat.com)

* Wed Jan 25 2012 Chris Duryee (beav) <cduryee@redhat.com>
- 768872: do not autosubscribe guests that are migrated (cduryee@redhat.com)

* Wed Jan 25 2012 Chris Duryee (beav) <cduryee@redhat.com>
- checkstyle: loads of minor fixes (jesusr@redhat.com)
- 768872: autosubscribe VMs when they migrate to a new host
  (cduryee@redhat.com)
* Tue Jan 24 2012 jesus m. rodriguez <jesusr@redhat.com> 0.5.12-1
- Optimize RulesCurator db updated time lookup (jbowes@redhat.com)
- 750307: Prevent duplicate pools via unique constraints (jbowes@redhat.com)
- Revert "750307: duplicate pools possible" (jbowes@redhat.com)
- Wrap JobCurator operations in units of work (jbowes@redhat.com)
- remove unused pinsetter chained listener (jbowes@redhat.com)
- Update productId on refreshPools if changed (jbowes@redhat.com)
- fix NaN error in rules.js (jesusr@redhat.com)
- Remove extra logic from the update checkin time path. (bkearney@redhat.com)
- 782561: Better error messages during manifest import. (dgoodwin@redhat.com)
- 772935: Add current time UTC to the status call (bkearney@redhat.com)
- Make the cache of serializers global. (bkearney@redhat.com)

* Tue Jan 17 2012 jesus m. rodriguez <jesusr@redhat.com> 0.5.11-1
- rebuilding to new branch

* Thu Jan 12 2012 jesus m. rodriguez <jesusr@redhat.com> 0.5.10-1
- add buildrequires for selinux-policy-doc (jesusr@redhat.com)

* Wed Jan 11 2012 jesus m. rodriguez <jesusr@redhat.com> 0.5.9-1
- i18n: extracted and merged strings. (jesusr@redhat.com)
- 743968: Do not import rules files exported from older candlepins (cduryee@redhat.com)
- initial selinux policy import (alikins@redhat.com)
- latest string files (bkearney@redhat.com)
- Latest strings from zanata (bkearney@redhat.com)
- 769644: Disable system wide checking of last manifest import date. (dgoodwin@redhat.com)
- No longer remove old consumer types on import. (mstead@redhat.com)
- tests: small gains in coverage (jmrodri@gmail.com)
- Clarify PUT /roles does not update collections in API documentation. (dgoodwin@redhat.com)
- tests: increase test coverage. (jesusr@redhat.com)

* Mon Dec 19 2011 Bryan Kearney <bkearney@redhat.com> 0.5.8-1
- Make imports be more resiliant. (bkearney@redhat.com)
- Provide better error logging for manifest imports (bkearney@redhat.com)
- checkstyle fix (jesusr@redhat.com)
- 766974: return 401 UNAUTHORISED when creds can make a difference.
  (jesusr@redhat.com)
- Updated consumer return JSON to include more information for the installed
  products. (wpoteat@redhat.com)

* Mon Dec 19 2011 Bryan Kearney <bkearney@redhat.com> 0.5.7-1
- 760560: Bonus pools, which provide per host entitlements, were not inheriting
  the attributes which included SLA and architecture (bkearney@redhat.com)
- Pull in the latest string (bkearney@redhat.com)
- 754426: Remove the remaining flex expiry code from Entitlement
  (jbowes@redhat.com)
- perf: seperate mockable consumer resource test from integration ones
  (jbowes@redhat.com)
- Pull db specific tests out of DefaultSubscriptionServiceAdapaterTest
  (jbowes@redhat.com)
- Revert "perf: make I18nProvider a singleton" (jesusr@redhat.com)
- perf: make I18nProvider a singleton (jmrodri@gmail.com)

* Tue Dec 13 2011 jesus m. rodriguez <jesusr@redhat.com> 0.5.6-1
- 766974: force job to authenticate temporarily while we fix the REAL bug. (jesusr@redhat.com)
- Added status string to ComplianceStatus. (mstead@redhat.com)
- Class to enable debug logging of guice, create for debugging perf problems. (jmrodri@gmail.com)
- perf: improve test performance by remove jpa init (jmrodri@gmail.com)
- dump heap to a .hprof file if OOME hit. (jesusr@redhat.com)
- Revert "754843: Fix legacy virt bonus pools missing pool_derived." (dgoodwin@redhat.com)

* Wed Dec 07 2011 jesus m. rodriguez <jesusr@redhat.com> 0.5.5-1
- Added support for Host registration when host can not register itself. (mstead@redhat.com)

* Tue Dec 06 2011 Devan Goodwin <dgoodwin@rm-rf.ca> 0.5.4-1
- Fix logic error with SystemPrincipal's. (dgoodwin@redhat.com)

* Fri Dec 02 2011 jesus m. rodriguez <jesusr@redhat.com> 0.5.3-1
- 758462: ensure job detail isn't null, skip it. (jesusr@redhat.com)
- javadoc: remove unused tags (jesusr@redhat.com)
- fix javadoc warnings: add tools.jar to classpath & define httpcode tag
  (jesusr@redhat.com)
- Latest strings (bkearney@redhat.com)
- 756628: Translate missing rule errors. (dgoodwin@redhat.com)
- Remove some unused test products (and add a couple new ones)
  (alikins@redhat.com)

* Tue Nov 29 2011 Devan Goodwin <dgoodwin@rm-rf.ca> 0.5.2-1
- Disable manifest rules import. (dgoodwin@redhat.com)
- 755677: Activation Keys should not check quantity on umlimited pools
  (bkearney@redhat.com)
- 754841: Implement DELETE /pools/id. (dgoodwin@redhat.com)
- 754843: Fix legacy virt bonus pools missing pool_derived.
  (dgoodwin@redhat.com)
- 753093: The Available Subscriptions count do not show correctly in
  Subscription Manager GUI (wpoteat@redhat.com)
- Fix rpm so that files are not left around. (jesusr@redhat.com)
- move files to proper location to match package. (jmrodri@gmail.com)

* Wed Nov 16 2011 jesus m. rodriguez <jmrodri@gmail.com> 0.5.1-1
- 750307: duplicate pools possible (jmrodri@gmail.com)
- Push the new strings file, do not pull (bkearney@redhat.com)
- Revert "Syncing the strings" (bkearney@redhat.com)
- Syncing the strings (bkearney@redhat.com)
- 750351: Delete expired subscriptions on refreshPools (mstead@redhat.com)
- return public/private key pair for upstream certificate (jbowes@redhat.com)
- Added new API call to ConsumerResource for getting current compliance status. (mstead@redhat.com)
- 751158: Deny manifest consumers access to derived pools. (dgoodwin@redhat.com)
- Behavior of unlimited bonus pool when physical pool is exhausted.  (wpoteat@redhat.com)
- write test data to candlepin_info.properties to fix unit test (jmrodri@gmail.com)
- 688707: capture all of the internal exceptions, transform into JSON.  (jmrodri@gmail.com)
- Latest strings (bkearney@redhat.com)
- Filter out uebercert-related pools and subscriptions (dmitri@redhat.com)
- 749361: Candlepin exports did not have a proper version in meta.json (awood@redhat.com)
- Allow command line options to override values in .candlepinrc (awood@redhat.com)
- version bump (jesusr@redhat.com)
- move org.fedoraproject.candlepin -> org.candlepin (jmrodri@gmail.com)
- Provide the ability to force an import that is older than existing data. (awood@redhat.com)
- fixed a bug when the first created uebercertificate would be returned for all
  owners (ddolguik@redhat.com)
- Latest string files (bkearney@redhat.com)
- fix NPE being logged during Candlepin usage. (jmrodri@gmail.com)

* Tue Oct 25 2011 jesus m. rodriguez <jesusr@redhat.com> 0.4.23-1
- don't use bouncycastle for now (jesusr@redhat.com)

* Tue Oct 25 2011 jesus m. rodriguez <jesusr@redhat.com> 0.4.22-1
- Alter testdata to make zero socket/no socket more easily testable.
  (cduryee@redhat.com)
- return a string for the subscrpition cert, to make it easier for thumbslug to
  parse (cduryee@redhat.com)
- Remove prints in rspec tests. (dgoodwin@redhat.com)
- Made the logging conditional. (wpoteat@redhat.com)
- bonus pool quantity adjustments were made synchronous. (wpoteat@redhat.com)
- 747399: Allow non-system consumers with no arch fact to pass arch rule.
  (dgoodwin@redhat.com)
- Add a test script for bonus pool unbind. (dgoodwin@redhat.com)
- Change to primitive boolean for status (wpoteat@redhat.com)
- Changes from code review for EntitlementRules refactoring. (wpoteat@redhat.com)
- Whitespace cleanup in default rules. (dgoodwin@redhat.com)
- Cleaning up role update code (tsmart@redhat.com)
- Cleaning up role put/edit to no longer require the ID to be passed into the
  body of the JSON (tsmart@redhat.com)
- Adding a comment. (dgoodwin@redhat.com)
- Added GuestId event messages to EventAdapterImpl (mstead@redhat.com)
- Adding httpcode doclet to document what http status codes are returned by a
  call. (awood@redhat.com)
- Remove an unused method. (dgoodwin@redhat.com)
- Fix intermittent ConsumerCuratorTest failure. (dgoodwin@redhat.com)
- Expose subscription certs via candlepin API, for use by thumbslug.
  (cduryee@redhat.com)
- Fixed problem where wrong host was being looked up when checking host change.
  (mstead@redhat.com)
- Cleanup dead code. (dgoodwin@redhat.com)
- Fix host list pools bug. (dgoodwin@redhat.com)
- updates to virt and sub-pool tests for allowing entitlement revocation when
  there are sub-pools (wpoteat@redhat.com)
- More virt spec testing. (dgoodwin@redhat.com)
- Add virt spec tests. (dgoodwin@redhat.com)
- Revoke bonus entitlements when source entitlement is unbound
  (wpoteat@redhat.com)
- Fix host restricted pool binds. (dgoodwin@redhat.com)
- Correction to the method call order (wpoteat@redhat.com)
- Cleanup: Updating logs and comments. (mstead@redhat.com)
- Moved guestId add/removed event sending into checkForGuestUpdate
  (mstead@redhat.com)
- Correction to post_virt_only in unbind. Status reports standalone true/false.
  (wpoteat@redhat.com)
- 743704: do not allow autobind to bind to pools with warnings
  (cduryee@redhat.com)
- Prefer virt_only + requires_host pools over just virt_only.
  (dgoodwin@redhat.com)
- 746035: set entitlement start dates to start date of pool
  (alikins@redhat.com)
- spec to check that entitlements get created with pool start date
  (dgoodwin@redhat.com)
- Post unbind reduction of bonus pools (wpoteat@redhat.com)
- 717650: enable recovery on specific async jobs. (jesusr@redhat.com)
- 744259: Entitlement quantity was missing in the entitlement certificate.
  (awood@redhat.com)
- fix bouncycastle jar name: bcprov *NOT* bcproj (jesusr@redhat.com)
- 735354: dropdb in deploy script fails on first run (jesusr@redhat.com)
- Revoke guest entitlements when host changes. (mstead@redhat.com)
- Decrement derived pool when parent pool is included in manifest.
  (wpoteat@redhat.com)
- Don't enforce requires_host in hosted. (dgoodwin@redhat.com)
- Fix refresh pools for standalone. (dgoodwin@redhat.com)
- Port virt_limit.spec to Java unit tests. (dgoodwin@redhat.com)
- Unit Tests for ConsumerCurator host/guest. (wpoteat@redhat.com)
- More comment cleanup. (dgoodwin@redhat.com)
- Remove TODO + comment cleanup. (dgoodwin@redhat.com)
- Adding consumer curator TODOs. (dgoodwin@redhat.com)
- Code cleanup. (dgoodwin@redhat.com)
- Ensure that facts, products and guestIds can be updated at same time.
  (mstead@redhat.com)
- Send events on updateConsumer for each guestId that is added/removed from
  consumer. (mstead@redhat.com)
- Spec tests for /consumers/{uuid}/host and /consumers/{uuid}/guests
  (wpoteat@redhat.com)
- Remove virt_limit spec. (dgoodwin@redhat.com)
- Remove parent restricted column in cp_pool. (dgoodwin@redhat.com)
- Fixing broken spec tests due to guestsIds rename. (mstead@redhat.com)
- More guestsIds to guestIds fixes. (dgoodwin@redhat.com)
- Modification to the use of a sql query on get consumer by system uuid
  (wpoteat@redhat.com)
- Fix setGuestIds method name. (dgoodwin@redhat.com)
- Fix issues with virt host restricted pools. (dgoodwin@redhat.com)
- Drop virt_limit_spec.rb. (dgoodwin@redhat.com)
- Update to allow /host and /guests to function (wpoteat@redhat.com)
- Fixed broken test (mstead@redhat.com)
- Added test cases for PUT /consumers/{uuid} updating guestIds. (mstead@redhat.com)
- Removing unneeded add/remove guest functions in candlepin_api (mstead@redhat.com)
- Use host lookup in rules when checking virt_only pools. (dgoodwin@redhat.com)
- Incorrect name for accessor (wpoteat@redhat.com)
- Removed old parent/child relationship (wpoteat@redhat.com)
- Expose host consumer lookup to rules. (dgoodwin@redhat.com)
- Restore if null set checks in Consumer. (dgoodwin@redhat.com)
- refactor for rules (wpoteat@redhat.com)
- Update for unit tests (wpoteat@redhat.com)
- Refactor ConsumerGuest to GuestId. (dgoodwin@redhat.com)
- Addition (wpoteat@redhat.com)
- First checkin for virt-guest handling (wpoteat@redhat.com)

* Thu Oct 13 2011 jesus m. rodriguez <jesusr@redhat.com> 0.4.21-1
- respin to right tag

* Wed Oct 12 2011 jesus m. rodriguez <jesusr@redhat.com> 0.4.20-1
- checkstyle: fix javadoc for bouncycastle changes (jmrodri@gmail.com)
- checkstyle: remove *ALL* trailing whitespace (jmrodri@gmail.com)
- remove tabs (jmrodri@gmail.com)
- checkstyle: check for trailing whitespace (jmrodri@gmail.com)
- ditch tomcat5 (jesusr@redhat.com)
- require bouncycastle, symlink & remove old bc (jesusr@redhat.com)
- upgrade to bouncycastle 1.46 (fix code to match exceptions)
  (jesusr@redhat.com)
- Update the script to generate fake exports to the new API
  (bkearney@redhat.com)
- Fix unit tests broken by rules change (cduryee@redhat.com)
- Drop default of debug logging. (dgoodwin@redhat.com)

* Wed Oct 05 2011 jesus m. rodriguez <jesusr@redhat.com> 0.4.19-1
- Do not add rulewarning.unsupported.number.of.sockets if sockets is not
  defined on the consumer or product, or if the product has zero sockets.
  (cduryee@redhat.com)
- Candlepin setup requires wget (bkearney@redhat.com)
- Ensure that the Candlepin exceptions wrapping normal exceptions do not cause
  a class cast exception. Chris Alfonso found this doing testing today.
  (bkearney@redhat.com)
- Return to throwing service unavailable exception. (dgoodwin@redhat.com)
- 734214: Use a more intelligent loop to wait for tomcat to start up
  (bkearney@redhat.com)
- fix up partial stack healing with no specified products (jbowes@redhat.com)
- teach select_best_pools to heal partial stacks (jbowes@redhat.com)
- fix up the test and code for select_best_pools with installed products
  (jbowes@redhat.com)
- Rspec tests for healing. (dgoodwin@redhat.com)
- Use current date in auto-subscribe if none provided. (dgoodwin@redhat.com)
- Dates can't be used as params. (jesusr@redhat.com)
- Teach select_best_pools about existing compliant products (jbowes@redhat.com)
- checkstyle: long lines, unused imports (jesusr@redhat.com)
- Use non-compliant products if none are provided. (dgoodwin@redhat.com)
- add test for date (jesusr@redhat.com)
- filter owner pools by entitle date (jesusr@redhat.com)
- checkstyle: a number of checkstyle fixes, see below. (jesusr@redhat.com)
- checkstyle: remove dead code and whitespaces (jesusr@redhat.com)
- cleanup javadoc (jesusr@redhat.com)
- reformat comment to fit 80 chars making it easier to read. (jesusr@redhat.com)
- Pass compliance status down to select best pool rules. (dgoodwin@redhat.com)
- initial changes for autobind with a date. (jesusr@redhat.com)
- select the stack id that will cover the most sockets (jbowes@redhat.com)
- Don't stack from different stack ids (jbowes@redhat.com)
- Test stacks providing different products. (dgoodwin@redhat.com)
- entitle from multiple pools from the same stack to fill an entitlement, if
  required (jbowes@redhat.com)
- Handle entitled and partially stacked edge case. (dgoodwin@redhat.com)
- don't overdraw from a pool for stacking autobind (jbowes@redhat.com)
- readd findStackingPools function and use it to get entitlement quantity
  (jbowes@redhat.com)
- use a list of lists of pools for select_best_pools (jbowes@redhat.com)
- add and remove some comments from default-rules (jbowes@redhat.com)
- Return hashmaps directly from the js rules (jbowes@redhat.com)
- Refactor selectBestPools to return pool/quantity pairs (jbowes@redhat.com)
- Detect uninstalled but partially stacked as out of compliance.
  (dgoodwin@redhat.com)
- Add stacking compliance checking. (dgoodwin@redhat.com)
- Port compliance logic to javascript rules. (dgoodwin@redhat.com)
- Add some tests for stacking. (dgoodwin@redhat.com)
- Check compliance for non-stacked products. (dgoodwin@redhat.com)
- Add tests for compliance checking. (dgoodwin@redhat.com)
- Add method for listing a consumers entitlements for specific date.
  (dgoodwin@redhat.com)
- Sketch out interface for compliance checking. (dgoodwin@redhat.com)
- Remove a remaining bind by product with quantity signature.
  (dgoodwin@redhat.com)
- Stacking javascript cleanup. (dgoodwin@redhat.com)
- Expose product attributes on read-only pools. (dgoodwin@redhat.com)
- Cleanup quantity in bind by product methods. (dgoodwin@redhat.com)

* Wed Sep 28 2011 jesus m. rodriguez <jesusr@redhat.com> 0.4.18-1
- Added consumerCountsByEntitlementStatus to OwnerInfo (mstead@redhat.com)
- Checkstyle (wpoteat@redhat.com)
- 737935: First part of making guest consumer machine UUID's into a
  relationship in the CP database. (wpoteat@redhat.com)
- Put JSON Annotations on the GET imports call for owners (bkearney@redhat.com)
- remove trailing whitespace (jesusr@redhat.com)
- Latest string files from zanata (bkearney@redhat.com)
- uebercert's content url is now correct after re-generation (ddolguik@redhat.com)
- remove unused variable while I'm in the code. (jesusr@redhat.com)

* Thu Sep 22 2011 jesus m. rodriguez <jesusr@redhat.com> 0.4.17-1
- Add missing resources to apicrawler output. (dgoodwin@redhat.com)
- Revert "Updated the strings from zanata" (bkearney@redhat.com)
- Owner info lists all the enabled consumer types (wpoteat@redhat.com)
- Updated the strings from zanata (bkearney@redhat.com)
- Make cpc more command line driven to work against non local machines
  (bkearney@redhat.com)
- Allow multiple consumer types to generate manifests. (wpoteat@redhat.com)
- added support for generation of ueber certs  - and increased checkstyle
  parameter list size to 24 (ddolguik@redhat.com)

* Wed Sep 14 2011 jesus m. rodriguez <jesusr@redhat.com> 0.4.16-1
- bumping candlepin-deps version to 0.0.18 (jesusr@redhat.com)

* Wed Sep 14 2011 jesus m. rodriguez <jesusr@redhat.com> 0.4.15-1
- Fix the russian string (bkearney@redhat.com)
- Updated string files (bkearney@redhat.com)
- 718052: Remove owner from consumer resource return codes. Only use the term org. (bkearney@redhat.com)
- 736791: Upgrade to RESTEasy 2.2.1GA (jbowes@redhat.com)

* Wed Sep 07 2011 jesus m. rodriguez <jesusr@redhat.com> 0.4.14-1
- add a containsKey method to Config (jmrodri@gmail.com)
- 735087: If quartz is in clustered mode, we shouldn't schedule any jobs. (jesusr@redhat.com)

* Tue Sep 06 2011 jesus m. rodriguez <jesusr@redhat.com> 0.4.13-1
- Revert "731996: SQL Error when using REST query for events" (wpoteat@redhat.com)
- 732538: Disallow the relationship between a 'person' pool and an activation key (wpoteat@redhat.com)
- 731996: SQL Error when using REST query for events (wpoteat@redhat.com)
- Add an autoheal attribute for consumers. (dgoodwin@redhat.com)
- Stop erroring out on the healing bind request. (dgoodwin@redhat.com)
- Corrections for checkstyle (wpoteat@redhat.com)
- Fix export of virt entitlements for non-candlepin consumers.  (wpoteat@redhat.com)
- 734174: Add missing produces annotations for role resource.  (dgoodwin@redhat.com)
- add an OID for virt entitlements (cduryee@redhat.com)

* Tue Aug 30 2011 jesus m. rodriguez <jesusr@redhat.com> 0.4.12-1
- 731577: API to query jobs by owner, principal, consumer uuid. (jmrodri@gmail.com)
- use commons.lang.StringUtils not hibernate.hbm2x.StringUtils (jmrodri@gmail.com)

* Wed Aug 24 2011 jesus m. rodriguez <jesusr@redhat.com> 0.4.11-1
- Move the translations to fedora.zanata.org (bkearney@redhat.com)
- Handle null values correctly before invoking the certgen (bkearney@redhat.com)
- Added another stackable product (mstead@redhat.com)
- Extracted the strings to updtae the zanata server (bkearney@redhat.com)
- 729780: Requesting a secure object which does not exist should throw a 404,
  not a 403. (bkearney@redhat.com)
- 708058: Server 500 error thrown when user autosubscribes and has no
  entitlements (wpoteat@redhat.com)
- Add consumer/uuid for EntitlerJob jobstatus (jbowes@redhat.com)
- Convert JobStatus ownerKey to a generic targetId/targetType
  (jbowes@redhat.com)
- Fix registration issue with null facts/installed products.
  (dgoodwin@redhat.com)
- Update supported products when updating consumer. (dgoodwin@redhat.com)
- Store a set of installed products for a consumer. (dgoodwin@redhat.com)
- 728622: Inconsistent enable config entries (wpoteat@redhat.com)
- 728624: Activation keys are successfully being created with invalid chars
  (wpoteat@redhat.com)
- 728636: Duplicate activation key error is hard to decipher
  (wpoteat@redhat.com)
- Checkstyle (wpoteat@redhat.com)
- 729125: Adding pools to an activation key should fail when quantity < 1 and
  quantity > totalQuantity for a multi-entitlement pool (wpoteat@redhat.com)
- 729070: Adding pools to an activation key should be blocked when specifying a
  quantity>1 for a non-multi-entitlement pool (wpoteat@redhat.com)
- 728721: NullPointerException thrown when registering with an activation key
  bound to a pool that requires_consumer_type person. (wpoteat@redhat.com)
- Added test case for CandlepinContextListener (jesusr@redhat.com)
- remove unused import (checkstyle please) :D (jmrodri@gmail.com)
- Disable quantity for bind by product. (dgoodwin@redhat.com)
- Replace hand-coded mock object with Mockito. (jmrodri@gmail.com)
- 729066: remove logging statement to avoid filling up logs.
  (jmrodri@gmail.com)
- Allow disabling pinsetter on a particular node. (jmrodri@gmail.com)
- refactor scheduleJob. (jmrodri@gmail.com)

* Mon Aug 08 2011 Devan Goodwin <dgoodwin@rm-rf.ca> 0.4.10-1
- 727611: Allow trusted user principals to register consumers.
  (dgoodwin@redhat.com)
- Only create one Config. Reduce from 1146 to 6 on typical deploy.
  (jesusr@redhat.com)
- change jobgroup column to 15 vs 255 characters. (jesusr@redhat.com)
- change the jobgroup name (jesusr@redhat.com)

* Wed Aug 03 2011 jesus m. rodriguez <jesusr@redhat.com> 0.4.9-1
- extract strings. (jesusr@redhat.com)

* Wed Aug 03 2011 jesus m. rodriguez <jesusr@redhat.com> 0.4.8-1
- Update a test for stacking (wpoteat@redhat.com)
- Updates to ensure removal of children actkeypools and non-circular json
  object reference. (wpoteat@redhat.com)
- 720487: Allow super admins to call refrehs pools with new orgs to be created.
  Added new spec tests as well (bkearney@redhat.com)
- 727600: Fix the order in which the parameters are checked to fake on the cli
  (bkearney@redhat.com)
- 724916: Fix the license for the rpm (bkearney@redhat.com)
- Add utility script for updating rules for testing (alikins@redhat.com)
- Test cases for RulesResource (alikins@redhat.com)
- Test cases for RulesCurator (alikins@redhat.com)
- spec for rules import/update/delete (alikins@redhat.com)
- cleanup unresolved merge conflict in comment in DatabaseTestFixture
  (alikins@redhat.com)
- checksytle cleanup (alikins@redhat.com)
- file from last commit not added. (wpoteat@redhat.com)
- whitespace cleanup for default-rules.js (alikins@redhat.com)
- Add stacking support to default-rules.js (alikins@redhat.com)
- Expose attribtues to ReadOnlyPool for rules to use (alikins@redhat.com)
- we need to be able to select more than one best pool now so don't break on
  first matching pool (alikins@redhat.com)
- RulesCurator was not deleting rules, change to use super.delete (alikins@redhat.com)
- Include socket info oid in entitlement certificate (alikins@redhat.com)
- Add DELETE handler for deleting and reseting rules (alikins@redhat.com)
- Delete any rules we have modified on destroy of @cp wrapper class (alikins@redhat.com)
- add some test products with multi-entitle and socket attributes (alikins@redhat.com)
- alter an error msg (cduryee@redhat.com)
- Fix global pre-entitlement rule run twice per bind. (dgoodwin@redhat.com)
- As a user, I would like to register to an activation key, and
  have it subscribe the machine to a set of pools. (wpoteat@redhat.com)
- 722975: Check multi-entitle attribute if binding with quantity.  (dgoodwin@redhat.com)
- add a @verify to activateSubscription (aka redemption) (cduryee@redhat.com)
- check for existing status (jmrodri@gmail.com)
- As a user, I would like to register to an activation key, and have it
  subscribe the machine to a set of pools. (wpoteat@redhat.com)

* Fri Jul 29 2011 jesus m rodriguez <jmrodri@gmail.com> 0.4.7-1
- remove obsolete testcase. (jmrodri@gmail.com)
- fully test PinsetterKernel (jmrodri@gmail.com)
- disable some canceljob logging (cduryee@redhat.com)
- allow owners to manage their own activation keys (cduryee@redhat.com)
- 726711: Added arch/version to product cert extensions. (mstead@redhat.com)
- Enable principal for all pinsetter jobs. (jesusr@redhat.com)
- remove stupid System.out printlns with typos and useless todos
  (jesusr@redhat.com)
- made getPrincipalName abstract since everyone overrides it and the class is
  abstract. (jesusr@redhat.com)
- remove whitespace from end of lines (jesusr@redhat.com)
- add username to JobStatus (increase test coverage of JobCurator)
  (jesusr@redhat.com)
- remove useless whitespace (jesusr@redhat.com)
- Improve the candlepin puppet script (bkearney@redhat.com)
- 725242: Change the error messages for pools to be consistent with
  subscription manager (bkearney@redhat.com)
- test for exception handling (jesusr@redhat.com)
- remove useless throws statements (jesusr@redhat.com)
- reorg imports (jesusr@redhat.com)
- allow cleanup of failed jobs (jesusr@redhat.com)
- update cp_job with error information. (jesusr@redhat.com)
- throw job exception if an error occurs. (jesusr@redhat.com)
- doesn't have to be Serializable. (jesusr@redhat.com)
- remove unserializable items from datamap. (jesusr@redhat.com)
- Add nopo flag. (cduryee@redhat.com)
- Add some periods to the end of error messages (bkearney@redhat.com)
- 721136: Make hte title a summary of the even, and the text be in the event
  description (bkearney@redhat.com)
- checkstyle (cduryee@redhat.com)
- Cancel jobs and pause/unpause scheduler (cduryee@redhat.com)
- Add config option to gracefully handle unknown properties during import.
  (dgoodwin@redhat.com)
- more typos (bkearney@redhat.com)
- 721141: Typos caused issues in the event text (bkearney@redhat.com)
- Add the java depdency based on the install issues from the katello list
  (bkearney@redhat.com)
- unit test entitler (jmrodri@gmail.com)
- unit test the Entitler class (jmrodri@gmail.com)
- Checkstyle (wpoteat@redhat.com)
- User Stories 6983 and 6977 (wpoteat@redhat.com)
- Remove UserService.isReadyOnly (not used anymore) (jbowes@redhat.com)
- Add katello user service adapter (does nothing) (jbowes@redhat.com)
- Refactor registration to not require a user service lookup normally.
  (dgoodwin@redhat.com)
- async bind (jmrodri@gmail.com)
- Allow users to get a list of their roles. (cduryee@redhat.com)
- Remove UserServiceAdapter.getRoles; call it on a user directly
  (jbowes@redhat.com)
- Remove now invalid (and duplicated) spec auth test. (dgoodwin@redhat.com)
- Return only partial user objects in security interceptor.
  (dgoodwin@redhat.com)
- Add a trusted user principal implementation. (dgoodwin@redhat.com)
- Fix DB null column error when registering with auth keys.
  (dgoodwin@redhat.com)
- Added multi-entitlement product for QA testing. (mstead@redhat.com)
- Cleanup unused user service adapter call. (dgoodwin@redhat.com)
- Spec tests and some Ruby lib touchups. (dgoodwin@redhat.com)
- Fix activation key API to use key1,key2,key3 syntax. (dgoodwin@redhat.com)
- Added products with multi-entitlement attributes (mstead@redhat.com)
- Remove the Activate resource. (dgoodwin@redhat.com)
- Remove duplicated activation key creation path. (dgoodwin@redhat.com)
- Fix a Ruby client lib discrepancy with listing keys. (dgoodwin@redhat.com)
- No keys should be empty list, not null. (dgoodwin@redhat.com)
- Complete happy-path activation key registration. (dgoodwin@redhat.com)
- Remove perm elevation to create owner during registration.
  (dgoodwin@redhat.com)
- Re-enable activation key lookup in consumer registration.
  (dgoodwin@redhat.com)
- Add query param for registration with activation keys. (dgoodwin@redhat.com)
- Stop requiring auth for registration call. (dgoodwin@redhat.com)
- Refactor registration API. (dgoodwin@redhat.com)

* Fri Jul 15 2011 jesus m. rodriguez <jesusr@redhat.com> 0.4.6-1
- require candlepin-deps 0.0.17 (jesusr@redhat.com)
- revert to real bouncycastle (jesusr@redhat.com)
- Better method of checking if user has access to an owner. (dgoodwin@redhat.com)
- Print where we were API JSON after APICrawl. (dgoodwin@redhat.com)
- findbugs: RuleOrderComparator implements Comparator but not Serializable (jesusr@redhat.com)
- Bug 684941 redux (wpoteat@redhat.com)
- findbugs: Possible null pointer dereference (jesusr@redhat.com)
- findbugs: unread field (jesusr@redhat.com)
- findbugs: inefficient use of new Long (jesusr@redhat.com)
- findbugs: makes inefficient use of keySet iterator instead of entrySet iterator (jesusr@redhat.com)
- findbugs: Comparison of String objects using == or != (jesusr@redhat.com)
- Product quantity from multiple pools (wpoteat@redhat.com)

* Wed Jul 13 2011 jesus m. rodriguez <jesusr@redhat.com> 0.4.5-1
- extract strings for translation. (jesusr@redhat.com)
- Rename of pool's product attributes field (mstead@redhat.com)
- refresh the pools when product attribute changes are detected. (jesusr@redhat.com)
- Added product provided attribute to pool (mstead@redhat.com)
- adding unit test for PoolHelper (jesusr@redhat.com)
- Add a magoo user, with RO access to all three test data owners. (dgoodwin@redhat.com)
- Cleanup test data role JSON. (dgoodwin@redhat.com)
- make Loggers static so they'll always be transient. (jesusr@redhat.com)
- remove trailing whitespace (jesusr@redhat.com)
- Make Principal object graph serializable for quartz clustering. (jesusr@redhat.com)
- cleanup comments (alikins@redhat.com)
- Add test products with no arch set, but arch set on child product (alikins@redhat.com)
- Darn you checkstyle (bkearney@redhat.com)
- 718052: More owner nuttiness (bkearney@redhat.com)
- Populate displayname for owners (alikins@redhat.com)
- Add displayName's for owners (alikins@redhat.com)
- make comment readable in 80 cols (jesusr@redhat.com)
- Add the ability to requst all the certificate ids as a zip file (bkearney@redhat.com)
- more ownerinfo enhancements (cduryee@redhat.com)
- Fix a race condition in refresh pools (jbowes@redhat.com)
- Expose updating of users to the API (bkearney@redhat.com)
- Checkstyle (wpoteat@redhat.com)
- Let read-only users fetch owner info. (dgoodwin@redhat.com)
- Fix duplicate user test failures. (dgoodwin@redhat.com)
- Uncomment some tests that were forgotten. (dgoodwin@redhat.com)
- Open GET /owners/key to users with ALL. (dgoodwin@redhat.com)
- Correction to machine stats (wpoteat@redhat.com)
- 703962: clean up the release field (bkearney@redhat.com)
- Add the ability to get all users (bkearney@redhat.com)
- Added spec tests for statistics (wpoteat@redhat.com)
- Checkstyle issues and a change of constructor (wpoteat@redhat.com)
- a few enhancements to ownerinfo (cduryee@redhat.com)
- Added permission for Candlepin stats 2 (wpoteat@redhat.com)
- Candlepin stats 2 (wpoteat@redhat.com)
- Remove /owners/{oid}/users (bkearney@redhat.com)

* Mon Jun 20 2011 Devan Goodwin <dgoodwin@redhat.com> 0.4.4-1
- New multi-owner permissions / roles infrastructure. (dgoodwin@redhat.com)
- Take slashes out of the product names since it causes issues with the get
  commnads (bkearney@redhat.com)
- Addition of on-demand statistic generation. (wpoteat@redhat.com)
- quit after first fail, unless you specify full_run=1 on command line
  (cduryee@redhat.com)
- 700821: Update Consumer Facts Updates Date (mstead@redhat.com)
- Added number of days parameter and value type parameter (wpoteat@redhat.com)
- List owners for user with access ALL. (dgoodwin@redhat.com)
- Fix the location of the import file (bkearney@redhat.com)
- Add a simple puppet script for candlepin. It will probably only work the
  first time (bkearney@redhat.com)
- using "uname.machine" instead of "cpu.architecture" (wpoteat@redhat.com)
- Set up system principal for all pinsetter jobs (jbowes@redhat.com)
- Expect 403 on non-existant consumer UUID, not 401. (dgoodwin@redhat.com)
- return 410 for user deletion, and fix spec that deleted owner then user
  (cduryee@redhat.com)
- return 400 during principal creation for unknown usernames
  (cduryee@redhat.com)
- Remove the AccessControlInterceptor. (dgoodwin@redhat.com)
- only use version 9 of the pgsql jar, since it still supports pgsql 8
  (cduryee@redhat.com)
- Moving consumer atom feeds under owner resource for security reasons.
  (jharris@redhat.com)
- Include Postgresql 9.0 JDBC driver for F15 deployments. (dgoodwin@redhat.com)

* Tue Jun 14 2011 jesus m. rodriguez <jesusr@redhat.com> 0.4.3-1
- require candlepin-deps 0.0.16 (jesusr@redhat.com)

* Mon Jun 13 2011 jesus m. rodriguez <jesusr@redhat.com> 0.4.2-1
- matches current state of custom product (wpoteat@redhat.com)
- Add a unit test for the db being down (bkearney@redhat.com)
- Enhance the Status resource to return false if the DB is down (bkearney@redhat.com)
- Adding job query by owner key. (jharris@redhat.com)
- Improve the url structure for activation keys (bkearney@redhat.com)
- Chanege the name of the activatoin key parameters (bkearney@redhat.com)
- Id generation for products is now based on the lack of an id. The custom tag
  has been removed. (wpoteat@redhat.com)
- unque id is now being generated for content if no id has been specified
  (ddolguik@redhat.com)
- Adding owner key to job status. (jharris@redhat.com)
- only use version 9 of the pgsql jar, since it still supports pgsql 8
  (cduryee@redhat.com)
- OID for Stacking Id (wpoteat@redhat.com)
- Once a day, not once a minute (wpoteat@redhat.com)
- whitespace cleanup (alikins@redhat.com)
- Updates for REST query. Path param of entryType. Query params of
  valueReference, fromDate, and toDate. (wpoteat@redhat.com)
- Missed updating the key. It is in now (bkearney@redhat.com)
- Add messages for the activation key events (bkearney@redhat.com)
- Events were not being persisted. (bkearney@redhat.com)
- Add in an /owners/{oid}/activationKeys api to support data loading
  (bkearney@redhat.com)
- Move activate to its own root resource (bkearney@redhat.com)
- Added key creation events (bkearney@redhat.com)
- Tests fail, but need to wait for the security work before going further
  (bkearney@redhat.com)
- Remove autosubscribe, since we really do not need it (bkearney@redhat.com)
- basic model spec tests. (bkearney@redhat.com)
- Resource for add/removing pools from keys. Unit tests pass, but not the spec
  tests (bkearney@redhat.com)
- Add /owners/foo/activation_keys (bkearney@redhat.com)
- Add Activation Key model, removing all the subscirption token business
  (bkearney@redhat.com)
- Changes for code review. (wpoteat@redhat.com)
- 710141: only show active pools in ownerinfo (jbowes@redhat.com)
- Style changes Added resource for retrieving statistic set based on owner
  (wpoteat@redhat.com)
- Package move for java classes (wpoteat@redhat.com)
- Stat History queries populated as per first set of data. (wpoteat@redhat.com)
- Need to merge the strings before pushing them (bkearney@redhat.com)
- Stop using SNAPSHOT qpid releases. (dgoodwin@redhat.com)
- Fix tomcat permissions in cpsetup. (dgoodwin@redhat.com)
- Buffer was too small (bkearney@redhat.com)
- Extracted the new strings (bkearney@redhat.com)
- Change the project vesion for zanata (bkearney@redhat.com)
- And the rest of the files for the commit (wpoteat@redhat.com)
- First checkin for stats reporting. Only puts number of consumers into DB each
  day. (wpoteat@redhat.com)
- Add the zanata.xml file for translations (bkearney@redhat.com)
- Include Postgresql 9.0 JDBC driver for F15 deployments. (dgoodwin@redhat.com)
- Remove assumptions that we have subs in our db. (dgoodwin@redhat.com)
- Remove prepend on custom product ids (wpoteat@redhat.com)
- Update of test data to remove the use of decimal points in product ids.
  (wpoteat@redhat.com)
- Revert changes to test. Method privilege was reverted. (wpoteat@redhat.com)
- Reverted change to method security. Wrong method for my use.
  (wpoteat@redhat.com)
- Add missed user resource spec tests. (dgoodwin@redhat.com)
- removed Physical and Flex Guest Entitlement counts from content certificate
  (wpoteat@redhat.com)
- removed change to Content class for getContentUrl (wpoteat@redhat.com)
- getContentUrl prepended with /$owner/$env (wpoteat@redhat.com)
- change to test as admin can see owners (wpoteat@redhat.com)
- ignore test to verify this was intentional (jmrodri@gmail.com)
- don't look at /etc/candlepin during unit test runs (jmrodri@gmail.com)
- added privilege for list on owner (wpoteat@redhat.com)
- Implement GET /users/{username}/owners, add verifyUser option to AccessRoles.
  (dgoodwin@redhat.com)
- As a subscription manager client, I would like to know which owner
  I am registered to: Do via API Call. (wpoteat@redhat.com)
- Update hibernate filters for multi-owner users. (dgoodwin@redhat.com)
- As an API user, I would like to be able to query pools resources specific
  to and owner. (wpoteat@redhat.com)

* Thu May 12 2011 jesus m. rodriguez <jesusr@redhat.com> 0.4.1-1
- require candlepin-deps 0.0.15 (jesusr@redhat.com)
- fix checkstyle (jesusr@redhat.com)
- cpbc: allow parsing of encrypted private keys (jesusr@redhat.com)
- apparently checkstyle isn't being run :), fixed errors. (jesusr@redhat.com)
- event text was not in the owner/consumer resources (bkearney@redhat.com)
- Enhance the events calls so that friendly messages are returned.
  (bkearney@redhat.com)
- Allowing the Subject Key Identifier to be injected (bleanhar@redhat.com)
- don't pull in checkstyle deps when creating candlepin-deps.
  (jesusr@redhat.com)
- replace virtual with guest for ownerinfo stats (jbowes@redhat.com)
- Bump version stream to 0.4 for next tag. (dgoodwin@redhat.com)
- use custom bouncycastle (jmrodri@gmail.com)

* Tue May 10 2011 jesus m. rodriguez <jesusr@redhat.com> 0.3.7-1
- Checkstyle Fix (bkearney@redhat.com)
- remove offline now that the repos are back (jesusr@redhat.com)
- put buildr in offline mode for now. Remove this when repo comes back. (jesusr@redhat.com)

* Wed May 04 2011 jesus m. rodriguez <jesusr@redhat.com> 0.3.6-1
- Add public modifiers to OwnerInfo.ConsumptionTypeCounts so jackson can work
  (jbowes@redhat.com)
- pull the checkstyle task out into its own extension (jbowes@redhat.com)
- Spec test for custom product creation (wpoteat@redhat.com)
- Generation of id when the incoming REST designates that the product is
  custom. (wpoteat@redhat.com)
- Add guest/physical consumer counts to OwnerInfo. (dgoodwin@redhat.com)
- Extend ownerInfo to count used entitlements by product family
  (jbowes@redhat.com)
- skip bundler if you're using rpms (jesusr@redhat.com)
- checkstyle fixup (jbowes@redhat.com)
- Add specs for oauth (jbowes@redhat.com)
- Add a new principal and role for trusted external systems (jbowes@redhat.com)
- Update to Ruby scripts to refine the code and fix an issue with the Candlepin
  API calls. (wpoteat@redhat.com)
- Spec test for entitlement import update (wpoteat@redhat.com)
- fix features= spec task option (jbowes@redhat.com)
- Upgrading to buildr 1.4.5 and rspec 2 (jharris@redhat.com)
- use ProductServiceAdapter not ProductCurator. (jesusr@redhat.com)
- Add support for removing a product content association. (dgoodwin@redhat.com)
- checkstyle fixup (jbowes@redhat.com)
- create generated-source dir for po compilation via ant (jbowes@redhat.com)
- Correction to the counts. Any non-specified pool gets put in the 'system'
  bucket. (wpoteat@redhat.com)

* Tue Apr 19 2011 jesus m. rodriguez <jesusr@redhat.com> 0.3.5-1
- require exact version of candlepin rpm (jesusr@redhat.com)
- option to skip the candlepin conf setup step (mmccune@redhat.com)
- make sure we stop tomcat before initalizing the db otherwise you error (mmccune@redhat.com)

* Wed Apr 13 2011 jesus m. rodriguez <jesusr@redhat.com> 0.3.4-1
- fix the rpm to actually generate all of the po files. (jesusr@redhat.com)
- fix it so that the block comments aren't mangled (jesusr@redhat.com)
- Extra system.out.printlns crept in (bkearney@redhat.com)
- Allowing owners and consumer to see consumer entitlements.
  (jharris@redhat.com)
- remove superclass code in the aut layer which is not providing any real value
  (bkearney@redhat.com)
- Missed the new file (bkearney@redhat.com)
- Enhance oauth to accept cp-user or cp-consumer (bkearney@redhat.com)
- up the memory for the build file (bkearney@redhat.com)
- 684350: Stop creating new owners during basic authentication.
  (dgoodwin@redhat.com)
- Adding @InfoProperty for hateoas serialization. (jharris@redhat.com)
- Checkstyle (jharris@redhat.com)
- Adding in Chris' auth work. (jharris@redhat.com)
- Add a /consumers query param to filter on owner key. (dgoodwin@redhat.com)
- Counts of cunsumer types added to OwnerInfo. These counts are based on the
  owner's pools and the requires_consumer_type attribute. (wpoteat@redhat.com)
- Suppress the warning about unused ctor since this is for JPA
  (jmrodri@gmail.com)
- Corrected issue with test. No longer using noop setter. (wpoteat@redhat.com)
- Put in noop setter for test to pass. Looking for better solution.
  (wpoteat@redhat.com)
- ignore some tests for now, gotta figure out how to fix jaxb deserialization.
  (jesusr@redhat.com)
- New  method returns entitlement count for consumer. (wpoteat@redhat.com)
- Moving json prettification to bash so that hudson will work.
  (jharris@redhat.com)
- Ignoring a lot of date-dependent unit tests until we get in a proper fix.
  (jharris@redhat.com)
- Skipping user deletion if service is read-only. (jharris@redhat.com)
- cleanup the output. (jesusr@redhat.com)
- Makes sure that owner is confirmed on consumer update. (wpoteat@wpoteat-
  desktop.usersys.redhat.com)

* Wed Mar 30 2011 jesus m. rodriguez <jesusr@redhat.com> 0.3.3-1
- write dialect to avoid exception (jesusr@redhat.com)

* Tue Mar 29 2011 jesus m. rodriguez <jesusr@redhat.com> 0.3.2-1
- subpackages should require candlepin. (jesusr@redhat.com)
- Allow Ruby API owner creation with a name and key. (dgoodwin@redhat.com)
- update readme to explain what's in this directory. (jesusr@redhat.com)
- changed the way the content type for the return message is handled. Allows
  the default to JSON to work correctly. (wpoteat@redhat.com)

* Mon Mar 28 2011 jesus m. rodriguez <jesusr@redhat.com> 0.3.1-1
- util to generate an export zip that is consuming all products (mmccune@redhat.com)
- 684941: Added exception handling for deletion of product with subscription. (wpoteat@wpoteat.desktop)
- Allow bind with a UUID in Candlepin Ruby API. (dgoodwin@redhat.com)
- candlepin setup script for the RPM. (jesusr@redhat.com)
- Updating transition for checked exceptions in importer. (jharris@redhat.com)
- Sorting generated api output by method name. (jharris@redhat.com)
- Adding apidiff. (jharris@redhat.com)
- Consume full subscription in export script. (dgoodwin@redhat.com)
- Add script to generate export.zip's easily. (dgoodwin@redhat.com)
- Add some notes about implementing other PKIReaders/Utilities (jbowes@redhat.com)
- remove really old useless code (jesusr@redhat.com)
- Fixing up unit tests and checkstyle. (jharris@redhat.com)
- Pulling out all cucumber features and references. (jharris@redhat.com)
- Rename CandlepinPKI* -> BouncyCastlePKI* (jbowes@redhat.com)
- Porting entitlement feature to rspec. (jharris@redhat.com)
- Move bouncycastle code into CandlepinPKIUtility (jbowes@redhat.com)
- Move x509 CRL generation into PKIUtility (jbowes@redhat.com)
- Porting product_cert test to rspec. (jharris@redhat.com)
- Removing direct pool creation/deletion in the resource. (jharris@redhat.com)
- Rolling user_restricted tests into existing restricted_pool rspec.  (jharris@redhat.com)
- Move DER decoding into pkiutility (jbowes@redhat.com)
- use java.security x500principal instead of bouncycastle's version (jbowes@redhat.com)
- Move PEMWriter use into PKIUtility (jbowes@redhat.com)
- Fixing pool update with virt_limit=unlimited (jharris@redhat.com)
- Porting restricted_pool to rspec. (jharris@redhat.com)
- remove bouncycastle use from Util (jbowes@redhat.com)
- Remove direct bouncycastle use from X509ExtensionUtil (jbowes@redhat.com)
- Add oauth Gem requirement. (dgoodwin@redhat.com)
- consistent indentation is important (jesusr@redhat.com)
- fix up eclipse classpath generation (jbowes@redhat.com)
- add tools.jar to eclipse .classpath (jesusr@redhat.com)
- 688707: add X-Candlepin-Version header to all responses. (jesusr@redhat.com)
- remove unused imports (jesusr@redhat.com)
- 670831: use subscription start date for start of certs instead of entitlement
  date (alikins@redhat.com)
- enable unit test for generateUniqueLong. (jmrodri@gmail.com)
- Changed the logic behind serial number generation. See the comment for the
  dirty details. (jeckersb@redhat.com)
- fix broken test (jmrodri@gmail.com)
- unit test pinsetter listeners (jmrodri@gmail.com)
- add more unit tests (jmrodri@gmail.com)
- access method in a static way (jmrodri@gmail.com)
- reformat comment to be 80 columns. (jmrodri@gmail.com)
- remove unused code. (jmrodri@gmail.com)
- fix checkstyle (jmrodri@gmail.com)
- unit test Util.java (jmrodri@gmail.com)
- consistency: use apache.commons.Base64 instead of bouncycastle. (jmrodri@gmail.com)
- move utility method to test where it was used. (jmrodri@gmail.com)
- make method public so I can use it in another test. (jmrodri@gmail.com)
- remove unused code (jmrodri@gmail.com)
- port person consumer from cuke to rspec (jbowes@redhat.com)
- remove /id/ from GET content path. It should be GET /content/{id} (jesusr@redhat.com)
- delete new delete api (jesusr@redhat.com)
- remove useless whitespace (jesusr@redhat.com)
- add DELETE api for content (jesusr@redhat.com)
- Adding pinsetter task to clean up old import records. (jharris@redhat.com)
- port unregister cuke to rspec. (jesusr@redhat.com)
- remove migrate tests, as they need special setups that we don't do (jbowes@redhat.com)
- move jmrodri repo to the top. (jesusr@redhat.com)
- Adding in import record functionality. (jharris@redhat.com)
- Return 409 Conflict if import zip is older than the latest. (jharris@redhat.com)
- cleanup scripts directory (jesusr@redhat.com)
- sharding: make migrations a resource. (jesusr@redhat.com)
- Use GET /owners/ instead of replicate (jesusr@redhat.com)
- separate the @Ignore to make it easier to renable the test. (jesusr@redhat.com)
- 683914: Missing various locales from the translations (bkearney@redhat.com)
- 682930:  Using multiplier for expected quantity in updatePool (jharris@redhat.com)
- Porting register feature. (jharris@redhat.com)
- Ignore test for now (jesusr@redhat.com)
- use .equals for string comparison in poolHelper (jbowes@redhat.com)
- set status code for response object (jesusr@redhat.com)
- remove extra debug logging (jesusr@redhat.com)
- fix checkstyle errors (jesusr@redhat.com)
- Handle pools with source entitlement id's (alikins@redhat.com)
- Adding tools.jar for coverity run. (jharris@redhat.com)
- update javadoc on connect method (jesusr@redhat.com)
- document the client interfaces (jesusr@redhat.com)
- Bump to 0.3 stream for next tag. (dgoodwin@redhat.com)
- rename method and document class. (jesusr@redhat.com)
- rename export to replicate since that's really what it's doing. (jesusr@redhat.com)
- add delete owner to migration flow. (jesusr@redhat.com)
- remove unused keyPairCurator (jesusr@redhat.com)
- remove unused IdentityCertCurator from MigrateOwnerJob. (jesusr@redhat.com)
- associate consumers to their entitlements. (jesusr@redhat.com)
- fix formatting (jesusr@redhat.com)
- new client (jesusr@redhat.com)
- export consumers (jesusr@redhat.com)
- remove tabs! they're evil (jesusr@redhat.com) (alikins@redhat.com)
- add support for migrating consumers (alikins@redhat.com)
- organize items as exportXXX methods to be a bit more clear.  (jesusr@redhat.com)
- reorg execute() test method. (jesusr@redhat.com)
- migrate entitlements and entitlement certificates (jesusr@redhat.com)
- remote @XmlTransient from getId so we can serialize it (jesusr@redhat.com)
- rename constraint (jesusr@redhat.com)
- test export pools (jesusr@redhat.com)
- export pools, prep for export entitlements. (jesusr@redhat.com)
- export Pool and Entitlements as Lists (jesusr@redhat.com)
- adding exportPools and exportEntitlements (jesusr@redhat.com)
- fix test (jesusr@redhat.com)
- make buildUri static and call from execute method. (jesusr@redhat.com)
- rename resp -> rsp (jesusr@redhat.com)
- add get consumers for owner (jesusr@redhat.com)
- Cleaning out broken spec. (jharris@redhat.com)
- Fixing migrate owner unit test. (jharris@redhat.com)
- Several fixes: (jharris@redhat.com)
- reformat for checkstyle (jesusr@redhat.com)
- validate url (jesusr@redhat.com)
- add missing eventOwnerMigrated method (jesusr@redhat.com)
- Adding owner migration event. (jharris@redhat.com)
- build up the uri properly (jesusr@redhat.com)
- debug logging to ensure we capture the inputs properly (jesusr@redhat.com)
- add config entry for webapp defaults to candlepin (jesusr@redhat.com)
- adding sharding to configuration (jesusr@redhat.com)
- added MigrateOwnerJob and CandlepinConnection. (jesusr@redhat.com)
- rename (jesusr@redhat.com)
- export client returns ClientResponse now (jesusr@redhat.com)
- checkstyle: static final instead of final static (jesusr@redhat.com)
- resteasy client (jesusr@redhat.com)
- fix checkstyle (jesusr@redhat.com)
- initial migrate owner job (jesusr@redhat.com)

* Tue Mar 08 2011 jesus m. rodriguez <jesusr@redhat.com> 0.2.11-1
- Fixing virt_limit when no virt.is_guest fact exists. (jharris@redhat.com)
- Adding virt_limit 'unlimited' support (jharris@redhat.com)
- Update OID for latest product changes (provides). (jbowes@redhat.com)

* Mon Mar 07 2011 Devan Goodwin <dgoodwin@redhat.com> 0.2.10-1
- Add content tags support to Ruby APİ. (dgoodwin@redhat.com)
- Add model support for content tags and apply to ent certs.
  (dgoodwin@redhat.com)
- 679472:  Add null check for autosubscribing to non-existant product.
  (jharris@redhat.com)
- 681287: Exclude pools with a rule when listing for a consumer.
  (dgoodwin@redhat.com)
- Move the locales to be language, not language plus country
  (bkearney@redhat.com)
- Fix localization spec for new I18N string. (dgoodwin@redhat.com)
- Cascade through tokens associated with a subscription. (dgoodwin@redhat.com)
- fix grammar in error msg (cduryee@redhat.com)
- Adding return value schema generation to api crawler. (jharris@redhat.com)
- Use underscores in table names per jomara. (cduryee@redhat.com)
- Add test suite for JsPoolRules. (dgoodwin@redhat.com)
- Add in the translated po files. (bkearney@redhat.com)
- Rename pool update DTO class. (dgoodwin@redhat.com)
- Begin pushing logic for updating pools to Javascript. (dgoodwin@redhat.com)
- Refactor creation of virt-only pools. (dgoodwin@redhat.com)
- Push pool creation responsibility to JS rule. (dgoodwin@redhat.com)
- Stop using separate rules in testing, fix multi-entitlement bug.
  (dgoodwin@redhat.com)
- Re-enable logging. (dgoodwin@redhat.com)
- Allow for more than one pool mapping to a subscription. (dgoodwin@redhat.com)

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
