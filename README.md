# Guardian (AndroidApp3)

MWD3B Android Development – Assignment 5. A location and safety app for a child or pet
carrying this phone. Based on the concepts from the LocationFinder tutorial.

## Screens
- Home – live mini compass, current position, accuracy, address, Set as Home
- Compass – Canvas-drawn dial, heading in degrees, direction name, green arrow toward home
- Safe Zone – continuous location updates, adjustable radius, alert + vibration when leaving

## Sensors and APIs used
- FusedLocationProviderClient (getCurrentLocation and requestLocationUpdates)
- Geocoder (reverse geocoding)
- Accelerometer + magnetic field sensor via SensorManager (rotation matrix / orientation)
- SharedPreferences for the saved home and radius
- NotificationCompat and Vibrator for the safe-zone alert

## Testing
Tested on a Samsung Galaxy (Android 16) and the Pixel 9 emulator.

See AIReflection.md for how AI tools were used in this assignment.
