#!/usr/bin/python
#
# For a brief window, content IDs were changed in the product service,
# resulting in the inability to import a new manifest because the content
# label being imported already exists in the db with a different ID.
#
# This script opens a manifest, looks for all the content in it, checks
# the Candlepin database for that content label but with a different ID,
# and if found attempts to update all references to that content to the
# new value so the import can proceed. (same for pulp database)
#
# Once run the issue should be permanently fixed as the database now
# has the new content IDs, and these IDs should not be changing ever
# again upstream.
#
# python update-content-ids.py /path/to/manifest.zip

import os
import glob
import shutil
import simplejson as json
import sys
import tempfile
from zipfile import ZipFile
import psycopg2
from commands import getstatusoutput


def load_content(filename):
    """
    Load all the content definitions from a product JSON file in a manfiest.
    """
    print("Loading product JSON: " + os.path.basename(filename))
    product_f = open(filename)
    product_json = json.load(product_f)
    content = []
    for c in product_json['productContent']:
        content.append(c['content'])
    return content


def scan_content(conn, content):
    """
    Scan all manifest content looking for pre-existing content with
    same label but different ID. We can do this by checking the Candlepin
    database.
    """
    for c in content:
        content_id = c['id']
        content_label = c['label']
        cur = conn.cursor()
        cur.execute("SELECT id FROM cp_content WHERE label = %s AND id != %s FOR UPDATE",
                [content_label, content_id])
        result = cur.fetchone()
        if result:
            print "Found duplicate content label for '%s', old ID = %s, new ID = %s" % \
                    (content_label, result[0], content_id)
            check_new_content_exists(conn, content_id)
            fix_pulp_content(result[0], content_id)
            fix_candlepin_content(conn, content_label, result[0], content_id)


def check_new_content_exists(conn, new_id):
    """
    Check if the new content ID is already being used by something else in
    the db. (presumably custom content) We check this explicitly to prevent
    possible issues as we're disabling triggers to get the data changed. If
    this situation does arise there's nothing we can do in any automated
    fashion, this is just to make sure we don't break something and terminate.
    """
    cur = conn.cursor()
    cur.execute("SELECT id FROM cp_content WHERE id = %s",
            [new_id])
    result = cur.fetchone()
    if result:
        # If the content ID already exists, maybe from custom content, we are
        # in real trouble and cannot automate a fix... Lets hope this never
        # happens.
        raise Exception("New content ID already exists: %s" % new_id)


def fix_candlepin_content(conn, label, old_id, new_id):
    """
    Fix the content in candlepin by updating all references to the old ID to
    the new.
    """
    cur = conn.cursor()

    # Have to disable the triggers to let these keys be modified:
    cur.execute("ALTER TABLE cp_content_modified_products DISABLE TRIGGER ALL")
    cur.execute("ALTER TABLE cp_content DISABLE TRIGGER ALL")

    cur.execute("UPDATE cp_content SET id = %s WHERE id = %s", (new_id, old_id))
    cur.execute("UPDATE cp_content_modified_products SET cp_content_id = %s WHERE cp_content_id = %s", (new_id, old_id))
    cur.execute("UPDATE cp_product_content SET content_id = %s WHERE content_id = %s", (new_id, old_id))
    cur.execute("UPDATE cp_env_content SET contentid = %s WHERE contentid = %s", (new_id, old_id))

    # Re-enable the triggers:
    cur.execute("ALTER TABLE cp_content_modified_products ENABLE TRIGGER ALL")
    cur.execute("ALTER TABLE cp_content ENABLE TRIGGER ALL")


def fix_pulp_content(old_id, new_id):
    """
    Update to the new content ID in pulp as well.
    """
    command = """echo "db.repos.update({ groupid: 'content:%s'}, {\\$set: { groupid: 'content:%s'}}, false, true);" | mongo pulp_database""" % (old_id, new_id)
    (status, output) = getstatusoutput(command)
    # Raise exception is command didn't give proper status, this should kill the Candlepin
    # transaction and prevent anything from being committed so we can fix and rerun.
    if status != 0:
        raise Exception("Pulp update failed")


def main(args):
    manifest = args[1]
    print("creating work directory")
    workdir = tempfile.mkdtemp(prefix="work-dir", dir=".")
    print("extracting manifest")
    zip_file = ZipFile(manifest, "r")
    zip_file.extractall(workdir)
    zip_file = ZipFile(os.path.join(workdir, "consumer_export.zip"), "r")
    zip_file.extractall(workdir)

    print("reading products")
    products = glob.glob(os.path.join(workdir, "export", "products",
        "*.json"))

    content = []
    for product in products:
        print product
        content.extend(load_content(product))


    # These credentials should be good for a normal Katello/SAM deployment:
    conn = psycopg2.connect("dbname=candlepin user=postgres")

    scan_content(conn, content)

    conn.commit()

    print("cleaning up")
    shutil.rmtree(workdir)
    print("all done.")


if __name__ == "__main__":
    main(sys.argv)
