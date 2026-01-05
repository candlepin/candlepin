# Broker testing

## Build WAR

```
./gradlew war -Ptest_extensions=hostedtest,manifestgen
```

## Building the Candlepin test image

```
sudo docker build -f ./containers/release.Containerfile -t session-rework:latest . \
	--target development \
	--build-arg WAR_FILE=./build/libs/candlepin-4.7.2.war \
	--no-cache
```

## Running the containers

```
sudo docker compose up -d
```


## Issues

- Only the Jobs sessions are being created