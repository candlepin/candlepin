Name: candlepin
Summary: Candlepin is an open source entitlement management system.
Group: Internet/Applications
License: GLPv2
Version: 1.0.1
Release: 1
URL: http://fedorahosted.org/candlepin
# Source0: https://fedorahosted.org/releases/c/a/candlepin/%{name}-%{version}.tar.gz
Source: candlepin-bin.tar.gz
BuildRoot: %{_tmppath}/%{name}-%{version}-%{release}-buildroot
Vendor: Red Hat, Inc
BuildArch: noarch

Requires: candlepin-webapp
BuildRequires: java >= 0:1.6.0
BuildRequires: rubygem-buildr

%description
Candlepin is an open source entitlement management system.

#%package tomcat5
#Summary: Candlepin web application for tomcat5
#Requires: tomcat5 >= 5.5
#Provides: candlepin-webapp

#%description tomcat5
#Candlepin web application for tomcat5

%package tomcat6
Summary: Candlepin web application for tomcat6
Requires: tomcat6
Provides: candlepin-webapp

%description tomcat6
Candlepin web application for tomcat6

#%package jboss
#Summary: Candlepin web application for jboss
#Provides: candlepin-webapp

#%description jboss
#Candlepin web application for jboss

%prep
%setup -c

%build
buildr clean test=no package

%install
# Cleaning up the build root
#rm -rf $RPM_BUILD_ROOT

# Create the directory structure required to lay down our files
install -d -m 755 $RPM_BUILD_ROOT/%{_localstatedir}/lib/tomcat5/webapps/
install -d -m 755 $RPM_BUILD_ROOT/%{_localstatedir}/lib/tomcat6/webapps/
#mkdir -p $RPM_BUILD_ROOT/%{_container}

# Copy the contents of the candlepin-bin.tar.gz to /var/lib/tomcat5/webapps (setup -c explodes the tar.gz automatically)
#cp -R . $RPM_BUILD_ROOT/%{_container}

%clean
rm -rf $RPM_BUILD_ROOT

#%files
#%defattr(-,jboss,jboss,-)
#/var/lib/tomcat5/webapps/candlepin-1.0.0.war

%files tomcat6
%defattr(644,tomcat,tomcat,775)
%{_localstatedir}/lib/tomcat6/webapps/candlepin*


%doc
