#!/bin/bash
#Need bluez-compat installed (probably aswell as other bluetooth things)
#Need to have DisablePlugins = pnat 

#Then sudo invoke-rc.d bluetooth restart
devices=($(hcitool scan | awk '{if (NR!=1){ print $1}}'))

for i in "${devices[@]}"
do
    echo "Connecting to $i with passcode 0000"
    sudo bluetooth-agent 0000 $i
done
