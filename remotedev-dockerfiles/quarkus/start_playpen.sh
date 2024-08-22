#!/bin/bash

echo "Downloading $PLAYPEN_CODE_URL"
curl -v --output /deployments/files.zip $PLAYPEN_CODE_URL
cd /deployments
unzip files.zip
/opt/jboss/container/java/run/run-java.sh