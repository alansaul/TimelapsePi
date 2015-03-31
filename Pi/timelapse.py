import lightblue as lb
import time
import threading
import Queue
import socket as socket_mod
from capture import Camera

# Commands
MOVE = 'MOVE'
CAMERA = 'CAMERA'
SEND = 'SEND'
REQUEST = 'REQUEST'
COMMANDS = [MOVE, CAMERA, SEND, REQUEST]

# Actions
START = 'START'
STOP = 'STOP'
SHUTDOWN = 'SHUTDOWN'
LEFT = 'LEFT'
RIGHT = 'RIGHT'
INFO = 'INFO'
IMAGE = 'IMAGE'
SETTINGS_CHANGE= 'SETTINGS_CHANGE'
ACTIONS = [START, STOP, SHUTDOWN, LEFT, RIGHT, INFO, IMAGE, SETTINGS_CHANGE]

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
    s.bind(("", 0))  # RFCOMM port
    # s.bind(("", 2))  # RFCOMM port
    print "About to listen"
    s.listen(1)
    print "About to advertise"
    lb.advertise("LightBlueService", s, lb.RFCOMM)
    print "Advertised at {} and listening on channel {}...".format(s.getsockname()[0], s.getsockname()[1])
    print "Waiting to accept"
    # s.setblocking(1)
    try:
        conn, addr = s.accept()
    except KeyboardInterrupt:
        print "Closing connection due to keyboard intterupt"
        s.close()
        raise KeyboardInterrupt
    # Set timeout for 1 second
    # s.settimeout(1.0)
    print "Connected by", addr
    return conn, addr, s


class BluetoothController(object):
    def __init__(self, camera, interval=1, pulse_length=30):
        self.camera = camera
        self.interval = interval
        self.pulse_length = pulse_length
        # Threadsafe requests handling between threads using queue
        self.request_queue = Queue.Queue()
        self.send_queue = Queue.Queue()
        self.finished = False
        self.conn = None
        self.sock = None
        self.start()

    def start(self):
        """
        Annoyingly lightblue doesn't appear threadsafe to accept connections
        Thus much of this logic belongs in the bluetooth thread
        """
        print "Starting BluetoothController"
        self.request_handler = RequestHandler(self.request_queue, self.send_queue,
                                              self.camera, self.interval, self.pulse_length)
        self.request_handler.register_stop(self.stop)
        self.request_handler.start()
        #self.request_handler.start_timelapse()
        while not self.finished:
            try:
                self.conn, self.addr, self.sock = find_connections()
                self.conn.settimeout(1)
                self.send_receive()
            except KeyboardInterrupt:
                print "Keyboard interrupt, stopping"
                self.stop()
            except socket_mod.error, e:
                print "Yelp! stopping"
                print e
                self.close_conn()

        print "BluetoothController closing"

    def close_conn(self):
        print "Closing connection"
        if self.conn is not None:
            try:
                self.conn.shutdown(socket_mod.SHUT_RDWR)
            except Exception:
                print "Failed to shutdown conn"
            try:
                self.conn.close()
            except Exception:
                print "Failed to close conn"

        print "Closing socket"
        if self.sock is not None:
            try:
                self.sock.shutdown(socket_mod.SHUT_RDWR)
            except Exception:
                print "Failed to shutdown sock"
            try:
                self.sock.close()
            except Exception:
                print "Failed to close sock"
        print "Closed socket and connection"

    def send_receive(self):
        while not self.finished:
            try:
                """
                Should use select here to avoid a delay in handling sends, lightblue
                does not implement fileno though in its sockets... sigh. Switch to
                pybluez when using linux (refactor accordingly as bluetooth will
                probably be possible to run in a thread aswell.)
                """
                command_str = self.conn.recv(1024)
                self.perform_command(command_str)
                self.perform_send()
            except socket_mod.timeout:
                # When we get a break in requests, handle sending back data
                # print "Timed out, no requests came in"
                pass
            finally:
                self.perform_send()

    def perform_send(self):
        while not self.send_queue.empty():
            # No garentee that it is not empty if there is more than 1 thing getting the send queue
            command, action, data = self.send_queue.get()
            self.send(command, action, data)

    def send(self, command, action, data):
        """Send data"""
        print "Send {}: {}".format(CAMERA, action)
        if action == IMAGE:
            image_bytes = data
            file_bytesize = len(image_bytes)
            image_str = str(image_bytes)
            data = CAMERA + "#" + IMAGE + "#" + str(file_bytesize)
            print data
            self.conn.send(data)
            print "Sending image"
            self.conn.send(image_str)
            print "Sent image"
        elif action == INFO:
            # details = "these, are, some, details"
            details = data
            data = CAMERA + "#" + INFO + "#" + details
            print "Sending details"
            self.conn.send(data)

    def perform_command(self, command_str):
        """Perform a generic action"""
        print "Received command: {}".format(command_str)
        commands = command_str.split('#')
        if len(commands) == 2:
            data = None
        elif len(commands) > 2:
            data = commands[2:]
        elif command_str == '':
            # Gets received when the server socket is closed (should be -1)
            print "Command suggests socket is being closed, ignore this command and close the socket"
            self.stop()
        else:
            print "Not a command, must be of form COMMAND#ACTION: {}".format(command_str)

        if len(commands) >= 2:
            command = commands[0]
            action = commands[1]
            if command in COMMANDS and action in ACTIONS:
                self.request_queue.put((command, action, data))
            else:
                print "Corrupt action"

    def stop(self):
        """Close the socket and indicate the loop should finish"""
        self.close_conn()
        self.finished = True
        self.request_handler.stop()
        print "Finished closing"


class RequestHandler(threading.Thread):
    def __init__(self, request_queue, send_queue, camera, interval, pulse_length):
        threading.Thread.__init__(self)
        self.request_queue = request_queue
        self.send_queue = send_queue
        self.camera = camera
        self.interval = interval
        self.pulse_length = pulse_length
        self.thread_num = 1
        self.timelapse_thread = None
        self.finished = False
        self.stop_callbacks = []
        self.setDaemon(True)
        # 24 photos in a second of video, so 1440 photos in a minute
        self.photos_per_second = 1440

    def run(self):
        while not self.finished:
            print "Waiting for request"
            try:
                command, action, data = self.request_queue.get(block=True, timeout=1)
                print "Got command: {} action: {}, data: {}".format(command, action, data)
                if command == MOVE and action == LEFT:
                    self.change_direction(LEFT)
                if command == MOVE and action == RIGHT:
                    self.change_direction(RIGHT)
                elif command == REQUEST and action == START:
                    self.start_timelapse()
                elif command == REQUEST and action == STOP:
                    self.stop_timelapse()
                elif command == REQUEST and action == SHUTDOWN:
                    self.shutdown_pi()
                elif command == CAMERA and action == IMAGE:
                    image_data = self.camera.last_image()
                    print "Got image ", image_data
                    if image_data is not None:
                        self.send_queue.put((SEND, IMAGE, image_data))
                elif command == CAMERA and action == INFO:
                    details_data = self.camera.details()
                    if details_data is not None:
                        self.send_queue.put((SEND, INFO, details_data))
                elif command == REQUEST and action == SETTINGS_CHANGE:
                    self.change_settings(data)
                else:
                    print "Not a request I can handle yet"
            except Queue.Empty:
                pass
        print "HANDLER FINALLY CLOSING"
        for callback in self.stop_callbacks:
            callback()

    def change_settings(self, data):
        """
        Theres been a change of settings, next time the timelapse
        is started these settings will be used
        """
        if len(data) == 3:
            try:
                minutes = int(data[0])
                percent = int(data[1])
                length = int(data[2])
                shutterspeed = 1./250  # FIXME: Need to get this from the camera
            except Exception, e:
                print "Something wrong with input data"
                print e
            try:
                interval, pulse_width = self.calculate_interval(minutes, percent, length, shutterspeed)
                print "Changing interval to {} and pulsewidth to {} seconds".format(interval, pulse_width)
                self.interval = interval
                self.pulse_width = pulse_width
            except ValueError, e:
                print e
            except AssertionError, e:
                print e
        else:
            print "It seems some settings were missing, ignoring change"

    def calculate_interval(self, minutes, percent, length, shutterspeed):
        """
        Calculate the interval (delay) and pulse length (distance) given settings
        """
        print "Calculating interval"
        num_photos = 60 * minutes * self.photos_per_second
        # Say it takes 0.5 seconds to respond to a request to take a photo
        response_time = 0.5
        photos_per_second = shutterspeed + response_time
        capture_time_seconds = photos_per_second*num_photos
        print "Seconds per photo: ", photos_per_second
        print "Overall photos to take: ", num_photos
        print "Number of minutes just to take photos", (capture_time_seconds)/float(60)
        # Sanity check that it is possible to have this shutterspeed and length combination
        left_over_seconds = float(minutes*60 - capture_time_seconds)
        print "{} left over seconds out of {}".format(left_over_seconds, minutes*60)
        if left_over_seconds > 0:
            pulse_length = left_over_seconds / num_photos
            interval = photos_per_second
            # Sanity check that our maths is right
            timelapse_length_minutes = ((pulse_length + interval)*num_photos)/60.0
            print "Timelapse will take {} minutes".format(timelapse_length_minutes)
            assert timelapse_length_minutes < minutes
            assert timelapse_length_minutes > minutes/2.0
            return interval, pulse_length
        else:
            raise ValueError("Not enough seconds to use make this timelapse")

    def shutdown_pi(self):
        """Shutdown Pi gracefully"""
        print "Shutting down Pi"
        self.stop()

    def start_timelapse(self):
        """Start timelapse, in thread"""
        print "Starting timelapse thread"
        if self.timelapse_thread is not None:
            self.timelapse_thread.stop()
            time.sleep(5)
        self.timelapse_thread = TimelapseThread("Timelapse thread: {}".format(self.thread_num),
                                                self.camera, self.interval, self.pulse_length,
                                                self.request_queue)
        self.timelapse_thread.start()
        self.thread_num = self.thread_num + 1

    def stop_timelapse(self):
        """End timelapse thread"""
        print "Stopping timelapse thread"
        if self.timelapse_thread is not None:
            self.timelapse_thread.stop()

    def stop(self):
        print "Stopping thread"
        self.stop_timelapse()
        self.finished = True
        print "Still stopping thread"

    def register_stop(self, callback):
        self.stop_callbacks.append(callback)


class TimelapseThread(threading.Thread):
    def __init__(self, name, camera, interval, pulse_length, request_queue):
        threading.Thread.__init__(self)
        self.camera = camera
        self.interval = interval
        self.pulse_length = pulse_length
        self.lock = threading.RLock()
        self.finished = False
        self.request_queue = request_queue
        self.direction = LEFT

    def run(self):
        """Run timelapse"""
        while not self.finished:
            self.move()
            # Wait for correct amount of time
            time.sleep(self.pulse_length)
            self.capture()

    def stop(self):
        """Initiate the stopping of timelapsing thread"""
        self.finished = True

    def move(self):
        """Send inpulse through GPIO"""
        with self.lock:
            print "Moving {} for {} seconds".format(self.direction, self.pulse_length)
            # Send pulse
            time.sleep(self.pulse_length)
            # Stop pulse

    def capture(self):
        """Capture"""
        with self.lock:
            self.camera.capture()
            # Wait amount of time for image to be captured
            time.sleep(self.interval)


# print "Setting up gphoto"
camera = Camera()
"""
# print "Taking photos"
# for i in range(5):
    # camera.capture()
    # print "Taken photo ", i
    # time.sleep(5)
"""

# camera.close()

# bluetooth_controller = BluetoothController(camera, interval=1, pulse_length=5)
try:
    bluetooth_controller = BluetoothController(camera, interval=1, pulse_length=5)
except Exception, e:
    print "Something went horribly wrong"
    print e
finally:
    camera.close()
