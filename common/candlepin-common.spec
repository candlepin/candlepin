%global _binary_filedigest_algorithm 1
%global _source_filedigest_algorithm 1
%global _binary_payload w9.gzdio
%global _source_payload w9.gzdio

# This is technically just a temporary directory to get us through
# the compilation phase. It is later destroyed and the spec file will
# re-call initjars with the correct destination for both tomcat and jboss.
%global distlibdir %{buildroot}/%{_tmppath}/distlibdir/

%{?fedora:%global reqcpdeps 1}

# Ideally we would just use %{dist} for the deps_suffix, but %dist isn't just always
# the major version.  E.g. rpm --eval "%{dist}" returns ".el6_5" in the RHEL 6
# candlepin buildroot and ".el6" in other environments.
%{?fedora:%global deps_suffix fc%{fedora}}
%{?rhel:%global deps_suffix el%{rhel}}

%global parent_proj candlepin

Name: %{parent_proj}-common
Summary: Common code for Candlepin and related projects
License: GPLv2
Version: 1.0.1
Release: 1%{?dist}
URL: http://www.candlepinproject.org
Source: %{name}-%{version}.tar.gz
BuildRoot: %{_tmppath}/%{name}-%{version}-%{release}-buildroot
Vendor: Red Hat, Inc.
BuildArch: noarch

# Build deps
BuildRequires: java-devel >= 0:1.6.0
BuildRequires: ant >= 0:1.7.0
BuildRequires: gettext

%if 0%{?reqcpdeps}
%global distlibdir %{_datadir}/%{parent_proj}/common/lib/
%global usecpdeps "usecpdeps"
%else
BuildRequires: resteasy >= 0:2.3.7
BuildRequires: jakarta-commons-io
BuildRequires: jakarta-commons-lang
BuildRequires: servlet

%global jackson_version 0:2.3.0
BuildRequires: jackson-annotations >= %{jackson_version}
BuildRequires: jackson-core >= %{jackson_version}
BuildRequires: jackson-databind >= %{jackson_version}
BuildRequires: jackson-jaxrs-json-provider >= %{jackson_version}
BuildRequires: jackson-module-jaxb-annotations >= %{jackson_version}

%if 0%{?rhel} >= 7
BuildRequires: apache-commons-codec-eap6
BuildRequires: apache-commons-collections
BuildRequires: mvn(org.apache.httpcomponents:httpclient) >= 0:4.1.2
BuildRequires: mvn(org.slf4j:slf4j-api)  >= 0:1.7.4
BuildRequires: mvn(org.slf4j:jcl-over-slf4j)  >= 0:1.7.4
BuildRequires: mvn(ch.qos.logback:logback-classic)
BuildRequires: mvn(javax.inject:javax.inject)
%endif

%if 0%{?rhel} < 7
BuildRequires: ant-nodeps >= 0:1.7.0
BuildRequires: apache-commons-codec-eap6
BuildRequires: slf4j-api >= 0:1.7.5
BuildRequires: jcl-over-slf4j >= 0:1.7.5
BuildRequires: httpclient >= 0:4.1.2
BuildRequires: javax.inject
%endif

%if 0%{?fedora}
BuildRequires: slf4j >= 0:1.7.5
#BuildRequires: httpclient >= 0:4.1.2
BuildRequires: jakarta-commons-httpclient
BuildRequires: apache-commons-codec
BuildRequires: javax.inject
%endif
%endif # end reqcpdeps

%if !0%{?reqcpdeps}
# Runtime deps
%if 0%{?rhel} >= 7
Requires: apache-commons-codec-eap6
Requires: apache-commons-collections
Requires: mvn(org.apache.httpcomponents:httpclient) >= 0:4.1.2
Requires: mvn(org.slf4j:slf4j-api)  >= 0:1.7.4
Requires: mvn(org.slf4j:jcl-over-slf4j)  >= 0:1.7.4
Requires: mvn(ch.qos.logback:logback-classic)
Requires: mvn(javax.inject:javax.inject)
%endif

%if 0%{?rhel} < 7
Requires: slf4j-api >= 0:1.7.5-4
Requires: jcl-over-slf4j >= 0:1.7.5
Requires: httpclient >= 0:4.1.2
Requires: apache-commons-codec-eap6
Requires: javax.inject
%endif

%if 0%{?fedora}
Requires: slf4j >= 0:1.7.5
Requires: apache-commons-codec
#Requires: httpclient >= 0:4.1.2
Requires: jakarta-commons-httpclient
Requires: javax.inject
%endif

Requires: resteasy >= 0:2.3.7
Requires: jackson-annotations >= %{jackson_version}
Requires: jackson-core >= %{jackson_version}
Requires: jackson-databind >= %{jackson_version}
Requires: jackson-jaxrs-json-provider >= %{jackson_version}
Requires: jackson-module-jaxb-annotations >= %{jackson_version}
Requires: jakarta-commons-io
Requires: jakarta-commons-lang
%endif # end reqcpdeps

%description
Common code for Candlepin and related projects

%prep
%setup -q
mkdir -p %{distlibdir}

%build
ant %{?rhel:-Ddeps.file=deps/%{deps_suffix}.txt} -Ddistlibdir=%{distlibdir} clean %{?reqcpdeps:usecpdeps} package

%install
rm -rf %{buildroot}
install -d -m 755 %{buildroot}/%{_javadir}
install -m 644 target/%{name}-%{version}.jar %{buildroot}/%{_javadir}
ln -s %{_javadir}/%{name}-%{version}.jar %{buildroot}/%{_javadir}/%{name}.jar

%clean
rm -rf %{buildroot}
rm -rf %{_tmppath}/distlibdir

%files
%defattr(644,root,root,775)
%{_javadir}/%{name}-%{version}.jar
%{_javadir}/%{name}.jar

%changelog
* Wed Aug 06 2014 jesus m. rodriguez <jesusr@redhat.com> 1.0.1-1
- Package contains common code used by gutterball and candlepin.
- new package built with tito

* Fri Aug 1 2014 jesus m. rodriguez <jesusr@redhat.com> 1.0.0-1
- Initial build

