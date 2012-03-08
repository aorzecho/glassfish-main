#!/bin/sh
#
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
#
# Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
#
# The contents of this file are subject to the terms of either the GNU
# General Public License Version 2 only ("GPL") or the Common Development
# and Distribution License("CDDL") (collectively, the "License").  You
# may not use this file except in compliance with the License.  You can
# obtain a copy of the License at
# https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
# or packager/legal/LICENSE.txt.  See the License for the specific
# language governing permissions and limitations under the License.
#
# When distributing the software, include this License Header Notice in each
# file and include the License file at packager/legal/LICENSE.txt.
#
# GPL Classpath Exception:
# Oracle designates this particular file as subject to the "Classpath"
# exception as provided by Oracle in the GPL Version 2 section of the License
# file that accompanied this code.
#
# Modifications:
# If applicable, add the following below the License Header, with the fields
# enclosed by brackets [] replaced by your own identifying information:
# "Portions Copyright [year] [name of copyright owner]"
#
# Contributor(s):
# If you wish your version of this file to be governed by only the CDDL or
# only the GPL Version 2, indicate your decision by adding "[Contributor]
# elects to include this software in this distribution under the [CDDL or GPL
# Version 2] license."  If you don't indicate a single choice of license, a
# recipient has the option to distribute your version of this file under
# either the CDDL, the GPL Version 2 or to extend the choice of license to
# its licensees as provided above.  However, if you add GPL Version 2 code
# and therefore, elected the GPL Version 2 license, then the option applies
# only if the new code is made subject to such option by the copyright
# holder.
#

# This script helps setup services for running PaaS native, kvm or ovm tests.
# setup.sh -r -d <templates-dir> -s jee,javadb,mysql,oracle,apachemodjk,otd,lb native|kvm|ovm
# Examples:
#   1) Plain native setup
#      setup.sh native
#   2) Native with lb service
#      setup.sh -s lb native
#   3) KVM with jee service
#      setup.sh -t /kvm/images -s jee kvm
#   4) KVM with jee,javadb,apachemodjk services
#      setup.sh -t /kvm/images -s jee,javadb,apachemodjk kvm
#   5) OVM with jee service
#      setup.sh -t /ovm/templates -s jee ovm
#   6) OVM with jee,otd services
#      setup.sh -t /ovm/templates -s jee,otd ovm
#   7) Recreate domain1 before setting up
#      setup.sh -r native
# Author: Yamini K B
# Date  : 8-FEB-2012

GF_HOME=${GF_HOME:-$S1AS_HOME}
USAGE="Usage: $(basename $0) -r -d <templates-dir> -s jee,javadb,mysql,oracle,apachemodjk,otd,lb native|kvm|ovm"

[ -z "$GF_HOME" ] && echo "Please set GF_HOME or S1AS_HOME" && exit 1;

[ $# -lt 1 ] && echo $USAGE && exit 1;

log ()
{
    echo "\033[33;33m$1\033[0m"
}

err ()
{
    echo "\033[33;31m$1\033[0m"
}

setup_native ()
{
    log "Configuring NATIVE mode...."
    $A start-domain --debug
    $A create-ims-config-native

    IFS_TMP=$IFS
    IFS=","
    for s in $SERVICES
    do
        case $s in
          "lb") log "Creating template for lb service..."
                $A create-template --indexes ServiceType=LB,VirtualizationType=Native LBNative
                ;;
             *) err "Ignoring unknown service $s"
                ;;
        esac
    done
    IFS=$IFS_TMP

    $A stop-domain
    log "Successfully configured NATIVE mode...."
}

setup_kvm ()
{
    log "Configuring KVM...."
    log "Removing stale files..."
    rm -f /tmp/helloworld?.xml
    #rm -rf ~/virt/*
    rm -rf ~/virt/disks/*

    $A start-domain --debug
    $A create-ims-config-libvirt kvm
    $A set virtualizations.libvirt-virtualization.kvm.template-cache-size=0
    $A create-jvm-options -Dorg.glassfish.paas.orchestrator.parallel-provisioning=true
    $A restart-domain --debug
    $A create-server-pool --virtualization kvm --subnet 192.168.122.0/24 --portName "virbr0" cloud

    $A create-machine --serverPool cloud --networkName localhost local
    $A create-machine-user --serverPool cloud --machine local --userId 1000 --groupId 1000 $USER

    IFS_TMP=$IFS
    IFS=","
    for s in $SERVICES
    do
        case $s in
                 "jee") log "Creating template for jee service..."
                         $A create-template --virtualization kvm --files $templates_dir/glassfish.img,$templates_dir/glassfish.xml --indexes ServiceType=JavaEE,VirtualizationType=libvirt glassfish
                         $A create-template-user --virtualization kvm --template glassfish cloud
                         ;;
               "javadb") log "Creating template for javadb service..."
                         $A create-template --virtualization kvm --files $templates_dir/glassfish.img,$templates_dir/glassfish.xml --indexes ServiceType=Database,VirtualizationType=libvirt javadb
                         $A create-template-user --virtualization kvm --template javadb cloud
                         ;;
                "mysql") log "Creating template for mysql service..."
                         $A create-template --virtualization kvm --files $templates_dir/MySQL.img,$templates_dir/MySQL.xml --indexes ServiceType=Database,VirtualizationType=libvirt MySQL
                         $A create-template-user --virtualization kvm --template MySQL mysqluser
                         ;;
               "oracle") log "Creating template for oracle service..."
                         $A create-template --virtualization kvm --files $templates_dir/oracledb.img,$templates_dir/oracledb.xml --indexes ServiceType=Database,VirtualizationType=libvirt oracledb
                         $A create-template-user --virtualization kvm --template oracledb shalinikvm
                         ;;
          "apachemodjk") log "Creating template for apachemodjk service..."
                         $A create-template --virtualization kvm --files $templates_dir/apache.img,$templates_dir/apache.xml --indexes ServiceType=LB,VirtualizationType=libvirt apachemodjk
                         $A create-template-user --virtualization kvm --template apachemodjk cloud
                         ;;
                     *) err "Ignoring unknown service $s"
                         ;;
        esac 
    done
    IFS=$IFS_TMP

    #copying of the template into ~/virt/templates takes about 2 min.
    log "Copying of template(s) into ~/virt/templates in progress..."
    sleep 100
    ls -l ~/virt/templates/glassfish

    $A stop-domain
    log "Successfully configured KVM...."
}

CONNECTION_STRING="http://admin:abc123@sf-x2200-7.in.oracle.com:8888;root:abc123"
SUBNET="10.178.214.0/24"

setup_ovm ()
{
    log "Configuring OVM...."
    $A start-domain domain1
    $A set configs.config.server-config.network-config.protocols.protocol.admin-listener.http.request-timeout-seconds=-1
    $A create-jvm-options -Dorg.glassfish.paas.orchestrator.parallel-provisioning=true

    $A create-ims-config-ovm --connectionstring $CONNECTION_STRING  ovm
    $A create-server-pool --subnet $SUBNET --portname "foobar" --virtualization ovm pool2

    IFS_TMP=$IFS
    IFS=","
    for s in $SERVICES
    do
        case $s in
            "jee") log "Creating template for jee service..."
                    touch $templates_dir/glassfish.tgz
                    $A create-template --files $templates_dir/glassfish.tgz --indexes ServiceType=JavaEE,VirtualizationType=OVM glassfish
                    $A create-template-user --virtualization ovm --template glassfish glassfish
                    ;;
          "oracle") log "Creating template for oracle service..."
                    touch $templates_dir/ORACLEDB.tgz
                    $A create-template --files $templates_dir/ORACLEDB.tgz --indexes ServiceType=Database,VirtualizationType=OVM ORACLE_DATABASE
                    $A create-template-user --virtualization ovm --template ORACLE_DATABASE oracle
                    ;;
           "derby") log "Creating template for derby service..."
                    touch $templates_dir/DERBY_DATABASE.tgz
                    $A create-template --files $templates_dir/DERBY_DATABASE.tgz --indexes ServiceType=Database,VirtualizationType=OVM DERBY_DATABASE
                    $A create-template-user --virtualization ovm --template DERBY_DATABASE glassfish
                    ;;
             "otd") log "Creating template for otd service..."
                    touch $templates_dir/OTD_LARGE.tgz
                    $A create-template --files $templates_dir/OTD_LARGE.tgz --properties vendor-name=otd --indexes ServiceType=LB,VirtualizationType=OVM otd-new
                    $A create-template-user --virtualization ovm --template otd-new cloud
                    ;;
                *) err "Ignoring unknown service $s"
                    ;;
        esac
    done
    IFS=$IFS_TMP

    $A stop-domain
    log "Successfully configured OVM...."
}

log "GlassFish is at $GF_HOME"

A=$GF_HOME/bin/asadmin

while getopts rd:s: opt
do
  case ${opt} in
    r) log "Recreating domain1..."
       $A stop-domain domain1
       $A delete-domain domain1
       $A create-domain --adminport 4848 --nopassword domain1
       ;;
    s) SERVICES=$OPTARG
       ;;
    d)
       if [ -d "$OPTARG" ]   # Check if dir exists
       then
         templates_dir=$OPTARG
       else
         err "Directory \"$OPTARG\" does not exist."
         exit 2
       fi
       ;;
    \?) echo $USAGE
        exit 2;;
  esac
done

shift $(($OPTIND - 1))

case "$1" in
  "native") setup_native
            ;;
     "kvm") setup_kvm
            ;;
     "ovm") setup_ovm
            ;;
         *) echo $USAGE
            exit 2;;
esac

exit 0
