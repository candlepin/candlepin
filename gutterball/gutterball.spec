%global _binary_filedigest_algorithm 1
%global _source_filedigest_algorithm 1
%global _binary_payload w9.gzdio
%global _source_payload w9.gzdio

# This is technically just a temporary directory to get us through
# the compilation phase. It is later destroyed and the spec file will
# re-call initjars with the correct destination for both tomcat and jboss.
%global distlibdir %{buildroot}/%{_tmppath}/distlibdir/

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
Version: 1.0.3
Release: 1%{?dist}
Summary: Data aggregator for Candlepin

License: GPLv2
URL: http://www.candlepinproject.org
Source0: %{name}-%{version}.tar.gz

BuildRoot: %{_tmppath}/%{name}-%{version}-%{release}-buildroot
BuildArch: noarch

# Universal build requires
BuildRequires: java-devel >= 0:1.6.0
BuildRequires: ant >= 0:1.7.0
BuildRequires: gettext

%if 0%{?reqcpdeps}
%global distlibdir %{_datadir}/%{parent_proj}/%{name}/lib/
%global usecpdeps "usecpdeps"
%else
BuildRequires: servlet
BuildRequires: gettext-commons
BuildRequires: qpid-java-client >= 0:0.22
BuildRequires: qpid-java-common >= 0:0.22
BuildRequires: resteasy >= 0:2.3.7
BuildRequires: candlepin-common >= 0:1.0.16
BuildRequires: jms
BuildRequires: oauth >= 20100601-4
BuildRequires: scannotation
BuildRequires: javassist >= 3.12.0
BuildRequires: c3p0 >= 0.9.1.2
BuildRequires: postgresql-jdbc
BuildRequires: jta
BuildRequires: apache-mime4j = 0:0.6
BuildRequires: antlr >= 0:2.7.7

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
Requires: java >= 0:1.6.0
Requires: servlet
Requires: qpid-java-client >= 0:0.22
Requires: qpid-java-common >= 0:0.22
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
ant -Ddist.name=%{dist_name} clean %{?reqcpdeps:usecpdeps} %{?reqcpdeps:-Dlib.dir=%{distlibdir}} package

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
ant -Ddist.name=%{dist_name} -Dlib.dir=%{buildroot}/%{_sharedstatedir}/%{tomcat}/webapps/%{name}/WEB-INF/lib/ initjars
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
