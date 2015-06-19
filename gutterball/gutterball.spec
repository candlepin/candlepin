%global _binary_filedigest_algorithm 1
%global _source_filedigest_algorithm 1
%global _binary_payload w9.gzdio
%global _source_payload w9.gzdio

# Note that this value is also set to 1 in the Katello Koji
# environments!
%{?fedora:%global reqcpdeps 1}

# Ideally we would just use %{dist} for the deps_suffix, but %dist isn't just
# always the major version.  E.g. rpm --eval "%{dist}" returns ".el6_5" in the
# RHEL 6 candlepin buildroot and ".el6" in other environments.
%{?fedora:%global dist_name fc%{fedora}}
%{?rhel:%global dist_name el%{rhel}}

%global parent_proj candlepin

%if 0%{?fedora} >= 19 || 0%{?rhel} >= 7
%global tomcat tomcat
%else
%global tomcat tomcat6
%endif

Name: gutterball
Version: 1.0.15.1
Release: 1%{?dist}
Summary: Data aggregator for Candlepin

License: GPLv2
URL: http://www.candlepinproject.org
Source0: %{name}-%{version}.tar.gz

BuildRoot: %{_tmppath}/%{name}-%{version}-%{release}-buildroot
BuildArch: noarch

# Universal build requires
BuildRequires: java-devel >= 0:1.6.0
BuildRequires: gettext
BuildRequires: candlepin-common >= 0:1.0.16

%if 0%{?rhel} && 0%{?rhel} < 7
BuildRequires: ant-nodeps >= 0:1.7.0
%else
BuildRequires: ant >= 0:1.7.0
%endif

%if 0%{?reqcpdeps}
%global distlibdir %{_datadir}/%{parent_proj}/%{name}/lib/
BuildRequires: candlepin-deps-gutterball >= 0:0.3.5
%else
BuildRequires: servlet
BuildRequires: gettext-commons
BuildRequires: qpid-java-client >= 0:0.30
BuildRequires: qpid-java-common >= 0:0.30
BuildRequires: resteasy >= 0:2.3.7
BuildRequires: jms
BuildRequires: oauth >= 20100601-4
BuildRequires: scannotation
BuildRequires: javassist >= 3.12.0
BuildRequires: c3p0 >= 0.9.1.2
BuildRequires: postgresql-jdbc
BuildRequires: jta
BuildRequires: apache-mime4j = 0:0.6
BuildRequires: antlr >= 0:2.7.7
BuildRequires: liquibase >= 0:2.0.5

%global jackson_version 0:2.3.0
BuildRequires: jackson-annotations >= %{jackson_version}
BuildRequires: jackson-core >= %{jackson_version}
BuildRequires: jackson-databind >= %{jackson_version}
BuildRequires: jackson-jaxrs-json-provider >= %{jackson_version}
BuildRequires: jackson-module-jaxb-annotations >= %{jackson_version}

BuildRequires: hibernate4-core >= 0:4.2.5
BuildRequires: hibernate4-entitymanager >= 0:4.2.5
BuildRequires: hibernate4-c3p0 >= 0:4.2.5
BuildRequires: hibernate4-validator >= 0:4.2.5
BuildRequires: hibernate-beanvalidation-api >= 1.0.0
BuildRequires: hibernate-jpa-2.0-api >= 1.0.1

# Version dependent build requires
%if 0%{?rhel} == 6
BuildRequires: jpackage-utils
BuildRequires: ant-nodeps >= 0:1.7.0
BuildRequires: jaxb-impl
BuildRequires: google-guice >= 0:3.0
BuildRequires: google-collections >= 0:1.0
BuildRequires: slf4j-api >= 0:1.7.5
BuildRequires: logback-classic
BuildRequires: apache-commons-codec-eap6
BuildRequires: commons-collections >= 3.1
BuildRequires: jakarta-commons-lang
BuildRequires: jakarta-commons-io
%endif

%if 0%{?rhel} >= 7
BuildRequires: javapackages-tools
BuildRequires: glassfish-jaxb
BuildRequires: candlepin-guice >= 0:3.0
BuildRequires: guava >= 0:13.0
BuildRequires: mvn(org.apache.commons:commons-collections)
BuildRequires: mvn(org.apache.commons:commons-io)
BuildRequires: mvn(org.apache.commons:commons-lang)
BuildRequires: mvn(org.slf4j:slf4j-api) >= 0:1.7.4
BuildRequires: mvn(ch.qos.logback:logback-classic)
%endif
%endif # end reqcpdeps

%if !0%{?reqcpdeps}
# Universal requires
Requires: %{tomcat}
Requires: java >= 0:1.6.0
Requires: servlet
Requires: qpid-java-client >= 0:0.30
Requires: qpid-java-common >= 0:0.30
Requires: gettext-commons
Requires: jms
Requires: candlepin-common >= 0:1.0.16
Requires: oauth >= 20100601-4
Requires: resteasy >= 0:2.3.7
Requires: scannotation
Requires: javamail
Requires: javassist >= 3.12.0
Requires: c3p0 >= 0.9.1.2
Requires: postgresql-jdbc
Requires: jta
Requires: apache-mime4j = 0:0.6
Requires: antlr >= 0:2.7.7
Requires: liquibase >= 0:2.0.5

Requires: jackson-annotations >= %{jackson_version}
Requires: jackson-core >= %{jackson_version}
Requires: jackson-databind >= %{jackson_version}
Requires: jackson-jaxrs-json-provider >= %{jackson_version}
Requires: jackson-module-jaxb-annotations >= %{jackson_version}

Requires: hibernate4-core >= 0:4.2.5
Requires: hibernate4-entitymanager >= 0:4.2.5
Requires: hibernate4-c3p0 >= 0:4.2.5
Requires: hibernate4-validator >= 0:4.2.5
Requires: hibernate-beanvalidation-api >= 1.0.0
Requires: hibernate-jpa-2.0-api >= 0:1.0.1

# Version dependent requires
%if 0%{?rhel} == 6
Requires: google-guice >= 0:3.0
Requires: google-collections >= 0:1.0
Requires: slf4j-api >= 0:1.7.5-4
Requires: jcl-over-slf4j >= 0:1.7.5
Requires: logback-classic
Requires: apache-commons-codec-eap6
Requires: jakarta-commons-lang
Requires: jakarta-commons-io
Requires: jaxb-impl
%endif

%if 0%{?rhel} >= 7
Requires: candlepin-guice >= 0:3.0
Requires: glassfish-jaxb
Requires: guava >= 0:13.0
Requires: mvn(org.apache.commons:commons-collections)
Requires: mvn(org.apache.commons:commons-io)
Requires: mvn(org.apache.commons:commons-lang)
Requires: mvn(org.slf4j:slf4j-api)  >= 0:1.7.4
Requires: mvn(org.slf4j:jcl-over-slf4j)  >= 0:1.7.4
Requires: mvn(ch.qos.logback:logback-classic)
Requires: mvn(net.sf.cglib:cglib)
Requires: mvn(asm:asm)
%endif
%endif # end reqcpdeps

%description
Gutterball is a data aggregator for the Candlepin entitlement
engine.

%prep
%setup -q

%build
%{ant} -Ddist.name=%{dist_name} clean %{?reqcpdeps:usecpdeps -Dlib.dir=%{distlibdir}} package

%install
rm -rf %{buildroot}

# Conf files
install -d -m 755 %{buildroot}/%{_sysconfdir}/%{name}/certs/amqp
install -m 640 conf/%{name}.conf %{buildroot}/%{_sysconfdir}/%{name}/%{name}.conf

# Logging
install -d -m 755 %{buildroot}/%{_localstatedir}/log/%{name}
install -d 755 %{buildroot}%{_sysconfdir}/logrotate.d/
install -m 644 conf/logrotate.conf %{buildroot}%{_sysconfdir}/logrotate.d/%{name}

# War file
install -d -m 755 %{buildroot}/%{_localstatedir}/lib/%{tomcat}/webapps/%{name}/
%{__unzip} target/%{name}-%{version}.war -d %{buildroot}/%{_sharedstatedir}/%{tomcat}/webapps/%{name}/

%if !0%{?reqcpdeps}
#remove the copied jars and resymlink
rm %{buildroot}/%{_localstatedir}/lib/%{tomcat}/webapps/%{name}/WEB-INF/lib/*.jar
%{ant} -Ddist.name=%{dist_name} -Dlib.dir=%{buildroot}/%{_sharedstatedir}/%{tomcat}/webapps/%{name}/WEB-INF/lib/ initjars
%endif

%clean
rm -rf %{buildroot}

%files
%defattr(-, root, root)
%doc LICENSE
%config(noreplace) %attr(644, root, root) %{_sysconfdir}/logrotate.d/%{name}
%dir %attr(750, tomcat, tomcat) %{_sysconfdir}/%{name}/certs/amqp
%config(noreplace) %attr(640, tomcat, tomcat) %{_sysconfdir}/%{name}/%{name}.conf

%defattr(644, tomcat, tomcat, 755)
%{_sharedstatedir}/%{tomcat}/webapps/%{name}/*

%attr(775, tomcat, root) %{_localstatedir}/log/%{name}

%changelog
* Fri Jun 19 2015 Michael Stead <mstead@redhat.com> 1.0.15.1-1
- Lifted the limitation on combined filters in the consumer status report
  (crog@redhat.com)
- Checkstyle and PR fixes (crog@redhat.com)
- Replaced the JSON deserializer with converters and event handlers
  (crog@redhat.com)
- Added a deserializer for ComplianceStatus (crog@redhat.com)
- Changed GB model on ComplianceStatus (crog@redhat.com)
- Added additional filtering to the consumer status report (crog@redhat.com)
- Checkstyle changes (crog@redhat.com)
- Added product name filtering to the status trend report (crog@redhat.com)
- 1217058: Fixed broken changeset affecting upgrade (mstead@redhat.com)
- Added consumer filtering to the status trend report (crog@redhat.com)

* Tue Apr 28 2015 Michael Stead <mstead@redhat.com> 1.0.15.0-1
- Adjusted default QPID connection timeouts/retries (mstead@redhat.com)
- Properly close qpid connection on shutdown (mstead@redhat.com)
- Make QPID connection in seperate thread (mstead@redhat.com)

* Wed Apr 01 2015 Devan Goodwin <dgoodwin@rm-rf.ca> 1.0.15-1
- 1207810: Add missing symlink for gutterball and c3p0. (dgoodwin@redhat.com)

* Tue Mar 31 2015 Devan Goodwin <dgoodwin@rm-rf.ca> 1.0.14-1
- Stop logging full message body at INFO level. (dgoodwin@redhat.com)
- Reduce QPID connection logging to debug in various places (mstead@redhat.com)
- Ensure ConsumerState does not exist before persisting (mstead@redhat.com)
- Do not process messages already handled by gutterball (mstead@redhat.com)
- Auto configure AMQP connection retry/wait connections (mstead@redhat.com)

* Wed Mar 18 2015 Devan Goodwin <dgoodwin@rm-rf.ca> 1.0.13-1
- Add jcl-over-slf4j to EL7 gutterball. (awood@redhat.com)
- 1201924: Add missing cglib dependency for gutterball in EL7.
  (awood@redhat.com)
- Include status reasons for consumer_status default results
  (mstead@redhat.com)
- Fixed management_enabled filtering for status_trend (mstead@redhat.com)
- Added management_enabled data to consumer_trend (mstead@redhat.com)
- Added management_enabled filtering to consumer_status report
  (mstead@redhat.com)
- Add new column for tracking management_enabled (mstead@redhat.com)
- Fix checkstyle errors and class name misspelling (wpoteat@redhat.com)
- 1200358: Wait for successful AMQP connection on startup (mstead@redhat.com)

* Tue Feb 17 2015 Devan Goodwin <dgoodwin@rm-rf.ca> 1.0.12-1
- 1190040: Add tomcat dependency for gutterball. (dgoodwin@redhat.com)

* Tue Feb 17 2015 Devan Goodwin <dgoodwin@rm-rf.ca> 1.0.11-1
- Changed custom param to custom_results (mstead@redhat.com)
- GB: Better description on status parameter (mstead@redhat.com)
- Added pagination to the status trend report. (crog@redhat.com)
- Completed migration of common pagination codebase. (crog@redhat.com)
- GB now uses CP's paging system for pagination (crog@redhat.com)

* Mon Feb 02 2015 Devan Goodwin <dgoodwin@rm-rf.ca> 1.0.10-1
- Update rpm deps for qpid 0.30. (dgoodwin@redhat.com)
- Fix SSL hostname verification error after upgrade to Qpid 0.30.
  (dgoodwin@redhat.com)
- Updated fix for connection holding (crog@redhat.com)
- Rename POM files to the Maven prefered 'pom.xml'. (awood@redhat.com)
- Upgrade to QPid 0.30. (awood@redhat.com)
- Connections are now released after reports are run. (crog@redhat.com)
- Paging is no longer enabled by default. (crog@redhat.com)
- Use minimized DTO for report defaults (mstead@redhat.com)
- ConsumerTrendReport no longer returns results for multiple consumers
  (crog@redhat.com)
- Cleaned up pagination and snapshot iterator functionality (crog@redhat.com)
- Added simple pagination (crog@redhat.com)
- Cleaned up curator and added unit tests. (crog@redhat.com)
- Added memory management improvements to consumer status report
  (crog@redhat.com)
- Optimized status trend report query filtering (crog@redhat.com)
- GB: Return correct gutterball version info (mstead@redhat.com)
- Add generated POM files to the repository. (awood@redhat.com)

* Fri Jan 09 2015 Devan Goodwin <dgoodwin@rm-rf.ca> 1.0.9-1
- Fix gutterball ant dep on Fedora. (dgoodwin@redhat.com)

* Fri Jan 09 2015 Devan Goodwin <dgoodwin@rm-rf.ca> 1.0.8-1
- Removed unused object reference (mstead@redhat.com)
- Fixed broken consumer status report query (mstead@redhat.com)
- Fixed GB event status DB upgrade (mstead@redhat.com)
- Make runtime classes for translations come from new common implementation
  (wpoteat@redhat.com)
- Merge all PO and POT files and place under common. (awood@redhat.com)
- Enable language specific text in Gutterball (wpoteat@redhat.com)
- Adjust CA name in Candlepin truststore for Katello installs.
  (awood@redhat.com)

* Fri Dec 12 2014 Devan Goodwin <dgoodwin@rm-rf.ca> 1.0.7-1
- Fixed an issue with data being filtered erroneously (crog@redhat.com)
- Fixed an issue with serializing the "environment" property. (crog@redhat.com)
- Added JSON filtering to GB's model objects. (crog@redhat.com)
- Log the thread ID instead of the request UUID / owner info we don't have in
  gb. (dgoodwin@redhat.com)
- Move to two phase event processing in gutterball. (dgoodwin@redhat.com)
- Store status on event in gutterball. (dgoodwin@redhat.com)
- Drop unused messagetext column. (dgoodwin@redhat.com)
- Allow gutterball message listener to throw exceptions. (dgoodwin@redhat.com)
- Add a toString for gutterball event logging. (dgoodwin@redhat.com)
- Correct DER vs PEM mix-up. (awood@redhat.com)
- Handle strict Katello permissions on password files. (awood@redhat.com)
- Add qpid-cpp-server-store to configure script. (dgoodwin@redhat.com)

* Fri Dec 05 2014 Alex Wood <awood@redhat.com> 1.0.6-1
- Reverted use of DateUtils.parseDateStrictly. (crog@redhat.com)

* Fri Dec 05 2014 Alex Wood <awood@redhat.com> 1.0.5-1
- Add candlepin-deps as a BuildRequires. (awood@redhat.com)
- Fixed GB deploy script (mstead@redhat.com)
- Removed note about bash version requirement. (crog@redhat.com)
- Removed JDBC hash from deploy scripts (crog@redhat.com)
- Added time zone support to the status trend report (crog@redhat.com)
- Clamped dates and relaxed year validation (crog@redhat.com)
- Cleaned up and/or removed extraneous code (crog@redhat.com)
- Added additional date tests and translation wrappers. (crog@redhat.com)
- Added support for extended validations to ParameterDescriptor
  (crog@redhat.com)
- Added the status trend report and API. (crog@redhat.com)

* Mon Nov 24 2014 Alex Wood <awood@redhat.com> 1.0.4-1
- Add missing requires for gutterball. (awood@redhat.com)

* Mon Nov 24 2014 Alex Wood <awood@redhat.com> 1.0.3-1
- Make logging less verbose and more informative. (awood@redhat.com)
- Add missing Gutterball runtime dependencies. (awood@redhat.com)
- Fix missing logging configuration in Gutterball. (awood@redhat.com)
- Fixes to qpid script. (awood@redhat.com)
- Allow qpid-configure.sh to run when invoked indirectly. (awood@redhat.com)
- Spec file fixes. (awood@redhat.com)
- Fix mistakes in Qpid certs deploy script. (awood@redhat.com)
- Add some sample values in default gutterball.conf (awood@redhat.com)
- Updated translations. (dgoodwin@redhat.com)

* Wed Nov 19 2014 Devan Goodwin <dgoodwin@rm-rf.ca> 1.0.2-1
- Die if we hit any Guice errors. (awood@redhat.com)
- Fix deploy script. (awood@redhat.com)
- Added OAuth support to Gutterball's API (crog@redhat.com)
- Aligned hibernate types with those specified by liquibase changeset
  (mstead@redhat.com)
- Sync liquibase column name with that specifed in annotation.
  (mstead@redhat.com)

* Mon Nov 17 2014 Alex Wood <awood@redhat.com> 1.0.1-1
- Initial packaging.

* Tue Jun 03 2014 Alex Wood <awood@redhat.com> 1.0.0-1
- Initial packaging
