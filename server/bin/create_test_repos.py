#!/usr/bin/env python
from __future__ import print_function, division, absolute_import

"""
This script is used for generating testing RPM repositories
"""

import sys
import os
import stat
import json
import string
import subprocess
import shutil
import tempfile
import requests

# To disable warning that we do insecure connection to localhost
import urllib3
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

RPM_SPEC_FILE_TEMPLATE = """
Summary:        ${Name} package
Name:           ${name}
Version:        ${version}
Release:        ${release}
License:        GPL License
Packager:       John Doe <john doe com>
Vendor:         Red Hat
URL:            http://www.tricky-testing-animals.org/
BuildArch:      noarch
 
%%description
This is a ${name} package.
 
%%files
 
%%changelog
* Fri Mar 1 2019 John Doe <john doe com> 0
- ${name}
"""

GPG_NAME_REAL = "candlepin"

GPG_NAME_EMAIL = "noreply@candlepinproject.org"

GPG_PASSPHRASE = "secret"

# When Fedora is used or gpg 2.1 is used, then it will be possible
# to use following options and not to use passphrase and expect script
#
# %%no-protection
# %%transient-key
#
GPG_BATCH_GEN_SCRIPT_CONTENT = """
Key-Type: 1
Key-Length: 4096
Name-Real: %s
Name-Email: %s
Expire-Date: 0
Passphrase: %s
%%commit
""" % (GPG_NAME_REAL, GPG_NAME_EMAIL, GPG_PASSPHRASE)

EXPECT_SCRIPT_CONTENT = """#!/usr/bin/expect -f
spawn rpm --addsign {*}$argv
expect -exact "Enter pass phrase: "
send -- "%s\r"
expect eof
""" % GPG_PASSPHRASE

GPG_EXPORTED_CANDLEPIN_KEY = os.path.expanduser("~") + "/RPM-GPG-KEY-candlepin"

RPMMACROS_CONTENT = """
%%_signature gpg
%%_gpg_name %s
%%_gpgbin /usr/bin/gpg2
""" % GPG_NAME_REAL

RPMBUILD_ROOT_DIR = os.path.expanduser("~") + "/rpmbuild"

REPO_ROOT_DIR = "/var/lib/tomcat/webapps/ROOT"

CANLDEPIN_SERVER_BASE_URL = "https://localhost:8443/candlepin/"

CANDLEPIN_USER = 'admin'
CANDLEPIN_PASS = 'admin'

TEST_DATA_JSON_MTIME = 0.0


def run_command(command, verbose=False):
    """
    Try to run command in own process
    """

    # Run command in subprocess
    process = subprocess.Popen(command, shell=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)

    # Print output of command
    if verbose is True:
        for line in process.stdout.readlines():
            print(line)

    # Wait for result of process
    ret = process.wait()

    return ret


def read_test_data(filename):
    """
    Try to read test data from json file.
    """
    try:
        with open(filename) as fp:
            try:
                data = json.load(fp)
            except ValueError as err:
                print("Unable to read file %s with test data: %s" % (filename, str(err)))
                return None
    except IOError as err:
        print("Unable to read file: %s with test data: %s" % (filename, str(err)))
        return None

    global TEST_DATA_JSON_MTIME
    TEST_DATA_JSON_MTIME = os.path.getmtime(filename)

    return data


def get_repo_definitions(test_data):
    """
    Get list of repositories from test_data
    """

    if test_data is None:
        return []

    try:
        repo_definitions = test_data['content']
    except KeyError as err:
        print('Info: Test data does not include any global definition of repositories')
        repo_definitions = []

    # There can be also some content specific for owners
    try:
        owners = test_data['owners']
    except KeyError as err:
        return repo_definitions
    
    for owner in owners:
        try:
            owner_content = owner['content']
        except KeyError as err:
            continue
        else:
            repo_definitions.extend(owner_content)
    
    return repo_definitions


def add_packages_to_repo(packages_list, repo_path, package_definitions):
    """
    This function tries to add list of packages to the repository
    """
    for pkg_name in packages_list:
        # Try to get information about package from package definitions
        try:
            pkg = package_definitions[pkg_name]
        except KeyError, TypeError:
            version = '1'
            release = '0'
            arch = 'noarch'
        else:
            version = pkg.get('version', '1')
            release = pkg.get('release', '0')
            arch = pkg.get('arch', 'noarch')

        # Construct name of package
        pkg_file_name = '%s-%s-%s.%s.rpm' % (pkg_name, version, release, arch)
        src_pkg_file_path = os.path.join(RPMBUILD_ROOT_DIR, 'RPMS' , arch, pkg_file_name)
        dst_pkg_file_path = os.path.join(repo_path, 'RPMS', pkg_file_name)

        # Check if RPm file exist
        if os.path.isfile(src_pkg_file_path):
            print("\tadding package %s" % pkg_name)
            shutil.copyfile(src_pkg_file_path, dst_pkg_file_path)
        else:
            print("\tpackage %s not defined" % pkg_name)


def get_owners():
    """
    Get list of owner names.
    """
    try:
        response = requests.get(
            CANLDEPIN_SERVER_BASE_URL + 'owners/',
            auth=(CANDLEPIN_USER, CANDLEPIN_PASS),
            verify=False)
    except Exception as err:
        print('Error: %s' % str(err))
        return None

    json_data = response.json()
    owner_names = [ owner['key'] for owner in json_data ]
    return owner_names


def generate_symlinks_for_owners(owner_names):
    """
    When content access mode is org_environment is switched on for some
    owner, then path to content is changed a little bit. It adds
    the name of owner to the beginning of path. e.g. path to content
    is changed from: /path/to/foo to /snowwhite/path/to/foo. For this
    reason it is necessary to add symbolic link for every owner.
    """
    if owner_names is None:
        return

    for owner_name in owner_names:
        symlink_name = os.path.join(REPO_ROOT_DIR, owner_name)
        try:
            print('\tcreating symbolic link %s' % symlink_name)
            if not os.path.islink(symlink_name):
                os.symlink(REPO_ROOT_DIR, symlink_name)
        except OSError as err:
            print("Unable to create symbolic link: %s (%s)" % (symlink_name, str(err)))


def generate_repositories(repo_definitions, package_definitions):
    """
    This function tries to generate yum repositories with some dummy packages
    """
    if repo_definitions is None:
        return None

    # Make sure that root directory of repositories exist
    try:
        if not os.path.exists(REPO_ROOT_DIR):
            os.makedirs(REPO_ROOT_DIR)
    except OSError as err:
        print("Unable to create directory: %s, %s" % (REPO_ROOT_DIR, str(err)))
        return None

    for repo in repo_definitions:
        repo_path = REPO_ROOT_DIR + repo['content_url']
        print("creating repository %s in %s" % (repo['name'], repo_path ))

        repo_path_rpms = os.path.join(repo_path, 'RPMS')
        if not os.path.exists(repo_path_rpms):
            os.makedirs(repo_path_rpms)
        else:
            # Delete all previous RPM files, because we don't to have there
            # obsolete RPM files (definition of rpm has been changed in test_data.json)
            for file_name in os.listdir(repo_path_rpms):
                file_path = os.path.join(repo_path_rpms, file_name)
                if os.path.isfile(file_path):
                    os.unlink(file_path)

        add_packages_to_repo(repo['packages'], repo_path, package_definitions)

        # Create repository with RPM packages
        run_command('createrepo_c %s' % repo_path)

        # Try to create temporary product on candlepin server
        ret = create_repo_product(repo)
        if ret is None:
            continue

        # Try to get product certificate from candlepin server and add it to repository
        cert = get_productid_cert(repo)
        if cert is not None:
            # Note: the cert has to have name 'productid', because modifyrepo
            # command creates new record in repomd.xml according name of file
            with open('productid', 'w') as fp:
                fp.write(cert)
            # This command add productid certificate to repository
            run_command('modifyrepo_c productid %s/repodata' % repo_path)
            # Remove temporary cert file
            os.remove('productid')

        # Remove temporary product from candelpin server
        remove_repo_product(repo)

    # Copy exported file to root of repositories
    gpg_key_path = os.path.join(REPO_ROOT_DIR, "RPM-GPG-KEY-candlepin")
    try:
        shutil.copyfile(GPG_EXPORTED_CANDLEPIN_KEY, gpg_key_path)
    except OSError as err:
        print("Unable to copy GPG key: %s (%s)" % (gpg_key_path, str(err)))

    # Create symbolic links for owners (golden ticket)
    print("\nCreating symbolic links for owners...")
    owner_names = get_owners()
    generate_symlinks_for_owners(owner_names)

def get_productid_cert(content, owner='admin'):
    """
    This function tries to get product-id certificate for given repository (content)
    """
    product_id = content['id']

    try:
        r = requests.get(
            CANLDEPIN_SERVER_BASE_URL + 'owners/' + owner + '/products/' + str(product_id) + '/certificate',
            auth=(CANDLEPIN_USER, CANDLEPIN_PASS),
            verify=False)
    except Exception as err:
        print('Error: %s' % str(err))
        return None

    if r.status_code != 200:
        return None

    json_data = r.json()

    try:
        cert = json_data['cert']
    except KeyError:
        return None

    return cert


def create_repo_product(content, owner="admin"):
    """
    This function tries to create temporary product for getting product-id certificate.
    We need to create certificate with id that contains only one content and id of
    product is equal to id of content.
    """

    # Name and ID have to be in specification of content
    name = content['name']
    product_id = content['id']
    # Other attributes are optional in definition of content and have default values
    arches = content.get('arches', 'noarch')
    version = content.get('version', '1.0')

    request_data = {
        'name': name,
        'id': product_id,
        'arch': arches,
        'version': version,
        'content': [product_id, True]
    }

    try:
        r = requests.post(
            CANLDEPIN_SERVER_BASE_URL + 'owners/' + owner + '/products',
            json=request_data,
            auth=(CANDLEPIN_USER, CANDLEPIN_PASS),
            verify=False)
    except Exception as err:
        print('Error: %s' % str(err))
        return None

    return r


def remove_repo_product(content, owner='admin'):
    """
    This function tries to remove temporary products from candlepin server
    """
    product_id = content['id']

    try:
        r = requests.delete(
            CANLDEPIN_SERVER_BASE_URL + 'owners/' + owner + '/products/' + str(product_id),
            auth=(CANDLEPIN_USER, CANDLEPIN_PASS),
            verify=False)
    except Exception as err:
        print('Error: %s' % str(err))
        return None

    return r


def get_package_definitions(test_data):
    """
    Try to get list of package definitions from test_data and
    convert the list to dictionary
    """
    if test_data is None:
        return None

    try:
        package_definitions = test_data['packages']
    except KeyError as err:
        print('Test data does not include any definition of packages')
        return None

    # Convert list to dictionary to be able to find packages faster
    package_definitions = { pkg['name']: pkg for pkg in package_definitions }
    
    return package_definitions


def create_dummy_package(package, expect_script_path):
    """
    This function tries to create dummy RPM package
    """
    global TEST_DATA_JSON_MTIME
    
    name = package['name']
    # Default values
    version = package.get('version', '1')
    release = package.get('release', '0')
    arch = package.get('arch', 'noarch')

    # Path to rpm file
    rpm_file_name = name + '-' + version + '-' + release + '.' + arch + '.rpm'
    rpm_file_path = os.path.join(RPMBUILD_ROOT_DIR, 'RPMS', arch, rpm_file_name)

    # Creating and signing many RPM packages can be time consuming. So, if RPM
    # package aready exists, then it is not created again.
    if os.path.isfile(rpm_file_path):
        rpm_mtime = os.path.getmtime(rpm_file_path)
        if rpm_mtime > TEST_DATA_JSON_MTIME:
            print("RPM %s already exist" % name)
            return

    print("creating RPM %s" % name)

    # Create content of spec file
    template = string.Template(RPM_SPEC_FILE_TEMPLATE)
    d = {'name': name, 'Name': name[0].upper() + name[1:], 'version': version, 'release': release}
    rpm_spec_content = template.substitute(d)

    # Path to spec file
    spec_file_path = os.path.join(RPMBUILD_ROOT_DIR, 'SPECS', name + '.spec')

    # Save content of spec file to real file
    with open(spec_file_path, 'w') as fp:
        fp.write(rpm_spec_content)

    # Generate RPM package using rpmbuild
    run_command('rpmbuild -bb %s' % spec_file_path)

    run_command('%s %s' % (expect_script_path, rpm_file_path))
    

def generate_packages(package_definitions):
    """
    This function tries to generate dummy packages from the list of package definitions
    """

    if package_definitions is None:
        return None

    rpmbuild_sub_dirs = ["BUILD", "RPMS", "SOURCES", "SPECS", "SRPMS"]

    # Make root dir for rpmbuild (~/rpmbuild)
    if not os.path.exists(RPMBUILD_ROOT_DIR):
        os.makedirs(RPMBUILD_ROOT_DIR)

    # Make all remaining subdirs for rpmbuild
    for subdir in rpmbuild_sub_dirs:
        subdir_path = os.path.join(RPMBUILD_ROOT_DIR, subdir)
        if not os.path.exists(subdir_path):
            os.makedirs(subdir_path)

    # Following script has to be used, because rpmsing or rpm --sign
    # reads input of passphrase using getpass() despite the gpg is
    # not protected by any passphrase. The environment variable has
    # to be set to en_EN.utf8 or it has to be unset. Otherwise expect
    # script will not work, because prompt of getpass() will be
    # different from "Enter pass phrase: ".
    # NOTE: The rpmsing doesn't try to read passphrase on Fedora,
    # when gpg is not protected by passphrase.
    os.environ["LANG"] = "en_EN.uft8"
    expect_script_path, temp_dir_path = create_expect_script()

    # Change permission to be able to execute the script
    os.chmod(expect_script_path, stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)

    for pkg in package_definitions.values():
        create_dummy_package(pkg, expect_script_path)

    # Remove temporary directory containing script for entering passphrase
    shutil.rmtree(temp_dir_path)

def create_gpg_batch_gen_script():
    """
    This function tries to create temporary script file for creating gpg key.
    This function returns path to script file and path to temporary directory
    """ 

    # Create temporary directory first
    temp_dir_path = tempfile.mkdtemp()

    script_path = os.path.join(temp_dir_path, 'gpg_batch_gen_script')

    with open(script_path, "w") as fp:
        fp.write(GPG_BATCH_GEN_SCRIPT_CONTENT)

    return script_path, temp_dir_path


def does_gpg_key_exist():
    """
    This function tries to check if GPG for signing RPM packages already exist
    """

    key_id = '"%s <%s>"' % (GPG_NAME_REAL, GPG_NAME_EMAIL)

    # Try to get list of GPG key with given key ID
    return run_command('gpg --list-keys %s' % key_id)


def create_gpg_key():
    """
    This function tries to create gpg key used for signing of RPM packages
    """

    # Note: you can completely remove all generated/exported keys using:
    #
    # gpg --delete-secret-key "candlepin <noreply@candlepinproject.org>"
    # gpg --delete-key "candlepin <noreply@candlepinproject.org>"
    #
    # rpm -q gpg-pubkey --qf '%{NAME}-%{VERSION}-%{RELEASE}\t%{SUMMARY}\n' | grep candlepin
    # Look at output and notice id
    #
    # rpm -e gpg-pubkey-12345678-90abcdef

    script_path, temp_dir_path = create_gpg_batch_gen_script()
    
    # Generate GPG key for signing RPM packages
    ret = run_command('gpg --batch --gen-key %s' % script_path)

    # Remove temporary directory containing script for generating GPG key
    shutil.rmtree(temp_dir_path)

    # Export gpg key
    ret += run_command('gpg --export -a %s > %s' % (GPG_NAME_REAL, GPG_EXPORTED_CANDLEPIN_KEY))

    # Import GPG key to rpm db (not necessary), but "rpm -K signed_file.rpm" can be used
    ret += run_command('rpm --import %s' % GPG_EXPORTED_CANDLEPIN_KEY)

    # Copy exported GPG key to root dir of static content provided by tomcat
    ret += run_command('cp %s %s' % (GPG_EXPORTED_CANDLEPIN_KEY, REPO_ROOT_DIR))

    return ret


def create_expect_script():
    """
    This function tries to create expect script using for executing rpmsing command.
    It is necessary to use epect script, because rpmsing command requires reading
    input using getpass() system call and stdin cannot be used in this case.
    """
    # Create temporary directory first
    temp_dir_path = tempfile.mkdtemp()

    script_path = os.path.join(temp_dir_path, 'rpm_sign.exp')

    with open(script_path, "w") as fp:
        fp.write(EXPECT_SCRIPT_CONTENT)

    return script_path, temp_dir_path


def modify_rpmmacros():
    """
    This function tries to modify ~/.rpmmacros
    """
    rpmmacros_path = os.path.expanduser("~") + '/.rpmmacros'
    if not os.path.isfile(rpmmacros_path):
        with open(rpmmacros_path, "w") as fp:
            fp.write(RPMMACROS_CONTENT)
    else:
        # TODO: check for macros and add them to the rpmmacros in needed
        pass


def main():
    if len(sys.argv) != 2:
        print("Error: syntax %s test_data.json" % sys.argv[0])
        return 1

    test_data = read_test_data(sys.argv[1])

    gpg_exists = does_gpg_key_exist()

    if gpg_exists != 0:
        print("")
        print("Creating GPG key...")
        create_gpg_key()

    modify_rpmmacros()

    print("")
    print("Creating packages...")

    package_definitions = get_package_definitions(test_data)

    generate_packages(package_definitions)

    print("")
    print("Creating repositories...")

    repo_definitions = get_repo_definitions(test_data)

    generate_repositories(repo_definitions, package_definitions)

    return 0
            

if __name__ == '__main__':
    main()