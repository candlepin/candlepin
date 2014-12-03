How to create the necessary certs:

Make a self-signed CA:

```
$ openssl genrsa -out test-ca.key 2048
$ openssl req -new -key test-ca.key -out test-ca.csr -subj "/C=US/CN=localhost"
$ openssl x509 -req -days 11000 -in test-ca.csr -signkey test-ca.key -out test-ca.cert -extensions v3_ca
```

Generate a client certificate signed by the CA:

```
$ openssl genrsa -out client.key 2048
$ openssl req -new -key client.key -out client.csr -subj "/C=US/CN=Client Certificate"
$ openssl x509 -req -days 11000 -in client.csr -out client.cert -CA test-ca.cert -CAkey test-ca.key -CAcreateserial
```
