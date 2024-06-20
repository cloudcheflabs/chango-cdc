#!/bin/sh

set -ex

export CHANGO_CDC_VERSION=1.1.0
export DEBEZIUM_VERSION=1.9.7.Final


for i in "$@"
do
case $i in
    --version=*)
    CHANGO_CDC_VERSION="${i#*=}"
    shift
    ;;
    --debezium.version=*)
    DEBEZIUM_VERSION="${i#*=}"
    shift
    ;;
    *)
          # unknown option
    ;;
esac
done

echo "CHANGO_CDC_VERSION = ${CHANGO_CDC_VERSION}"
echo "DEBEZIUM_VERSION = ${DEBEZIUM_VERSION}"

# build all.
mvn -e clean install -Ddebezium.version=${DEBEZIUM_VERSION};


export CURRENT_DIR=$(pwd);
export CHANGO_CDC_DIST_NAME=chango-cdc-${CHANGO_CDC_VERSION}-debezium-${DEBEZIUM_VERSION}-linux-x64
export CHANGO_CDC_DIST_BASE=${CURRENT_DIR}/dist
export CHANGO_CDC_DIST_DIR=${CHANGO_CDC_DIST_BASE}/${CHANGO_CDC_DIST_NAME}

rm -rf ${CHANGO_CDC_DIST_BASE}/*

mkdir -p ${CHANGO_CDC_DIST_DIR}/{bin,lib,java,conf};

chmod +x *.sh;
cp *-chango-cdc.sh ${CHANGO_CDC_DIST_DIR}/bin;
cp target/*-shaded.jar ${CHANGO_CDC_DIST_DIR}/lib;
cp src/main/resources/configuration.yml ${CHANGO_CDC_DIST_DIR}/conf;
cp LICENSE ${CHANGO_CDC_DIST_DIR}/

# download jdk.
cd ${CHANGO_CDC_DIST_DIR}/java;
export JAVA_DIST_NAME=openlogic-openjdk-17.0.7+7-linux-x64;
curl -L -O https://github.com/cloudcheflabs/chango-libs/releases/download/chango-private-deps/${JAVA_DIST_NAME}.tar.gz;
tar -zxf ${JAVA_DIST_NAME}.tar.gz;
cp -R ${JAVA_DIST_NAME}/* .;
rm -rf ${JAVA_DIST_NAME}*;

# package as tar.gz.
cd ${CHANGO_CDC_DIST_BASE};
tar -czvf ${CHANGO_CDC_DIST_NAME}.tar.gz ${CHANGO_CDC_DIST_NAME}