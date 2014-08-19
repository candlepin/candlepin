%global _binary_filedigest_algorithm 1
%global _source_filedigest_algorithm 1
%global _binary_payload w9.gzdio
%global _source_payload w9.gzdio

# Ideally we would just use %{dist} for the deps_suffix, but %dist isn't just
# always the major version.  E.g. rpm --eval "%{dist}" returns ".el6_5" in the
# RHEL 6 candlepin buildroot and ".el6" in other environments.
%{?fedora:%global dist_name fc%{fedora}}
%{?rhel:%global dist_name el%{rhel}}

%if 0%{?fedora} >= 19 || 0%{?rhel} >= 7
%global tomcat tomcat
%else
%global tomcat tomcat6
%endif

Name: gutterball
Version: 1.0.0
Release: 1%{?dist}
Summary: Data aggregator for Candlepin

License: GPLv2
URL: http://www.candlepinproject.org
Source0: %{name}-%{version}.tar.gz

BuildRoot: %{_tmppath}/%{name}-%{version}-%{release}-buildroot
Vendor: Red Hat, Inc.
BuildArch: noarch

# Universal build requires
BuildRequires: java-devel >= 0:1.6.0
BuildRequires: gettext
BuildRequires: servlet
BuildRequires: gettext-commons
BuildRequires: qpid-java-client >= 0:0.22
BuildRequires: qpid-java-common >= 0:0.22
BuildRequires: resteasy >= 0:2.3.7
BuildRequires: mongodb24-mongo-java-driver
BuildRequires: mongodb24-mongo-java-driver-bson
BuildRequires: candlepin-common
BuildRequires: jms
%global jackson_version 0:2.3.0
BuildRequires: jackson-annotations >= %{jackson_version}
BuildRequires: jackson-core >= %{jackson_version}
BuildRequires: jackson-databind >= %{jackson_version}
BuildRequires: jackson-jaxrs-json-provider >= %{jackson_version}
BuildRequires: jackson-module-jaxb-annotations >= %{jackson_version}

# Version dependent build requires
%if 0%{?rhel} == 6
BuildRequires: jpackage-utils
BuildRequires: ant-nodeps >= 0:1.7.0
BuildRequires: jaxb-impl
BuildRequires: google-guice >= 0:3.0
BuildRequires: slf4j-api >= 0:1.7.5
BuildRequires: logback-classic
BuildRequires: apache-commons-codec-eap6
BuildRequires: jakarta-commons-lang
%endif

%if 0%{?rhel} == 7
BuildRequires: javapackages-tools
BuildRequires: ant >= 0:1.7.0
BuildRequires: glassfish-jaxb
BuildRequires: candlepin-guice >= 0:3.0
BuildRequires: mvn(org.apache.httpcomponents:httpclient) >= 0:4.1.2
BuildRequires: mvn(org.apache.commons:commons-lang)
BuildRequires: mvn(org.slf4j:slf4j-api) >= 0:1.7.4
BuildRequires: mvn(ch.qos.logback:logback-classic)
%endif

# Universal requires
Requires: java >= 0:1.6.0
Requires: servlet
Requires: qpid-java-client >= 0:0.22
Requires: qpid-java-common >= 0:0.22
Requires: gettext-commons
Requires: mongodb24-mongo-java-driver
Requires: mongodb24-mongo-java-driver-bson
Requires: jms
Requires: candlepin-common

# Version dependent requires
%if 0%{?rhel} == 6
Requires: google-guice >= 0:3.0
Requires: slf4j-api >= 0:1.7.5-4
Requires: logback-classic
Requires: apache-commons-codec-eap6
Requires: jakarta-commons-lang
%endif

%if 0%{?rhel} == 7
Requires: candlepin-guice >= 0:3.0
Requires: mvn(org.apache.httpcomponents:httpclient) >= 0:4.1.2
Requires: mvn(org.apache.commons:commons-lang)
Requires: mvn(org.slf4j:slf4j-api)  >= 0:1.7.4
Requires: mvn(org.slf4j:jcl-over-slf4j)  >= 0:1.7.4
Requires: mvn(ch.qos.logback:logback-classic)
Requires: mvn(net.sf.cglib:cglib)
Requires: mvn(asm:asm)
%endif

%description
Gutterball is a data aggregator for the Candlepin entitlement
engine.

%prep
%setup -q

%build
ant -Ddist.name=%{dist_name} clean package

%install
rm -rf %{buildroot}

# Conf files
install -d -m 755 %{buildroot}/%{_sysconfdir}/%{name}/certs/amqp
install -d -m 640 %{buildroot}/%{_sysconfdir}/%{name}/%{name}.conf

# Logging
install -d -m 755 %{buildroot}/%{_localstatedir}/log/%{name}
install -d 755 %{buildroot}%{_sysconfdir}/logrotate.d/
install -m 644 conf/logrotate.conf %{buildroot}%{_sysconfdir}/logrotate.d/%{name}

# War file
install -d -m 755 %{buildroot}/%{_localstatedir}/lib/%{tomcat}/webapps/%{name}/
%{__unzip} target/%{name}-%{version}.war -d %{buildroot}/%{_sharedstatedir}/%{tomcat}/webapps/%{name}/

#remove the copied jars and resymlink
rm %{buildroot}/%{_localstatedir}/lib/%{tomcat}/webapps/%{name}/WEB-INF/lib/*.jar
ant -Ddist.name=%{dist_name} -Dlib.dir=%{buildroot}/%{_sharedstatedir}/%{tomcat}/webapps/%{name}/WEB-INF/lib/ initjars

%clean
rm -rf %{buildroot}

%files
%defattr(-, root, root)
%doc LICENSE
%dir %attr(750, root, root) %{_sysconfdir}/%{name}/certs/amqp
%config(noreplace) %attr(644, root, root) %{_sysconfdir}/logrotate.d/%{name}
%config(noreplace) %attr(640, root, root) %{_sysconfdir}/%{name}/%{name}.conf

%defattr(644, tomcat, tomcat, 755)
%{_sharedstatedir}/%{tomcat}/webapps/%{name}/*
%{_localstatedir}/log/%{name}

%changelog
* Tue Jun 03 2014 Alex Wood <awood@redhat.com> 1.0.0-1
- Initial packaging
