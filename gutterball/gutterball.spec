Name: gutterball
Version: 1.0.0
Release: 1%{?dist}
Summary: Data aggregator for Candlepin

License: GPLv2
URL: http://www.candlepinproject.org
Source0: %{name}-%{version}.tar.gz

BuildRequires: java-devel >= 0:1.6.0
BuildRequires: ant >= 0:1.7.0

Requires: java >= 0:1.6.0

%description
Gutterball is a data aggregator for the Candlepin entitlement
engine.

%prep
%setup -q

%build
# ant

%install
rm -rf %{buildroot}

%files
%defattr(-, root, root)
%doc LICENSE

%changelog
* Tue Jun 03 2014 Alex Wood <awood@redhat.com> 1.0.0-1
- Initial packaging
