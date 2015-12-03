<h1>Project Overview</h1>

In this project, you will build a wearable watch face for Sunshine to run on an Android Wear device.

<h2>Why this Project?</h2>

Android Wear is an exciting way to integrate your app more directly into users’ lives. As a new developer, it will be important for you to understand how to perform this integration. This project gives you an opportunity to design a companion app for Sunshine, tying it to a watch face in order to enrich the experience.

<h2>What Will I Learn?</h2>

Through this project, you will:

* Understand the fundamentals of Android Wear.
* Design for multiple watch form factors.
* Communicate between a mobile device and a wearable device.

<h2>What did I do?</h2>

* Added a wear module to the Sunshine project for the custom watch face.
* Added round and square watch faces.
* Added MessageApi to communicate between the wearable and connected device. From the connected device, weather information is sent to the wearable based on the SunshineSyncAdapter. When the watch face is addded to the wearable, a message is sent to the connected device to retrieve the current temperature and weather.
* On the watch face, the display consists of the time, current weather icon, high and low temperatures and tomorrow's weather icon.
* The watch face display changes based on the current weather conditions - rainy, cloudy and sunny weather each has a unique color set that affects the AM/PM indicator, weekday, month/day and the background color for the bottom portion of the watch face.