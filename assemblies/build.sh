#!/bin/bash


argCmd=$1
buildType=$2
buildZip=$3
WORD_DIR=$(dirname "$PWD")

VERSION=v1.0.2
BUILD_TIME=`date -d today +"%Y%m%d-%H%M%S"`
ARCH=`arch`
source /etc/profile

if [[ ${argCmd} == "build" ]];
then
    if [[ ${buildType} == "all" ]];
    then
        rm -rf build
        mkdir build

        pushd $WORD_DIR > /dev/null
        echo ""
        echo "start to compile grn"
        echo ""
        bash resources/jenkins/build.sh && bash resources/jenkins/release.sh
    fi

    echo ""
    echo "start mv deb to build.."
    echo ""
    mv ../jibri_*.deb assemblies/build/
    rm -f ../jibri_*.changes
    rm -f ../jibri_*.buildinfo
    popd 

    echo ""
    echo "start to build image.."
    echo ""
    docker build -t  numax/docker-grn-$ARCH:$VERSION --ulimit nofile=1024000:1024000 .

    if [[ $buildZip == "zip" ]];
    then
        echo ""
        echo "start to save image.."
        echo ""
        docker save -o docker-grn-$ARCH-$VERSION.tar numax/docker-grn-$ARCH:$VERSION

        echo ""
        echo "start to make onestep zip package.."
        zip docker-grn-$ARCH-$VERSION-build$BUILD_TIME.zip -r INSTALL CLEAN docker-grn-$ARCH-$VERSION.tar
    fi
fi