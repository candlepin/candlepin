Name: python-rhsm
Version: 0.94.1
Release: 1%{?dist}

Summary: A Python library to communicate with a Red Hat Unified Entitlement Platform
Group: Development/Libraries
License: GPLv2
Requires: m2crypto
Requires: python-simplejson
BuildArch: noarch

Source0: %{name}-%{version}.tar.gz

URL: http://fedorahosted.org/candlepin
BuildRoot: %{_tmppath}/%{name}-%{version}-%{release}-root-%(%{__id_u} -n)

BuildRequires: python2-devel
BuildRequires: gettext
BuildRequires: intltool


%description 
A small library for communicating with the REST interface of a Red Hat Unified
Entitlement Platform. This interface is used for the management of system
entitlements, certificates, and access to content.

%prep
%setup -q -n python-rhsm-%{version}

%build
%{__python} setup.py build

%install
rm -rf $RPM_BUILD_ROOT
%{__python} setup.py install -O1 --skip-build --root $RPM_BUILD_ROOT

%clean
rm -rf $RPM_BUILD_ROOT

%files
%defattr(-,root,root,-)
%attr(755,root,root) %dir %{_var}/log/rhsm

%dir %{python_sitelib}/rhsm

%files 
%{python_sitelib}/rhsm/connection.py*
%{python_sitelib}/rhsm/logutil.py*
%{python_sitelib}/rhsm/config.py*
%{python_sitelib}/rhsm/__init__.py*
%{python_sitelib}/rhsm-*.egg-info

%changelog
* Fri Dec 10 2010 Devan Goodwin <dgoodwin@redhat.com> 0.94.1-1
- new package built with tito

* Fri Dec 10 2010 Devan Goodwin <dgoodwin@redhat.com> 
- Initial tagging as separate python-rhsm package.
