Name: candlepin-common
Summary: Common code for Candlepin and related projects
License: GPLv2
Version: 1.0.0
Release: 1%{?dist}
URL: http://www.candlepinproject.org
Source: %{name}-%{version}.tar.gz
BuildRoot: %{_tmppath}/%{name}-%{version}-%{release}-buildroot
Vendor: Red Hat, Inc.
BuildArch: noarch

BuildRequires: java-devel >= 0:1.6.0
BuildRequires: ant >= 0:1.7.0

