#!/usr/bin/env python
from __future__ import print_function, division, absolute_import

"""
This script is used for generating testing RPM repositories
"""

import sys
import os
import re
import stat
import json
import string
import subprocess
import shutil
import tempfile
import requests
import urllib3
import logging

from deploy_pkg.logger import build_logger

# To disable warning that we do insecure connection to localhost
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

# We don't care about urllib/requests logging
logging.getLogger("requests").setLevel(logging.WARNING)
logging.getLogger("urllib3").setLevel(logging.WARNING)

log = build_logger(name='create_test_repos')

RPM_SPEC_FILE_TEMPLATE = """
Summary:        ${Name} package
Name:           ${name}
Version:        ${version}
Release:        ${release}
License:        GPL License
Packager:       John Doe <john doe com>
Vendor:         Red Hat
URL:            https://www.tricky-testing-animals.org/
BuildArch:      noarch

%%install
${install_cmds}

%%description
This is a ${name} package.

%%files
${installed_files}

%%changelog
* Fri Mar 1 2019 John Doe <john doe com> 0
- ${name}
"""

GPG_NAME_REAL = "candlepin"

GPG_NAME_EMAIL = "noreply@candlepinproject.org"

GPG_PASSPHRASE = "secret"

GPG_KYEGRIP = None

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

# Single source of rpmmacros
RPMMACROS_KEY_VALUE = {
    "%_signature": "gpg",
    "%_gpg_name": GPG_NAME_REAL,
    "%_gpgbin": "/usr/bin/gpg2"
}

# Create content from dictionary
RPMMACROS_CONTENT = "\n".join([key + " " + value for key, value in RPMMACROS_KEY_VALUE.items()])

RPMBUILD_ROOT_DIR = os.path.expanduser("~") + "/rpmbuild"

REPO_ROOT_DIR = "/var/lib/tomcat/webapps/ROOT"

CANLDEPIN_SERVER_BASE_URL = "https://localhost:8443/candlepin/"

CANDLEPIN_USER = 'admin'
CANDLEPIN_PASS = 'admin'

TEST_DATA_JSON_MTIME = 0.0

CERT_DIR = 'generated_certs'


def run_command(command, verbose=False):
    """
    Try to run command in own process
    """

    # Run command in subprocess
    process = subprocess.Popen(command, shell=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)

    output = []
    # Print output of command
    for line in process.stdout.readlines():
        if verbose is True:
            print(line, end='')
        output.append(line)
    if verbose is True:
        sys.stdout.flush()

    # Wait for result of process
    ret = process.wait()

    return ret, output


def read_test_data(filename):
    """
    Try to read test data from json file.
    """
    try:
        with open(filename) as fp:
            try:
                data = json.load(fp)
            except ValueError as err:
                log.error("Unable to read file {} with test data: {}".format(filename, str(err)))
                return None
    except IOError as err:
        log.error("Unable to read file: {} with test data: {}".format(filename, str(err)))
        return None

    global TEST_DATA_JSON_MTIME
    TEST_DATA_JSON_MTIME = os.path.getmtime(filename)

    return data


def find_content(content_definitions, content_id):
    """
    Try to find content with specified content_id
    """

    for content in content_definitions:
        if content['id'] == content_id:
            return content

    return None


def create_repo_definition(product, content):
    """
    Create definition of repository from definition of content. Note: Definitions of
    content are shared between several products in test data.
    """
    repo_definition = {
        'name': product['id'] + '-' + content['name'],
        'product_id': product['id'],
        'type': content['type'],
        'content_url': os.path.join(content['content_url'], str(product['id']) + '-' + str(content['id'])),
        'gpg_url': content.get('gpg_url', ""),
        'packages': content.get('packages', [])
    }
    return repo_definition


def get_repo_definitions_for_products(product_definitions, content_definitions):
    """
    Try to get list of repository definitions from list of product definitions
    and list of content_definitions that are used by products.
    """
    repo_definitions = []
    for product in product_definitions:
        product_contents = product.get('content', [])
        for prod_cont in product_contents:
            content_id = prod_cont[0]
            content = find_content(content_definitions, content_id)
            if content is None:
                log.info(
                    "Unable to find content_id: {content_id} in definition of product: {product_name}".format(
                        content_id=content_id,
                        product_name=product['name']
                    )
                )
                continue
            repo = create_repo_definition(product, content)
            repo_definitions.append(repo)
    return repo_definitions


def get_repo_definitions(test_data):
    """
    Get list of repository definitions from test_data
    """

    if test_data is None:
        return []

    repo_definitions = []

    # There can be some "global" definitions of content shared between several products
    try:
        product_definitions = test_data['products']
    except KeyError as err:
        log.warning('Test data does not include any global definition of products')
        product_definitions = []
    try:
        content_definitions = test_data['content']
    except KeyError as err:
        log.warning('Test data does not include any global definition of contents')
        content_definitions = []

    glob_repo_defs = get_repo_definitions_for_products(product_definitions, content_definitions)
    repo_definitions.extend(glob_repo_defs)

    # There can be also some content specific for owners
    try:
        owners = test_data['owners']
    except KeyError as err:
        return repo_definitions

    for owner in owners:
        try:
            owner_product_definitions = owner['products']
        except KeyError as err:
            continue
        try:
            owner_content_definitions = owner['content']
        except KeyError as err:
            continue

        owners_repo_defs = get_repo_definitions_for_products(owner_product_definitions, owner_content_definitions)
        repo_definitions.extend(owners_repo_defs)

    return repo_definitions


def add_packages_to_repo(packages_list, repo_path, package_definitions):
    """
    This function tries to add list of packages to the repository
    """
    for pkg_name in packages_list:
        # Try to get information about package from package definitions
        try:
            pkg = package_definitions[pkg_name]
        except (KeyError, TypeError):
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
            log.info("  Adding package {pkg_name}".format(pkg_name=pkg_name))
            shutil.copyfile(src_pkg_file_path, dst_pkg_file_path)
        else:
            log.info("  Package {pkg_name} not defined".format(pkg_name=pkg_name))


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
        log.error('Unable to get owner: {err}'.format(err=str(err)))
        return None

    json_data = response.json()
    owner_names = [owner['key'] for owner in json_data]
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
            log.info('  Creating symbolic link %s' % symlink_name)
            if not os.path.islink(symlink_name):
                os.symlink(REPO_ROOT_DIR, symlink_name)
        except OSError as err:
            log.error(
                "Unable to create symbolic link: {link} ({err})".format(
                    link=symlink_name,
                    err=str(err)
                )
            )


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
        log.error(
            "Unable to create directory: {repo_dir}, {err}".format(
                repo_dir=REPO_ROOT_DIR,
                err=str(err)
            )
        )
        return None

    for repo in repo_definitions:
        repo_path = REPO_ROOT_DIR + repo['content_url']
        log.info("creating repository %s in %s" % (repo['name'], repo_path ))

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

    # Copy exported file to root of repositories
    gpg_key_path = os.path.join(REPO_ROOT_DIR, "RPM-GPG-KEY-candlepin")
    try:
        shutil.copyfile(GPG_EXPORTED_CANDLEPIN_KEY, gpg_key_path)
    except OSError as err:
        log.error(
            "Unable to copy GPG key: {gpg_key_path} ({err})".format(
                gpg_key_path=gpg_key_path,
                err=str(err)
            )
        )

    # Create symbolic links for owners (golden ticket)
    log.info("\nCreating symbolic links for owners...")
    owner_names = get_owners()
    generate_symlinks_for_owners(owner_names)


def get_productid_cert(repo_definition, owner='admin'):
    """
    This function tries to get product-id certificate for given repository
    """
    product_id = repo_definition['product_id']

    # Try to read certificate from cached file
    cert_path = os.path.join(CERT_DIR, str(product_id) + '.pem')
    try:
        with open(cert_path, 'r') as fp:
            cert = fp.read()
    except IOError as err:
        pass
    else:
        return cert

    # When the certificate cannot be found on the disk, then try to get certificate
    # from the candlepin server
    try:
        r = requests.get(
            CANLDEPIN_SERVER_BASE_URL + 'owners/' + owner + '/products/' + str(product_id) + '/certificate',
            auth=(CANDLEPIN_USER, CANDLEPIN_PASS),
            verify=False)
    except Exception as err:
        log.error('Unable to get product certificate: {err}'.format(err=str(err)))
        return None

    if r.status_code != 200:
        return None

    json_data = r.json()

    try:
        cert = json_data['cert']
    except KeyError:
        return None

    # When certificate was successfully downloaded from the server, then try to
    # save the certificate to the file
    try:
        with open(cert_path, 'w') as fp:
            fp.write(cert)
    except IOError as err:
        pass

    return cert


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
        log.warning('Test data does not include any definition of packages')
        return None

    # Convert list to dictionary to be able to find packages faster
    package_definitions = { pkg['name']: pkg for pkg in package_definitions }

    return package_definitions


def create_dummy_package(package, expect_script_path, keygrip):
    """
    This function tries to create dummy RPM package. It returns True,
    when package was created correctly. Otherwise it returns False.
    """
    global TEST_DATA_JSON_MTIME

    name = package['name']
    # Default values
    version = package.get('version', '1')
    release = package.get('release', '0')
    arch = package.get('arch', 'noarch')
    files = package.get('files', None)
    installed_files = ''
    install_cmds = ''

    # If definition of RPM contains files section, then add files to the RPM
    if files is not None:
        for file in files:
            # Path of file has to be specified, other attributes
            # are optional, because we can use default values
            path = file['path']
            file_type = file.get('type', 'file')
            perm = file.get('perm', '0644')
            owner = file.get('owner', 'root')
            group = file.get('group', 'root')
            content = file.get('content', '')
            if file_type == 'file':
                dir_name = os.path.dirname(path)
                install_cmds += "mkdir -p $RPM_BUILD_ROOT/{dir_name}\n".format(
                    dir_name=dir_name)
                install_cmds += 'echo "{content}" > $RPM_BUILD_ROOT/{path}\n'.format(
                    content=content, path=path)
            elif file_type == 'directory':
                install_cmds += 'mkdir -p $RPM_BUILD_ROOT/{path}\n'.format(
                    path=path)
            else:
                log.warning(
                    'Skipping file: "{path}", because file type: "{file_type}" is not supported'.format(
                        path=path,
                        file_type=file_type
                    )
                )
                continue
            install_cmds += 'chown {owner} $RPM_BUILD_ROOT/{path}\n'.format(
                owner=owner, path=path)
            install_cmds += 'chgrp {group} $RPM_BUILD_ROOT/{path}\n'.format(
                group=group, path=path)
            install_cmds += 'chmod {perm} $RPM_BUILD_ROOT/{path}\n'.format(
                perm=perm, path=path)
            installed_files += path + '\n'

    # Path to rpm file
    rpm_file_name = name + '-' + version + '-' + release + '.' + arch + '.rpm'
    rpm_file_path = os.path.join(RPMBUILD_ROOT_DIR, 'RPMS', arch, rpm_file_name)

    # Creating and signing many RPM packages can be time consuming. So, if RPM
    # package already exists, then it is not created again.
    if os.path.isfile(rpm_file_path):
        rpm_mtime = os.path.getmtime(rpm_file_path)
        if rpm_mtime > TEST_DATA_JSON_MTIME:
            log.info("RPM %s already exist" % name)
            return True

    log.info("creating RPM %s" % name)

    # Create content of spec file
    template = string.Template(RPM_SPEC_FILE_TEMPLATE)
    d = {
        'name': name,
        'Name': name[0].upper() + name[1:],
        'version': version,
        'release': release,
        'installed_files': installed_files,
        'install_cmds': install_cmds
    }
    rpm_spec_content = template.substitute(d)

    # Path to spec file
    spec_file_path = os.path.join(RPMBUILD_ROOT_DIR, 'SPECS', name + '.spec')

    # Save content of spec file to real file
    with open(spec_file_path, 'w') as fp:
        fp.write(rpm_spec_content)

    # Generate RPM package using rpmbuild
    ret, _ = run_command('rpmbuild -bb %s' % spec_file_path, verbose=False)

    if ret != 0:
        log.error('Creating RPM "{name}" FAILED'.format(name=name))
        return False

    log.info("Signing RPM %s" % name)

    if keygrip is None:
        ret, _ = run_command('%s %s' % (expect_script_path, rpm_file_path), verbose=False)
    else:
        ret, _ = run_command('%s %s' % ("rpm --addsign", rpm_file_path), verbose=False)

    # RPM has to be signed. Otherwise it will not be able to install it from testing repository
    if ret != 0:
        log.error('Signing of RPM "{name}" FAILED'.format(name=name))
        # Unsigned RPM file is useless. Delete it.
        os.remove(rpm_file_path)
        return False

    return True


def generate_packages(package_definitions, keygrip):
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

    results = []
    for pkg in package_definitions.values():
        ret = create_dummy_package(pkg, expect_script_path, keygrip)
        results.append(ret)

    # Remove temporary directory containing script for entering passphrase
    shutil.rmtree(temp_dir_path)

    if not all(results):
        return False

    return True


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
    ret, _ = run_command('gpg --list-keys %s' % key_id)

    return ret


def gpg_key_keygrip():
    """
    This function tries to get keygrip from key used for signing RPMs
    """
    key_id = '"%s <%s>"' % (GPG_NAME_REAL, GPG_NAME_EMAIL)

    ret, output = run_command('gpg2 --list-keys --with-keygrip %s' % key_id, verbose=False)

    return ret, output


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
    ret,_ = run_command('gpg --batch --gen-key %s' % script_path)

    # Remove temporary directory containing script for generating GPG key
    shutil.rmtree(temp_dir_path)

    # Export gpg key
    res, _ = run_command('gpg --export -a %s > %s' % (GPG_NAME_REAL, GPG_EXPORTED_CANDLEPIN_KEY))
    ret += res

    # Import GPG key to rpm db (not necessary), but "rpm -K signed_file.rpm" can be used
    res, _ = run_command('rpm --import %s' % GPG_EXPORTED_CANDLEPIN_KEY)
    ret += res

    # Copy exported GPG key to root dir of static content provided by tomcat
    res, _ = run_command('cp %s %s' % (GPG_EXPORTED_CANDLEPIN_KEY, REPO_ROOT_DIR))
    ret += res

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
        # Read existing ~/.rpmmacros file
        with open(rpmmacros_path, "r") as fp:
            content = fp.read()
        update_required = False
        # Check if the file contains all keys
        for key,value in RPMMACROS_KEY_VALUE.items():
            if key not in content:
                # If not update the content
                content += "\n" + key + " " + value
                update_required = True
        # write new content, whe content was updated
        if update_required is True:
            with open(rpmmacros_path, "w") as fp:
                fp.write(content)


def main():
    if len(sys.argv) != 2:
        log.error("Syntax {name} test_data.json".format(name=sys.argv[0]))
        return 1

    test_data = read_test_data(sys.argv[1])

    gpg_exists = does_gpg_key_exist()

    if gpg_exists != 0:
        log.info("")
        log.info("Creating GPG key...")
        create_gpg_key()

    # Try to find key_grip in output of gpg command
    key_grip = None
    ret, pgp_output = gpg_key_keygrip()
    if ret == 0:
        pattern = re.compile(r'^ *Keygrip = (.*)$')
        for line in pgp_output:
            result = pattern.search(line)
            # When gpg supported key_grip, then preset passphrase has to be used
            if result is not None:
                key_grip = result.groups()[0]
                # Make sure that preset passphrases are allowed
                ret, _ = run_command("grep 'allow-preset-passphrase' /root/.gnupg/gpg-agent.conf")
                if ret != 0:
                    log.info("Updating gpg-agent.conf")
                    run_command("echo 'allow-preset-passphrase' >> /root/.gnupg/gpg-agent.conf")
                # Make sure that gpg-agent is running
                run_command('gpg-connect-agent reloadagent /bye')
                log.info("Using GPG keygrip: %s" % key_grip)
                run_command(
                    '/usr/libexec/gpg-preset-passphrase --passphrase {gpg_passphrase} --preset {key_grip}'.format(
                        gpg_passphrase=GPG_PASSPHRASE,
                        key_grip=key_grip
                    )
                )
                break

    modify_rpmmacros()

    log.info("")
    log.info("Creating packages...")

    package_definitions = get_package_definitions(test_data)

    ret = generate_packages(package_definitions, key_grip)

    if ret is False:
        log.info("")
        log.error("Unable to generate all packages")
        return 1

    log.info("")
    log.info("Creating repositories...")

    repo_definitions = get_repo_definitions(test_data)

    generate_repositories(repo_definitions, package_definitions)

    return 0


if __name__ == '__main__':
    main()
