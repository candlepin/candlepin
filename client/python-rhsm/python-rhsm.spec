Name: python-rhsm
Version: 0.94.4
Release: 1%{?dist}

Summary: A Python library to communicate with a Red Hat Unified Entitlement Platform
Group: Development/Libraries
License: GPLv2
Source0: %{name}-%{version}.tar.gz
URL: http://fedorahosted.org/candlepin
BuildRoot: %{_tmppath}/%{name}-%{version}-%{release}-root-%(%{__id_u} -n)

Requires: m2crypto
Requires: python-simplejson
Requires: python-iniparse
BuildArch: noarch

BuildRequires: python2-devel


%description 
A small library for communicating with the REST interface of a Red Hat Unified
Entitlement Platform. This interface is used for the management of system
entitlements, certificates, and access to content.

%prep
%setup -q -n python-rhsm-%{version}

%build
%{__python} setup.py build

%install
rm -rf %{buildroot}
%{__python} setup.py install -O1 --skip-build --root %{buildroot}

%clean
rm -rf %{buildroot}

%files
%defattr(-,root,root,-)
%doc README

%dir %{python_sitelib}/rhsm

%{python_sitelib}/rhsm/*
%{python_sitelib}/rhsm-*.egg-info

%changelog
* Thu Dec 16 2010 Devan Goodwin <dgoodwin@redhat.com> 0.94.4-1
- Add python-rhsm tito.props. (dgoodwin@redhat.com)

* Thu Dec 16 2010 Devan Goodwin <dgoodwin@redhat.com> 0.94.3-1
- Refactor logging. (dgoodwin@redhat.com)
- Add a small README. (dgoodwin@redhat.com)

* Tue Dec 14 2010 Devan Goodwin <dgoodwin@redhat.com> 0.94.2-1
- Remove I18N code. (dgoodwin@redhat.com)
- Spec cleanup. (dgoodwin@redhat.com)
- Cleaning out unused log parsing functions (jharris@redhat.com)
- More tolerant with no rhsm.conf in place. (dgoodwin@redhat.com)
- Switch to python-iniparse. (alikins@redhat.com)

* Fri Dec 10 2010 Devan Goodwin <dgoodwin@redhat.com> 0.94.1-1
- Initial package tagging.

