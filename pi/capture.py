from subprocess import call
import subprocess
import os
import threading
import time
import signal

class Camera(object):
    def __init__(self,  folder="/store_00010001/DCIM/101D7000", hook_script="process_image.sh", latest_image_fname="last_image.jpg", latest_exif_fname='latest_exif.txt'):
        self.proc = None
        self.last_photo = None
        self.storage_folder = folder
	self.hook_script = hook_script
        self.latest_image_fname = latest_image_fname
        self.latest_exif_fname = latest_exif_fname
	#self.filename = filename
	#filename="capt%y%m%d%H%M%S.jpg",
        self.lock = threading.RLock()
        self.setup_camera()

    def setup_camera(self):
        """Setup camera"""
        print "Setting up"
        self.close()
        time.sleep(1)
	#Set capture target as the memory card to reduce lag 
	subprocess.Popen(["gphoto2", "--set-config", "capturetarget=1"])
	time.sleep(1)
	#Setup capture with interval -1 so it is waiting for SIGUSR1
        #self.proc = subprocess.Popen(["gphoto2", "--capture-image", "-I", "-1", "&"])
	self.proc = subprocess.Popen(["gphoto2", "--capture-image-and-download", "--keep", "-I", "-1", "--force-overwrite", "--filename", self.latest_image_fname, "--hook-script={}".format(self.hook_script), "&"])
	
	#"--filename={}".format(self.filename), 
        print "Pausing"
        time.sleep(1)

    def capture(self):
        """Capture image"""
        with self.lock:
            print "Capturing"
            if self.proc is None:
                self.setup_camera()
            self.kill_PTP()
            self.proc.send_signal(signal.SIGUSR1)
            print "Signal sent"

    def close(self):
        """Close the capturing scheme"""
        with self.lock:
            print "Closing"
            if self.proc is not None:
                print "Killing existing process"
                os.kill(self.proc.pid, signal.SIGKILL)
                time.sleep(1)
            call(["killall", "gphoto2"])
            call(["killall", "PTPCamera"])
            print "Closed"

    def kill_PTP(self):
        print "Killing PTP"
        call(["killall", "PTPCamera"])

    def last_image(self):
        """Get the last image"""
        # Call download image, then get the image as a Image file
        with self.lock:
            latest_image_bytes = None
	    #out = subprocess.check_output(["gphoto2", "--folder={}".format(self.storage_folder), "-n"])
	    #latest_image_fname_id = out.split(':')[-1].strip()
            #subprocess.Popen(["gphoto2", "--folder={}".format(self.storage_folder), "--get-file", latest_image_fname_id])
            if os.path.isfile(self.latest_image_fname):
                with open(self.latest_image_fname, "rb") as imageFile:
		    latest_image_bytes = bytearray(imageFile.read())
                    print "Got the latest image from the camera"
            else:
                print "File does not exist yet"
            return latest_image_bytes

    def details(self):
        """Get the settings"""
        # Get image capturing details
        with self.lock:
	    details = 'No info available'
            if os.path.isfile(self.latest_image_fname):
                with open(self.latest_exif_fname, 'r') as f:
		    details = f.read()
            return details

if __name__ == '__main__':
    camera = Camera()
