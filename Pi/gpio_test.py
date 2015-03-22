import RPi.GPIO as GPIO
from time import sleep
output_gpio_pin = 23
GPIO.setmode(GPIO.BCM)
GPIO.setup(output_gpio_pin, GPIO.OUT)
while True:
	print "Low"
	GPIO.output(output_gpio_pin, False)
	sleep(2)
	print "High"
	GPIO.output(output_gpio_pin, True)
	sleep(2)
