# Broker testing

## Building the Candlepin test image

```
sudo docker build -f ./containers/release.Containerfile -t broker-testing:latest . \
	--target development \
	--build-arg WAR_FILE=./build/libs/candlepin-4.7.1.war \
	--no-cache
```

## Running the containers

```
sudo docker compose up -d
```