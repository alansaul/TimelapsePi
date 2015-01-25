from subprocess import call
import subprocess
import signal
import os
import time
import threading
import Queue
import lightblue as lb
import socket as socket_mod
import sys

# Commands
MOVE = 'MOVE'
CAMERA = 'CAMERA'
SEND = 'SEND'
REQUEST = 'REQUEST'
COMMANDS = [MOVE, CAMERA, SEND, REQUEST]

# Actions
START = 'START'
STOP = 'STOP'
LEFT = 'LEFT'
RIGHT = 'RIGHT'
INFO = 'INFO'
IMAGE = 'IMAGE'
ACTIONS = [START, STOP, LEFT, RIGHT, INFO, IMAGE]

with open("lol_cat.jpg", "rb") as imageFile:
    image_bytes = bytearray(imageFile.read())

def find_connections():
    """Block until a bluetooth connection is made"""
    # print "External"
    # print findservices('00:0D:93:19:C8:68')
    # print findservices('bc:f5:ac:84:81:0c')
    # print finddevices()
    # print findservices(gethostaddr())
    # print gethostclass()
    print "Your address: ", lb.gethostaddr()
    print lb.finddevicename(lb.gethostaddr())
    s = lb.socket()
    # Channel appears to need to be 1 for android to find it?
    s.bind(("", 0))
    print "About to listen"
    s.listen(10)
    print "About to advertise"
    lb.advertise("LightBlueService", s, lb.RFCOMM)
    print "Advertised at {} and listening on channel {}...".format(s.getsockname()[0], s.getsockname()[1])
    print "Waiting to accept"
    #s.setblocking(1)
    conn, addr = s.accept()
    # Set timeout for 1 second
    #s.settimeout(1.0)
    print "Connected by", addr
    return conn, addr, s


class Camera(object):
    def __init__(self):
        self.proc = None
        self.last_photo = None
        self.lock = threading.RLock()
        self.setup_camera()

    def setup_camera(self):
        """Setup camera"""
        print "Setting up"
        self.close()
        time.sleep(1)
        # proc = subprocess.Popen(["gphoto2", "--capture-image-and-download", "-I", "-1", "--force-overwrite"])# shell=True)
        self.proc = subprocess.call(["gphoto2", "--capture-image-and-download", "-I", "-1", "--force-overwrite"], shell=True)
        print "Pausing"
        time.sleep(1)

    def capture(self):
        """Capture image"""
        with self.lock:
            print "Capturing"
            if self.proc is None:
                self.setup_camera()
            self.proc.send_signal(signal.SIGUSR1)
            # os.kill(proc.pid, signal.SIGUSR1)
            print "Signal sent"

    def close(self):
        """Close the capturing scheme"""
        with self.lock:
            print "Closing"
            if self.proc is not None:
                os.kill(self.proc.pid, signal.SIGKILL)
                time.sleep(1)
            call(["killall", "gphoto2"])
            call(["killall", "PTPCamera"])

    def last_image(self):
        """Get the last image"""
        # Call download image, then get the image as a Image file
        with self.lock:
            last_image = None
            return last_image

    def details(self):
        """Get the settings"""
        # Get image capturing details
        with self.lock:
            details = None
            return details


class TimelapseController(object):
    def __init__(self, camera, interval=1, impulse_length=0.5):
        self.camera = camera
        self.interval = interval
        self.impulse_length = impulse_length
        self.timelapse_thread = None
        # Threadsafe requests handling between threads using queue
        self.request_queue = Queue.Queue()
        self.send_queue = Queue.Queue()
        self.finished = False
        self.start()

    def start_timelapse(self):
        """Start timelapse, in thread"""
        if self.timelapse_thread is not None:
            self.timelapse_thread.stop()
            time.sleep(5)
        self.timelapse_thread = TimelapseThread("Timelapse thread: {}".format(self.thread_num),
                                                self.camera, self.interval, self.impulse_length,
                                                self.request_queue, self.send_queue)
        self.timelapse_thread.start()

    def stop_timelapse(self):
        """End timelapse thread"""
        if self.timelapse_thread is not None:
            self.timelapse_thread.stop()

    def listen_bluetooth(self):
        """Listen to bluetooth"""
        self.bluetooth = BluetoothThread(self.request_queue, self.send_queue, self.conn)
        self.bluetooth.start()

    def stop_bluetooth(self):
        """Stop listening on bluetooth"""
        if self.bluetooth_thread is not None:
            self.bluetooth_thread.stop()

    def start(self):
        """
        Annoyingly lightblue doesn't appear threadsafe to accept connections
        Thus much of this logic belongs in the bluetooth thread
        """
        print "Starting to listen"
        while not self.finished:
            try:
                self.conn, self.addr, self.socket = find_connections()
                self.conn.settimeout(5)
                self.listen_bluetooth()
                self.mediate()
            except KeyboardInterrupt:
                self.stop()
            except socket_mod.error, e:
                print e
                time.sleep(1)
            finally:
                print "Closing"
                if self.conn is not None:
                    self.conn.close()
                if self.sock is not None:
                    self.sock.close()

    def mediate(self):
        while not self.finished:
            print "Waiting for request"
            command, action, data = self.request_queue.get(block=True)
            print "Got c: {} a: {}, d: {}".format(command, action, data)
            if command == MOVE and action == START:
                self.start()
            elif command == MOVE and action == STOP:
                self.stop()
            elif command == CAMERA and action == IMAGE:
                image_data = self.camera.last_image()
                self.send_queue.put((SEND, IMAGE, image_data))
            elif command == CAMERA and action == INFO:
                details_data = self.camera.details()
                self.send_queue.put((SEND, INFO, details_data))
            else:
                print "Not a request I can handle yet"

    def stop(self):
        self.stop_bluetooth()
        self.stop_timelapse()
        self.finished = True


class BluetoothThread(threading.Thread):
    def __init__(self, request_queue, send_queue, conn):
        threading.Thread.__init__(self)
        self.request_queue = request_queue
        self.send_queue = send_queue
        self.finished = False
        self.conn = conn

    def run(self):
        print "Bluetooth Thread started"
        while not self.finished:
            self.send_receive()

        print "Bluetooth Thread stopped"

    def send_receive(self):
        """Block waiting to receive and then send response"""
        print "Send and receive called"
        try:
            # Wait for a second for communication then handle sending things back
            print "Waiting to receive"
            command_str = self.conn.recv(1024)
            print "Recieved: {}".format(command_str)
            sys.stdout.flush()
            self.perform_command(command_str)
        except socket_mod.timeout:
            # When we get a break in requests, handle sending back data
            print "Timed out, no requests came in"
        finally:
            print "Handling any sending requests"
            if not self.send_queue.empty():
                #No garentee that it is not empty if there is more than 1 thing getting the send queue
                command, action, data = self.send_queue.get(blocking=False)
                self.send(command, action, data)

    def send(self, command, action, data):
        """Send data"""
        print "Send {}: {}".format(CAMERA, action)
        if action == IMAGE:
            # image_bytes = data
            # image_bytes = self.camera.get_image()
            file_bytesize = len(image_bytes)
            image_str = str(image_bytes)
            data = CAMERA + "#" + IMAGE + "#" + str(file_bytesize)
            self.conn.send(data)
            print "Sending image"
            self.conn.send(image_str)
            print "Sent image"
        elif action == INFO:
            # details = data
            details = "these, are, some, details"
            data = CAMERA + "#" + INFO + "#" + details
            print "Sending details"
            self.conn.send(data)

    def perform_command(self, command_str):
        """Perform a generic action"""
        print "Received command: {}".format(command_str)
        commands = command_str.split('#')
        if len(commands) == 2:
            command, action = commands
            data = None
            if command in COMMANDS and action in ACTIONS:
                self.requests.put((command, action, data))
            else:
                print "Corrupt action"
        else:
            print "Not a command, must be of form COMMAND#ACTION"

    def stop(self):
        """Initiate the stopping of bluetooth thread"""
        self.finished = True


class TimelapseThread(threading.Thread):
    def __init__(self, name, camera, interval, impulse_length, request_queue):
        threading.Thread.__init__(self)
        self.interval = interval
        self.impulse_length = impulse_length
        self.lock = threading.RLock()
        self.finished = False
        self.request_queue = request_queue

    def run(self):
        """Run timelapse"""
        while not self.finished:
            self.move()
            time.sleep(self.impulse_length)
            self.capture()
            # Wait for correct amount of time

    def stop(self):
        """Initiate the stopping of timelapsing thread"""
        self.finished = True

    def move(self):
        """Send inpulse through GPIO"""
        with self.lock:
            print "Moving {}".format(self.directon)

    def capture(self):
        """Capture"""
        with self.lock:
            self.camera.capture()


# print "Setting up gphoto"
camera = Camera()
# print "Taking photos"
# for i in range(5):
    # camera.capture()
    # print "Taken photo ", i
    # time.sleep(5)

# camera.close()

timelapse_controller = TimelapseController(camera, interval=1, impulse_length=0.5)
