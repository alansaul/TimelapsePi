for OSX we must kill the PTP daemon

    killall PTPCamera

Some testing python code:

    import gphoto2 as gp
    context = gp.Context()
    camera = gp.Camera()
    camera.init(context)
    print camera.get_summary(context)
