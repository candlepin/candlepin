Summary: Candlepin
Name: candlepin
Source: candlepin-bin.tar.gz
Version: 1.0.0
Release: %{_release}
BuildRoot: %{_tmppath}/%{name}-%{version}-%{release}-buildroot
Group: Internet/Applications
Vendor: Red Hat, Inc
URL: http://fedorahosted.org/candlepin
License: GLPv2
Requires: jbossas >= 4.3
BuildArch: noarch

%global _binary_filedigest_algorithm 1
%global _source_filedigest_algorithm 1
%global _binary_payload w9.gzdio
%global _source_payload w9.gzdio

%define __jar_repack %{nil}

%description
Candlepin Entitlement Management

%prep

%setup -c

%build

%install
# Cleaning up the build root
rm -rf $RPM_BUILD_ROOT

# Create the directory structure required to lay down our files
mkdir -p $RPM_BUILD_ROOT/%{_container}

# Copy the contents of the candlepin-bin.tar.gz to /var/lib/tomcat5/webapps (setup -c explodes the tar.gz automatically)
cp -R . $RPM_BUILD_ROOT/%{_container}

%clean
rm -rf $RPM_BUILD_ROOT

%files
%defattr(-,jboss,jboss,-)
/var/lib/jbossas/server/production/deploy/candlepin-1.0.0.war

%doc
