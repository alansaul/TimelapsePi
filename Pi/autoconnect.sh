#!/bin/bash
#Need bluez-compat installed (probably aswell as other bluetooth things)
#Need to have DisablePlugins = pnat 

#Then sudo invoke-rc.d bluetooth restart
devices=($(hcitool scan | awk '{if (NR!=1){ print $1}}'))

for i in "${devices[@]}"
do
    echo "Connecting to $i with passcode 0000"
    sudo bluetooth-agent 0000 $i
    echo 0000 | sudo bluez-simple-agent hci0 $i

    bluetooth_text="
    rfcomm0 {\n
    # Automatically bind the device at startup\n
    bind yes;\n
    # Bluetooth address of the device\n
    device $i;\n
    # RFCOMM channel for the connection\n
    channel 1;\n
    # Description of the connection\n
    comment \"This is Device 1's serial port.\";\n
    }\n
    "
    echo -e $bluetooth_text | sudo tee -a /etc/bluetooth/rfcomm.conf
    sudo rfcomm connect rfcomm0
done

result=$(hcitool scan)
echo $result
