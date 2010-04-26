%global _binary_filedigest_algorithm 1
%global _source_filedigest_algorithm 1
%global _binary_payload w9.gzdio
%global _source_payload w9.gzdio

Name: candlepin
Summary: Candlepin is an open source entitlement management system.
Group: Internet/Applications
License: GLPv2
Version: 0.0.4
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

%clean
rm -rf $RPM_BUILD_ROOT

%files
%config(noreplace) %{_sysconfdir}/%{name}/%{name}.conf

%files jboss
%defattr(-,jboss,jboss,-)
%{_localstatedir}/lib/jbossas/server/production/deploy/%{name}*

%files tomcat5
%defattr(644,tomcat,tomcat,775)
%{_localstatedir}/lib/tomcat5/webapps/%{name}*

%files tomcat6
%defattr(644,tomcat,tomcat,775)
%{_localstatedir}/lib/tomcat6/webapps/%{name}*

%changelog
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
