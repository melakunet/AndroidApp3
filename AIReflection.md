**1. How did I use AI in this assignment?**

**A. Overall**

I gave the instructions and the idea about tracking teenagers from 11 up to 17 years old to the Claude AI agent. I told it to give me steps with prompts. The Claude agent and I had a back-and-forth chat about how it should be built based on the assignment instructions. Then we decided on the steps and prepared prompts for the home page, for the compass animation, and for the live tracking.

Next, I gave each prompt one by one to Gemini AI. Gemini created the code, and I pasted it into the corresponding files inside the Android Studio `AndridApp3` folder. Then I checked for red underlines, tested them back and forth with Gemini to fix bugs, and tested everything on the emulator and on my Samsung S26 phone.

---

**B. Examples of my process**

At first, the animation was not working well and was not showing properly. I told Claude about the issue, and Claude gave me a prompt. I then gave that prompt to the Gemini agent inside Android Studio to create the animation on top of the page. Gemini created a nice looking and rotating animation compass, which fixed the problem.

Next, I tested how it worked. It rotated based on my location. I set my home address as my home, and the app tracked my exact location. After setting the home address, the safe zone feature activated. When I clicked the "Start Tracking" button, it showed that I was at home, and when I moved around, it showed me how far away I was.

AI helped me here to create steps, guide me on what files to make, and create the code. I saved the code in the files, tested it, fixed bugs with AI, and tested again. Gemini also built the animated compass without using any PNG image. It only used a ChatGPT-generated image from our class example as a reference to code the working animated compass. That is how I used AI.

---

**C. Concepts not covered in class**

* **Custom View drawn with Canvas instead of a PNG (`CompassView.kt`):** Learned how `onDraw`, `canvas.rotate`, `Path`, and `Paint` work together to draw the needle and dial directly in code.
* **`ValueAnimator` with the 359°→0° wrap (`CompassView.kt`):** Learned how to calculate the shortest path rotation so the compass needle does not spin all the way around when crossing north.
* **`toRawBits` / `fromBits` to store a `Double` in `SharedPreferences` (`HomeStore.kt`):** `SharedPreferences` does not have a `putDouble` method, so I learned to store latitude and longitude coordinates as bits.
* **Continuous location updates with `LocationCallback` (`SafeZoneActivity.kt`):** Used `LocationRequest.Builder` and `requestLocationUpdates` to track the user's distance from the safe zone in real time.

2. How did you understand, verify, and adapt the code?

a. How I verified the AI code was correct:

Tested every step on my physical Samsung phone, not just the emulator, to confirm the address matched my real street.

Saved my home address, force-closed the app, and reopened it to verify SharedPreferences actually kept the data.

Tested the Safe Zone by setting a radius, tapping "Start Tracking," and walking around to watch the live distance and the INSIDE/OUTSIDE status change.

b. Changes I made and why:

Moved the sensor logic into HeadingSensor.kt and the distance math into GeoUtils.kt so the home screen and compass screen share the same code instead of repeating it.

Replaced the class example's null with CancellationTokenSource for cleaner location requests.

3. What did you learn or get better at?

a. Concept I leveled up on:

I understood how the accelerometer and magnetometer work together to calculate the device heading, and why CompassView must rotate the needle and dial together inside onDraw so North stays accurate.

b. What went well:

Using small, focused prompts with clear rules, testing on a real phone, and making regular git commits throughout the process