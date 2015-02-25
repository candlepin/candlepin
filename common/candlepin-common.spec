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
Version: 1.0.21
Release: 1%{?dist}
URL: http://www.candlepinproject.org
Source: %{name}-%{version}.tar.gz
BuildRoot: %{_tmppath}/%{name}-%{version}-%{release}-buildroot
BuildArch: noarch

# Build deps
BuildRequires: java-devel >= 1:1.6.0
BuildRequires: ant >= 0:1.7.0
BuildRequires: gettext

%if 0%{?rhel} && 0%{?rhel} < 7
BuildRequires: ant-nodeps >= 0:1.7.0
%endif

%if 0%{?reqcpdeps}
%global distlibdir %{_datadir}/%{parent_proj}/common/lib/
%global usecpdeps "usecpdeps"
BuildRequires: candlepin-deps-common >= 0:0.3.1
%else
BuildRequires: resteasy >= 0:2.3.7
BuildRequires: jakarta-commons-io
BuildRequires: jakarta-commons-lang
BuildRequires: servlet
BuildRequires: gettext-commons
BuildRequires: jta
BuildRequires: hibernate-beanvalidation-api >= 1.0.0
BuildRequires: hibernate-jpa-2.0-api >= 1.0.1
BuildRequires: oauth >= 20100601-4

%global jackson_version 0:2.3.0
BuildRequires: jackson-annotations >= %{jackson_version}
BuildRequires: jackson-core >= %{jackson_version}
BuildRequires: jackson-databind >= %{jackson_version}
BuildRequires: jackson-jaxrs-json-provider >= %{jackson_version}
BuildRequires: jackson-module-jaxb-annotations >= %{jackson_version}

%if 0%{?rhel} == 6
BuildRequires: apache-commons-codec-eap6
BuildRequires: google-guice >= 0:3.0
BuildRequires: google-collections >= 0:1.0
BuildRequires: slf4j-api >= 0:1.7.5
BuildRequires: jcl-over-slf4j >= 0:1.7.5
BuildRequires: httpclient >= 0:4.1.2
BuildRequires: javax.inject
BuildRequires: logback-classic
BuildRequires: logback-core
%endif

%if 0%{?rhel} >= 7
BuildRequires: apache-commons-codec-eap6
BuildRequires: apache-commons-collections
BuildRequires: candlepin-guice >= 0:3.0
BuildRequires: guava >= 0:13.0
BuildRequires: mvn(org.apache.httpcomponents:httpclient) >= 0:4.1.2
BuildRequires: mvn(org.slf4j:slf4j-api)  >= 0:1.7.4
BuildRequires: mvn(org.slf4j:jcl-over-slf4j)  >= 0:1.7.4
BuildRequires: mvn(ch.qos.logback:logback-classic)
BuildRequires: mvn(javax.inject:javax.inject)
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
%if 0%{?rhel} == 6
Requires: google-guice >= 0:3.0
Requires: google-collections >= 0:1.0
Requires: slf4j-api >= 0:1.7.5-4
Requires: jcl-over-slf4j >= 0:1.7.5
Requires: httpclient >= 0:4.1.2
Requires: apache-commons-codec-eap6
Requires: javax.inject
Requires: logback-classic
Requires: logback-core
%endif

%if 0%{?rhel} >= 7
Requires: apache-commons-codec-eap6
Requires: apache-commons-collections
Requires: candlepin-guice >= 0:3.0
Requires: guava >= 0:13.0
Requires: mvn(org.apache.httpcomponents:httpclient) >= 0:4.1.2
Requires: mvn(org.slf4j:slf4j-api)  >= 0:1.7.4
Requires: mvn(org.slf4j:jcl-over-slf4j)  >= 0:1.7.4
Requires: mvn(ch.qos.logback:logback-classic)
Requires: mvn(javax.inject:javax.inject)
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
Requires: gettext-commons
Requires: jta
Requires: hibernate-beanvalidation-api >= 1.0.0
Requires: hibernate-jpa-2.0-api >= 1.0.1
Requires: oauth >= 20100601-4

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
install -d -m 755 %{buildroot}/%{_datadir}/%{parent_proj}/lib/
install -d -m 755 %{buildroot}/%{_datadir}/%{parent_proj}/gutterball/lib/
install -m 644 target/%{name}-%{version}.jar %{buildroot}/%{_javadir}
ln -s %{_javadir}/%{name}-%{version}.jar %{buildroot}/%{_javadir}/%{name}.jar
ln -s %{_javadir}/%{name}-%{version}.jar %{buildroot}/%{_datadir}/%{parent_proj}/lib/%{name}.jar
ln -s %{_javadir}/%{name}-%{version}.jar %{buildroot}/%{_datadir}/%{parent_proj}/gutterball/lib/%{name}.jar

%clean
rm -rf %{buildroot}
rm -rf %{_tmppath}/distlibdir

%files
%defattr(644,root,root,775)
%{_javadir}/%{name}-%{version}.jar
%{_javadir}/%{name}.jar
%{_datadir}/%{parent_proj}/lib/%{name}.jar
%{_datadir}/%{parent_proj}/gutterball/lib/%{name}.jar

%changelog
* Tue Feb 17 2015 Devan Goodwin <dgoodwin@rm-rf.ca> 1.0.21-1
- Changed property filtering to use JSON path instead of reflection.
  (crog@redhat.com)
- Added JSON formatting to ServletLogger (crog@redhat.com)
- Completed migration of common pagination codebase. (crog@redhat.com)
- GB now uses CP's paging system for pagination (crog@redhat.com)

* Mon Feb 02 2015 Devan Goodwin <dgoodwin@rm-rf.ca> 1.0.20-1
- Rename POM files to the Maven prefered 'pom.xml'. (awood@redhat.com)
- Add generated POM files to the repository. (awood@redhat.com)
- Logging touchups. (dgoodwin@redhat.com)

* Fri Jan 09 2015 Devan Goodwin <dgoodwin@rm-rf.ca> 1.0.19-1
- Fix oauth dependency for cpdeps builds. (dgoodwin@redhat.com)

* Fri Jan 09 2015 Devan Goodwin <dgoodwin@rm-rf.ca> 1.0.18-1
- Updated translations from Zanata. (dgoodwin@redhat.com)
- Move all current translations to common. (awood@redhat.com)
- Enable language specific text in Gutterball (wpoteat@redhat.com)

* Thu Dec 11 2014 Devan Goodwin <dgoodwin@rm-rf.ca> 1.0.17-1
- Fixed an issue with data being filtered erroneously (crog@redhat.com)
- Fixed an issue with serializing the "environment" property. (crog@redhat.com)
- Added JSON filtering to GB's model objects. (crog@redhat.com)

* Thu Nov 20 2014 Alex Wood <awood@redhat.com> 1.0.16-1
- Spec file fixes. (awood@redhat.com)
- Updated translations. (dgoodwin@redhat.com)

* Wed Nov 19 2014 Devan Goodwin <dgoodwin@rm-rf.ca> 1.0.15-1
- Added OAuth support to Gutterball's API (crog@redhat.com)

* Fri Nov 14 2014 Alex Wood <awood@redhat.com> 1.0.14-1
- Remove dependency on Guava. (awood@redhat.com)
- 1162297: Remove the vendor tag from the spec files (bkearney@redhat.com)
- Refactor Configuration implementation to be more consistent.
  (awood@redhat.com)
- Add Hibernate Validation to Gutterball. (awood@redhat.com)
- Correct misspelled class name. (awood@redhat.com)

* Wed Nov 05 2014 Devan Goodwin <dgoodwin@rm-rf.ca> 1.0.13-1
- Fix el6 guice persist dependency. (dgoodwin@redhat.com)

* Wed Nov 05 2014 Devan Goodwin <dgoodwin@rm-rf.ca> 1.0.12-1
- Fix EL6 logback deps. (dgoodwin@redhat.com)

* Wed Nov 05 2014 Devan Goodwin <dgoodwin@rm-rf.ca> 1.0.11-1
- Correct logback dependencies. (dgoodwin@redhat.com)
- Remove redundant imports. (awood@redhat.com)
- Initial commit for bringing hibernate into GB (mstead@redhat.com)

* Thu Oct 30 2014 Devan Goodwin <dgoodwin@rm-rf.ca> 1.0.10-1
- Update candlepin-common spec to work around JDK 1.8 as well.
  (dgoodwin@redhat.com)

* Tue Oct 28 2014 Devan Goodwin <dgoodwin@rm-rf.ca> 1.0.9-1
- Updated translations. (dgoodwin@redhat.com)
- Fix issue with JPAConfigParser not returning entire config.
  (awood@redhat.com)
- Add logging to runtime exceptions in Configuration classes.
  (awood@redhat.com)
- Make merge static & implementation dependent. (jesusr@redhat.com)
- remove Candlepin's Config class & switch to using common Configuration
  (jmrodri@gmail.com)
- Major config refactoring (jmrodri@gmail.com)
- Make it possible for CandlepinExceptions to not log from the
  CandlepinExceptionMapper. This is useful when a lengthy stacktrace is
  unnecessary, say for example a failed login attempt (dcrissma@redhat.com)
- Modified paths displayed in gettext output. (crog@redhat.com)
- 1142824: Fixed Java gettext extract task (crog@redhat.com)

* Fri Oct 03 2014 jesus m. rodriguez <jesusr@redhat.com> 1.0.8-1
- Updated translations. (dgoodwin@redhat.com)

* Fri Sep 12 2014 jesus m. rodriguez <jesusr@redhat.com> 1.0.7-1
- rhel not defined on fedora (jesusr@redhat.com)
- Updated translations. (dgoodwin@redhat.com)
- keep da_popo alive. It's part of our history. (jesusr@redhat.com)
- Translations on Gutterball and Common (wpoteat@redhat.com)

* Wed Sep 10 2014 jesus m. rodriguez <jesusr@redhat.com> 1.0.6-1
- Revert "remove mkdir" (jesusr@redhat.com)

* Tue Sep 09 2014 jesus m. rodriguez <jesusr@redhat.com> 1.0.5-1
- rhel6 requires ant-nodeps. (jesusr@redhat.com)

* Tue Sep 09 2014 jesus m. rodriguez <jesusr@redhat.com> 1.0.4-1
- use candlepin-deps-common instead of *-deps (jesusr@redhat.com)

* Tue Sep 09 2014 jesus m. rodriguez <jesusr@redhat.com> 1.0.3-1
- remove the mkdir (jesusr@redhat.com)

* Tue Sep 09 2014 jesus m. rodriguez <jesusr@redhat.com> 1.0.2-1
- Update candlepin-deps (jesusr@redhat.com)
- common: put jar in cpdeps location as well (jesusr@redhat.com)
- Move REST exceptions to common package (wpoteat@redhat.com)
- Reorganize all imports according to the Candlepin import order. (awood@redhat.com)

* Wed Aug 06 2014 jesus m. rodriguez <jesusr@redhat.com> 1.0.1-1
- Package contains common code used by gutterball and candlepin.
- new package built with tito

* Fri Aug 1 2014 jesus m. rodriguez <jesusr@redhat.com> 1.0.0-1
- Initial build

