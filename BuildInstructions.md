# Build instructions #

To build the project and deploy the included Android app, you need install the following components:
  * [Java JDK](http://java.sun.com)
  * [Android SDK](http://developer.android.com/sdk/index.html)
  * [maven 2](http://maven.apache.org/)

The project make use of maven-android-plugin, so add to your maven settings.xml file:

```
<pluginGroups>
   <pluginGroup>
     com.jayway.maven.plugins.android.generation2
   </pluginGroup>
</pluginGroups>        
```

Once you have those in place, check out the project:
```
$ svn checkout http://androidsipservice.googlecode.com/svn/trunk/ androidsipservice-read-only
$ cd androidsipservice-read-only
```

Then define the environment variable **ANDROID\_HOME** to point to your Android SDK base directory:
```
$ export ANDROID_HOME=<your_sdk_dir>
```

You should now be able to build the project using maven:
```
$ mvn install
```

This builds the project locally. If you also want to deploy the SipService app to your running emulator, execute the following commands:

```
$ cd app-service
$ mvn android:deploy -Dandroid.device=emulator
```