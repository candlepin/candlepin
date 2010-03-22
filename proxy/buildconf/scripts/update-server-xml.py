#!/usr/bin/python
import os.path, os
import re
import shutil

from sys import argv, exit

https_connector_configuration="""
<Connector port="8443" protocol="HTTP/1.1" SSLEnabled="true"
           maxThreads="150" scheme="https" secure="true"
           clientAuth="want" sslProtocol="TLS" 
           keystoreFile="conf/keystore"
           truststoreFile="conf/keystore" 
           keystorePass="password"
           truststorePass="password" />"""

existing_https_connector_pattern = '<Connector port="8443".*?/>'
commentedout_https_connector_pattern = '<!--\s*?\n*?<Connector port="8443".*?-->'

def replace_current_https_connector(an_original):
	compiled_regex = re.compile(existing_https_connector_pattern, re.DOTALL)
	return compiled_regex.sub(https_connector_configuration, an_original)
	
def replace_commented_out_https_connector(an_original):
	compiled_regex = re.compile(commentedout_https_connector_pattern, re.DOTALL)
	return compiled_regex.sub(https_connector_configuration, an_original)
	
def update_tomcat_config(conf_dir):
	print "Updating tomcat configuration ..."
	original_config = open(os.path.join(conf_dir, "server.xml"), "r").read()
	
	if re.search(commentedout_https_connector_pattern, original_config, re.DOTALL):
		updated_config = replace_commented_out_https_connector(original_config)
	else:
		updated_config = replace_current_https_connector(original_config)
	
	config_file = open(os.path.join(conf_dir, "server.xml"), "w")
	config_file.write(updated_config)
	file.close

def make_backup_config(conf_dir):
	print "Backing up current server.xml ..."
	shutil.copy(os.path.join(conf_dir, "server.xml"), os.path.join(conf_dir, "server.xml.original"))

def main():
	if len(argv) != 2:
		print "Usage: python %s <conf directory of tomcat installation>" % argv[0]
		exit(1)

	make_backup_config(argv[1])
	update_tomcat_config(argv[1])
	
	print "done!"

#def main():
#    if len(argv) != 2 and len(argv) != 3:
#        print "Usage: python %s <jar dirs separated by :> [<src jars separated by :>]" % argv[0]
#        print """Example: python %s "/usr/share/java:/usr/share/java-ext" "/usr/share/src-jars" """ % argv[0]
#        exit(1)
#
#    src_entries = {}
#    if len(argv) == 3:
#        for dr in argv[2].split(":"):
#            if dr.strip() and os.path.exists(dr):
#                for f in os.listdir(dr):
#                    if f != "rhn.jar" and f.endswith(".jar") and not f in src_entries:
#                        src_entries[f] = os.path.join(dr,f)
#    entries = {}
#    entries['tools.jar'] = classpath_entry % "/usr/lib/jvm/java/lib/tools.jar"
#    entries['ant-junit.jar'] = classpath_entry % "/usr/share/java/ant/ant-junit.jar"
#    entries['ant.jar'] = classpath_entry % "/usr/share/java/ant.jar"
#
#    for dr in argv[1].split(":"):
#        if dr.strip():
#            if os.path.isdir(dr):
#                for f in os.listdir(dr):
#                    if f != "rhn.jar" and f.endswith(".jar") and not f in entries:
#                        if f in  src_entries:
#                            entries[f] = classpath_sourcepath_entry % (os.path.join(dr,f) , src_entries[f])
#                        elif f[:-4] + "-" +"src.jar" in src_entries:
#                            entries[f] = classpath_sourcepath_entry % (os.path.join(dr,f) ,
#                                                                src_entries[f[:-4] + "-" +"src.jar"])
#                        elif f[:-4] + "-" +"sources.jar" in src_entries:
#                            entries[f] = classpath_sourcepath_entry % (os.path.join(dr,f) ,
#                                                                src_entries[f[:-4] + "-" +"sources.jar"])
#                        else:
#                            entries[f] = classpath_entry % os.path.join(dr,f)
#            if os.path.isfile(dr):
#                f = os.path.basename(dr)
#                if f != "rhn.jar" and f.endswith(".jar") and not f in entries:
#                    entries[f] = classpath_entry % dr
#
#    print base_template % "\n".join (entries.values())

if __name__=="__main__":
    main()
