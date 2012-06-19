#!/usr/bin/python
import os.path, os
import re
import shutil

from sys import argv, exit

https_connector_configuration="""
<Connector port="8443" protocol="HTTP/1.1" SSLEnabled="true"
           maxThreads="150" scheme="https" secure="true"
           clientAuth="want" SSLProtocol="TLS"
           keystoreFile="conf/keystore"
           truststoreFile="conf/keystore"
           keystorePass="password"
           keystoreType="PKCS12"
           ciphers="SSL_RSA_WITH_3DES_EDE_CBC_SHA,TLS_RSA_WITH_AES_256_CBC_SHA,
                    TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA,TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA,
                    TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA,TLS_ECDH_RSA_WITH_AES_128_CBC_SHA,
                    TLS_ECDH_RSA_WITH_AES_256_CBC_SHA,TLS_ECDH_anon_WITH_3DES_EDE_CBC_SHA,
                    TLS_ECDH_anon_WITH_AES_128_CBC_SHA,TLS_ECDH_anon_WITH_AES_256_CBC_SHA"
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
	print "Updating tomcat configuration in %s..."  % conf_dir
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

if __name__=="__main__":
    main()
