for OSX we must kill the PTP daemon

    killall PTPCamera

Some testing python code:

    import gphoto2 as gp
    context = gp.Context()
    camera = gp.Camera()
    camera.init(context)
    print camera.get_summary(context)

sudo rpi-update
sudo shutdown -r now
sudo apt-get install python-dev
sudo apt-get install gcc
sudo apt-get install -y libltdl-dev libusb-dev libexif-dev libpopt-dev
sudo apt-get install python-setuptools
easy_install pip
#sudo apt-get install gphoto2
# Upgrade gphoto2 http://
sudo apt-get install --no-install-recommends bluetooth
sudo service bluetooth
sudo service bluetooth status
sudo apt-get install libbluetooth-dev
sudo apt-get install python-bluez
sudo apt-get install python-dev
sudo pip install --upgrade pybluez
sudo apt-get install python-obexftp

sudo apt-get install cmake
wget http://downloads.sourceforge.net/openobex/openobex-1.7.1-Source.tar.gz
tar xfv openobex-1.7.1-Source.tar.gz
cd openobex-1.7.1-Source/
mkdir build
cd build/
cmake ..
make
sudo make install
sudo apt-get install libopenobex1-dev

sudo apt-get install python-lightblue
sudo pip install --upgrade --allow-external lightblue --allow-unverified lightblue lightblue
hcitool scan

git clone https://github.com/alansaul/LapsePi.git
