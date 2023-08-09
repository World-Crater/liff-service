#!/bin/bash

docker build . --platform linux/amd64 -t=superj80820/liff-service
docker push superj80820/liff-service