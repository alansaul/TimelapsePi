from lightblue import *
import socket as socket_mod

#Commands
MOVE = 'MOVE'
CAMERA = 'CAMERA'

#Actions
LEFT = 'LEFT'
RIGHT = 'RIGHT'
INFO = 'INFO'
DETAIL = 'DETAIL'
IMAGE = 'IMAGE'

with open("lol_cat.jpg", "rb") as imageFile:
    image_bytes = bytearray(imageFile.read())

def find_connections():
    #print "External"
    #print findservices('00:0D:93:19:C8:68')
    #print findservices('bc:f5:ac:84:81:0c')
    #print finddevices()

    print "Your address: ", gethostaddr()
    print finddevicename(gethostaddr())
    #print findservices(gethostaddr())
    #print gethostclass()

    s = socket()
    #Channel appears to need to be 1 for android to find it?
    s.bind(("", 0))
    print "about to listen"
    s.listen(10)
    print "about to advertise"
    advertise("LightBlueService", s, RFCOMM)
    print "Advertised at {} and listening on channel {}...".format(s.getsockname()[0], s.getsockname()[1])

    print "Waiting to accept"
    conn, addr = s.accept()
    print "Connected by", addr
    return conn, addr, s

def send_receive(conn, addr):
    while True:
        command = conn.recv(1024)
        perform_command(command, conn)

def perform_move(action, conn):
    print "{}: {}".format(MOVE, action)

def perform_camera(action, conn):
    print "{}: {}".format(CAMERA, action)
    if action == IMAGE:
        file_bytesize = len(image_bytes)
        image_str = str(image_bytes)
        data = CAMERA + "#" + IMAGE + "#" + str(file_bytesize)
        conn.send(data)
        print "send command"
        conn.send(image_str)
        print "sent image"
    elif action == DETAIL:
        details = "these, are, some, details"
        data = CAMERA + "#" + DETAIL + "#" + details
        conn.send(data)

def perform_command(data, conn):
    data = data.split('#')
    if len(data) == 2:
        command, action = data
        if command == MOVE:
            perform_move(action, conn)
        elif command == CAMERA:
            perform_camera(action, conn)
        else:
            print "Corrupt action"
    else:
        print "Not a command, must be of form COMMAND#ACTION"

if __name__ == '__main__':
    while True:
        try:
            conn, addr, s = find_connections()
            try:
                send_receive(conn, addr)
            except KeyboardInterrupt:
                print "Interrupted!"
            finally:
                conn.close()
                s.close()
        except socket_mod.error, e:
            print e
