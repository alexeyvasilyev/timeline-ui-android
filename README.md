Android timeline UI widget written in Java
=============================================

![Screenshot](docs/images/timeline-android-screenshot1.png?raw=true "Screenshot")

## Features:

- Android min API 21.
- Supports single timeline.
- Supports on demand loading.
- Shows 1 min, 5 min, 15 min, 30 min, 1 hour, 6 hours, 12 hours, 1 day, 7 days, 30 days ranges.

## See also:
- [Web timeline UI widget written in JavaScript](https://github.com/alexeyvasilyev/timeline-ui-web)

## Compile

To use this library in your project with gradle add this to your build.gradle:

```gradle
allprojects {
  repositories {
    maven { url 'https://jitpack.io' }
  }
}
dependencies {
  implementation 'com.github.alexeyvasilyev:timeline-ui-android:1.4.4'
}
```
