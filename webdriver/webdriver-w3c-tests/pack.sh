#!/bin/bash
source $(dirname $0)/webdriver-w3c-tests.spec

#parse params
usage="Usage: ./pack.sh [-t <package type: apk | xpk>] [-a <apk runtime arch: x86 | arm>]
[-t apk] option was set as default.
[-a x86] option was set as default.
"
if [[ $1 == "-h" || $1 == "--help" ]]; then
    echo "$usage"
    exit 1
fi

type="apk"
arch="x86"
while getopts t:a: o
do
    case "$o" in
    t) type=$OPTARG;;
    a) arch=$OPTARG;;
    *) echo "$usage"
       exit 1;;
    esac
done

if [[ $type == "apk" || $type == "xpk" ]]; then
    echo "Create package with $type and raw source"
else
    echo "Sorry,$type is not support... >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>"
    echo "$usage"
    exit 1
fi

if [[ -z $name || -z $version || -z $appname ]]; then
    echo "Package name or version not specified in setting file"
    exit 1
fi

SRC_ROOT=$PWD
RESOURCE_DIR=/home/app/content
BUILD_ROOT=/tmp/${name}_pack
BUILD_DEST=/tmp/${name}

# check precondition
function check_precondition(){
    which $1 > /dev/null 2>&1
    if [ $? -ne 0 ]; then
        echo "Error: no tool: $1"
        exit 1
    fi
}
check_precondition autoreconf
check_precondition gcc
check_precondition make

# clean
function clean_workspace(){
    echo "cleaning workspace... >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>"
    rm -rf $BUILD_ROOT $BUILD_DEST
}

clean_workspace
mkdir -p $BUILD_ROOT $BUILD_DEST

# copy source code
rm -rf *.apk *.tar.bz2 *.tar.gz *.zip
cp -arf $SRC_ROOT/* $BUILD_ROOT/


function create_apk(){
    cd $BUILD_ROOT

    # copy html to apk folder
    find -name *.html |grep -v support |xargs -I% cp --parents % $BUILD_ROOT/support/XwalkDriverTest

    cp -r $SRC_ROOT/../../tools/crosswalk $BUILD_ROOT/crosswalk

    # enable remote debugging
    temp=$BUILD_ROOT/crosswalk/template/src/org/xwalk/app/template/AppTemplateActivity.java
    line=`sed -n '/super.onCreate/=' $temp | tail -n1`
    sed -i "${line}s/.*/setRemoteDebugging(true);\n&/" $temp

    cd $BUILD_ROOT/crosswalk
    python make_apk.py --manifest=$BUILD_ROOT/support/XwalkDriverTest/manifest.json --arch=$arch --package=org.xwalk.xwalkdrivertest

    if [ $? -ne 0 ];then
        echo "Create $name.apk fail.... >>>>>>>>>>>>>>>>>>>>>>>>>"
        clean_workspace
        exit 1
    fi
}

function create_xpk(){
cd $BUILD_ROOT
}

function zip_for_apk(){
    cd $BUILD_DEST
    mkdir -p $BUILD_DEST/opt/$name/
    cp -af $BUILD_ROOT/* $BUILD_DEST/opt/$name/
    mv $BUILD_ROOT/crosswalk/*.apk $BUILD_DEST/opt/$name/

    # Remove files in blacklist
    if [ $src_file -eq 1 ];then
        for file in $(ls opt/$name |grep -v apk);do
            if [[ "${blacklist[@]}" =~ $file ]];then
                echo "Remove unnessary file:$file..."
                rm -rf opt/$name/$file
            fi
        done
    fi
    find -name *.html |xargs -I% rm %
    # Rename all test py t
    for i in `find -name '*.py' | grep -v client|grep -v __init__.py|grep -v webserver.py|grep -v base_test.py|grep -v network.py|grep -v runtests.py`
    do
        mv $i `dirname ${i}`/test_`basename ${i}`
    done
    zip -Drq $BUILD_DEST/$name-$version.$type.zip opt/
    if [ $? -ne 0 ];then
        echo "Create zip package fail... >>>>>>>>>>>>>>>>>>>>>>>>>"
        clean_workspace
        exit 1
    fi
}

function zip_for_xpk(){
cd $BUILD_ROOT
}

## create package
case $type in
    apk) create_apk
         zip_for_apk;;
    xpk) create_xpk
         zip_for_xpk;;
esac

# copy zip file
echo "copy package from workspace... >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>"
cp -f $BUILD_DEST/$name-$version.$type.zip $SRC_ROOT/$name-$version.$type.zip

# clean workspace
clean_workspace

# validate
echo "checking result... >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>"
if [ -z "`ls $SRC_ROOT | grep "\.zip"`" ];then
    echo "------------------------------ FAILED to build $name packages --------------------------"
    exit 1
fi

echo "------------------------------ Done to build $name packages --------------------------"
cd $SRC_ROOT
ls *.zip 2>/dev/null
