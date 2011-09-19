#
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
#
# Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
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

echo "Your GlassFish is at $S1AS_HOME"
rm $S1AS_HOME/modules/paas.lbplugin.jar
rm $S1AS_HOME/modules/paas.javadbplugin.jar
$S1AS_HOME/bin/asadmin start-domain --debug
$S1AS_HOME/bin/asadmin create-ims-config-libvirt kvm
#$S1AS_HOME/bin/asadmin create-server-pool --virtualization kvm --subnet 192.168.1.102/250 --portName "br0" cloud
$S1AS_HOME/bin/asadmin create-server-pool --virtualization kvm --subnet 129.158.193.70/250 --portName "br0" cloud
$S1AS_HOME/bin/asadmin create-server-pool-user --serverPool cloud --userId 1001 --groupId 1001 cloud
$S1AS_HOME/bin/asadmin create-template --virtualization kvm --files /space/bhavani/Desktop/glassfish.img,/space/bhavani/Desktop/glassfish.xml --indexes ServiceType=JavaEE,VirtualizationType=libvirt glassfish
$S1AS_HOME/bin/asadmin create-template-user --virtualization kvm --userid 1000 --groupid 1000 --template glassfish cloud
$S1AS_HOME/bin/asadmin create-machine --serverPool cloud --networkName localhost local
$S1AS_HOME/bin/asadmin create-machine-user --serverPool cloud --machine local --userId 1000 --groupId 1000 bhavani

#$S1AS_HOME/bin/asadmin create-emulator --virt-type kvm --emulator-path /usr/bin/kvm --connection-string qemu:///system kvm
#$S1AS_HOME/bin/asadmin add-virtualization --type libvirt --emulator kvm
#$S1AS_HOME/bin/asadmin create-server-pool --subnet 192.168.1.102/250 --portName "br0" --virtName libvirt cloud
#$S1AS_HOME/bin/asadmin create-server-pool --subnet 129.158.239.70/250 --portName "br0" --virtName libvirt cloud
#$S1AS_HOME/bin/asadmin create-template --files /space/bhavani/Desktop/glassfish.img,/space/bhavani/Desktop/glassfish.xml --indexes ServiceType=JavaEE,VirtualizationType=libvirt glassfish
#$S1AS_HOME/bin/asadmin create-template-user --userid 1000 --groupid 1000 --template glassfish cloud
#$S1AS_HOME/bin/asadmin create-machine --serverPool cloud --networkName localhost local
#$S1AS_HOME/bin/asadmin create-machine-user --serverPool cloud --machine local --userId 1000 --groupId 1000 bhavani
#$S1AS_HOME/bin/asadmin stop-domain
