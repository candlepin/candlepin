#!/bin/sh -e

for file in $@; do
    echo "Injecting subscriptions from JSON file: ${file}"
    curl -k -u admin:admin -X POST -F "file=@${file}" "https://localhost:8443/candlepin/hostedtest/import/subscriptions"
done
