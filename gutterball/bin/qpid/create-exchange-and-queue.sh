#!/bin/bash

config_args='-b amqps://localhost:5671 --ssl-certificate ./keys/qpid_ca.crt --ssl-key ./keys/qpid_ca.key'
sudo qpid-config $config_args add exchange fanout "activation" --durable 
sudo qpid-config $config_args add queue "qactivation" --durable 
sudo qpid-config $config_args bind "activation" "qactivation"
