In the previous [Android Vehicle Application Development and Analysis (1) - Android Automotive Overview and Compilation] (https://source.android.com/docs/automotive/start/avd/android_virtual_device) I learned how to download and compile the Android system for vehicle IVI, everything If it goes well, run the emulator and wait for the startup animation to finish playing. The first APP we can see is the android desktop of the car, and this is the focus of this article - CarLauncher.

This article focuses on analyzing `CarLauncher` in the Android 11 source code. In order to facilitate reading the source code, the source code of `CarLauncher` is organized into a structure that can be imported into Android Studio. The source code address: https://github.com/linux-link/CarLauncher. Since `CarLauncher` depends on the source code, the project cannot be run directly, and the way to introduce jar dependencies is not completely correct, it is only for reading.

The functions and source code analysis in this article are based on *android-11.0.0_r43, *`CarLauncher` source code is located in ***packages/apps/Car/Launcher***

## Launcher and CarLauncher
> Launcher is a desktop launcher in the Android system, and the desktop UI of the Android system is collectively called Launcher. The Launcher is one of the main program components in the Android system. If there is no Launcher in the Android system, [Android desktop] (https://baike.baidu.com/item/Android desktop) cannot be started. When the Launcher fails, the Android system will A prompt window appears "The process com.android.launcher stopped unexpectedly". At this time, the Launcher needs to be restarted.
> From "Baidu Encyclopedia - launcher"

`Launcher` is the desktop of the android system, and it is the first APP with an interface that users come into contact with. It is essentially a system-level APP. Like ordinary APP, its interface is also drawn on the Activity.
Although `Launcher` is also an APP, it involves more technical points than ordinary APPs. As the desktop of the IVI system, CarLauncher needs to display the entrances of all **user-available apps** in the system, and display the apps recently used by users. At the same time, it also needs to support the dynamic display of information inside each app, such as maps and music, on the desktop. Display the map on the desktop and perform simple interactions with it. The workload of map development is huge. It is obviously impossible for `Launcher` to introduce the SDK of the map to develop a map application, so how to dynamically display the map without expanding the workload has become a technical difficulty of `CarLauncher` (the The content involves a lot of knowledge points and miscellaneous, I haven't sorted out `=_=||`, I will introduce it later).

## CarLauncher function analysis
The native `Carlaunher` code is not complicated, it mainly cooperates with SystemUI to complete the following two functions.
* Show the quick home page
     ![](https://upload-images.jianshu.io/upload_images/3146091-837aace4d9bc39ee?imageMogr2/auto-orient/strip|imageView2/2/w/1071/format/webp)
* Display the desktop of all APP entrances
     ![](https://upload-images.jianshu.io/upload_images/3146091-83b1e2578c354e4e?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

It should be noted that only the content in the red box belongs to `CarLauncher`, and the content outside the red box belongs to `SystemUI`. Although `SystemUI` has 6 buttons on the NaviBar below, only clicking **Home** and **App Desktop** will enter **CarLauncher**, and clicking other buttons will enter other apps, so they are not included in this article The analysis scope of the article.

## CarLauncher source code analysis
The source code structure of `CarLauncher` is as follows:
![](https://upload-images.jianshu.io/upload_images/3146091-10208cd2926fc728?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
###Android.dp
The `android.bp` of **CarLauncher** is relatively simple, which defines the source code structure of CarLauncher and the dependent class library. If you don't know anything about `android.bp`, you can take a look at the [Android.bp Getting Started Tutorial](https://www.jianshu.com/p/f23e18933122) to learn the basic grammar, and then look back* The `android.bp` of *CarLauncher** is believed to be much easier to understand.

```
android_app {
     name: "CarLauncher",
     srcs: ["src/**/*.java"],
     resource_dirs: ["res"],
     // Allow the use of the system's hide api
     platform_apis: true,
     required: ["privapp_whitelist_com.android.car.carlauncher"],
     // signature type: platform
     certificate: "platform",
     // Set the apk installation path to priv-app
     privileged: true,
     // Override other types of Launchers
     overrides: [
         "Launcher2",
         "Launcher3",
         "Launcher3QuickStep",
     ],
     optimize: {
         enabled: false,
     },
     dex_preopt: {
         enabled: false,
     },
     // import static library
     static_libs: [
         "androidx-constraintlayout_constraintlayout-solver",
         "androidx-constraintlayout_constraintlayout",
         "androidx.lifecycle_lifecycle-extensions",
         "car-media-common",
         "car-ui-lib",
     ],
     libs: ["android.car"],
     product_variables: {
         pdk: {
             enabled: false,
         },
     },
}

```

In the Android.bp above, we need to pay attention to an attribute `overrides`, which means coverage. `Launcher2`, `Launcher3` and `Launcher3QuickStep` will be replaced by `CarLauncher` when the system is compiled. The first three Launchers are the desktops of the mobile phone system. In the car system, `CarLauncher`, a customized new desktop, will be used to replace the mobile phone system desktop. . Similarly, if we don't want to use the `CarLauncher` that comes with the system, we also need to override `CarLauncher` in `overrides`. We will often use this attribute in the self-developed vehicle Android system, and replace the default APP in the system with various customized APPs, such as system settings and so on.

### AndroidManifest.xml

In the Manifest file, we can see the permissions required by CarLauncher and the entry Activity.

```
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
     package="com.android.car.carlauncher">

     <uses-permission android:name="android.car.permission.ACCESS_CAR_PROJECTION_STATUS" />
     <!-- System permission to host maps activity -->
     <uses-permission android:name="android.permission.ACTIVITY_EMBEDDING" />
     <!-- System permission to send events to hosted maps activity -->
     <uses-permission android:name="android.permission.INJECT_EVENTS" />
     <!-- System permission to use internal system windows -->
     <uses-permission android:name="android.permission.INTERNAL_SYSTEM_WINDOW" />
     <!-- System permissions to bring hosted maps activity to front on main display -->
     <uses-permission android:name="android.permission.MANAGE_ACTIVITY_STACKS" />
     <!-- System permission to query users on device -->
     <uses-permission android:name="android.permission.MANAGE_USERS" />
     <!-- System permission to control media playback of the active session -->
     <uses-permission android:name="android.permission.MEDIA_CONTENT_CONTROL" />
     <!-- System permission to get app usage data -->
     <uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" />
     <!-- System permission to query all installed packages -->
     <uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />
     <uses-permission android:name="android.permission.REORDER_TASKS" />
<!-- To connect to media browser services in other apps, media browser clients
         that target Android 11 need to add the following in their manifest -->
     <queries>
         <intent>
             <action android:name="android.media.browse.MediaBrowserService" />
         </intent>
     </queries>
     <application
         android:icon="@drawable/ic_launcher_home"
         android:label="@string/app_title"
         android:supportsRtl="true"
         android:theme="@style/Theme.Launcher">
         <activity
             android:name=".CarLauncher"
             android:clearTaskOnLaunch="true"
             android:configChanges="uiMode|mcc|mnc"
             android:launchMode="singleTask"
             android:resumeWhilePausing="true"
             android:stateNotNeeded="true"
             android:windowSoftInputMode="adjustPan">
             <meta-data
                 android:name="distractionOptimized"
                 android:value="true" />
             <intent-filter>
                 <action android:name="android.intent.action.MAIN" />

                 <category android:name="android.intent.category.HOME" />
                 <category android:name="android.intent.category.DEFAULT" />
             </intent-filter>
         </activity>
         <activity
             android:name=".AppGridActivity"
             android:exported="true"
             android:launchMode="singleInstance"
             android:theme="@style/Theme.Launcher.AppGridActivity">
             <meta-data
                 android:name="distractionOptimized"
                 android:value="true" />
         </activity>
     </application>
</manifest>

```

Regarding the Manifest, let's focus on understanding some of the less commonly used tags.

#### **<queries/>**
`<queries/>` was introduced on Android 11 to tighten app permissions. Used to specify the set of other applications that the current application wants to interact with, these other applications can pass package, intent, provider. Example:

```
<queries>
     <package android:name="string" />
     <intent>
         ...
     </intent>
     <provider android:authorities="list" />
     ...
</queries>

```

For more information, please refer to: [Android Developers | <queries>](https://developer.android.google.cn/guide/topics/manifest/queries-element?hl=cn)

#### **android:clearTaskOnLaunch = "true"**

The root Activity is started every time it starts, and other activities are cleaned up.

#### **android:configChanges="uiMode|mcc|mnc"**

No more nonsense about android:configChanges, just upload a relatively complete form for reference.

| **VALUE** | **DESCRIPTION**|
|----|-----|
|mcc| The country code of the International Mobile Subscriber Identity has changed, the sim has been detected, update mcc MCC is the country code of the mobile subscriber|
|mnc|The mobile network number of the International Mobile Subscriber Identity Code has changed, and the sim has been detected, so update mnc
|locale|The locale of the user has changed. For example: when the user switches the language, the switched language will be displayed |
|touchscreen| The touchscreen has changed|
|keyboard|The keyboard has changed. Example: User intervened on external keyboard |
|keyboardHidden|Availability of the keyboard has changed|
|navigation|Navigation has changed|
|screenLayout|The display of the screen has changed. Example: Different displays are activated |
|fontScale|The font scale has changed. Example: A different global font is selected |
|uiMode|The user's mode has changed|
|orientation|The screen orientation has changed. For example: horizontal and vertical screen switching |
|smallestScreenSize|The physical size of the screen has changed. Example: Connecting to an external screen|

#### **android:resumeWhilePausing = "true"**
When the previous Activity is still executing the onPause() method (that is, during the pause process, it has not been completely paused), allow the Activity to display (at this time, the Activity cannot apply for any other additional resources, such as the camera)

#### **android:stateNotNeeded="true"**
This property is false by default. If it is set to true, the onSaveInstanceState method will not be called when the Activity is restarted, and the Bundle parameter in the onCreate() method will always be null. In some special occasions, because the user presses the Home button, when this property is set to true, it can ensure that the original state reference does not need to be saved, saving space resources to a certain extent.

#### **android:name="distractionOptimized"**
Set whether the current Activity is active and whether it will cause the driver to be distracted. In foreign countries, the car Android application needs to abide by the official "Driver Distraction Guidelines" formulated by Android. This rule is rarely used in China. For details, please refer to [Driver Distraction Guidelines | Android Open Source Project](https://source.android.google.cn/devices/automotive/driver_distraction/guidelines)

###AppGridActivity
`AppGridActivity` is used to display all APPs in the system and provide an entry for users.
![image](https://upload-images.jianshu.io/upload_images/3146091-7ddcfb41a0579782?imageMogr2/auto-orient/strip|imageView2/2/w/1069/format/webp)

As application developers, we need to pay attention to how the following two functions are implemented.
* Display all APPs in the system, and filter out some APPs that do not need to be displayed on the desktop (for example: Service in the background)
* Show recently used apps

#### Display all APPs in the system (All App)
The methods used to filter all APPs in `CarLauncher` are concentrated in `AppLauncherUtils`
```
/**
  * Gets all the components we want to see in the launcher in unsorted order, including launcher activities and media services.
  *
  * @param blackList list of applications (package names) to hide (may be empty)
  * @param customMediaComponents A list (possibly empty) of media components (component names) that should not be displayed in the Launcher, since their application's Launcher activity will be displayed
  * @param appTypes application types to display (eg: all or media sources only)
  * @param openMediaCenter Whether the launcher should navigate to the media center when the user selects a media source.
  * @param launcherApps {@link LauncherApps} System Services
  * @param carPackageManager {@link CarPackageManager} system service
  * @param packageManager {@link PackageManager} system service
  * @return a new {@link LauncherAppsInfo}
  */
@NonNull
static LauncherAppsInfo getLauncherApps(
         @NonNull Set<String> blackList,
         @NonNull Set<String> customMediaComponents,
         @AppTypes int appTypes,
         boolean openMediaCenter,
         LauncherApps launcherApps,
         CarPackageManager carPackageManager,
         Package Manager package Manager,
         CarMediaManager carMediaManager) {

     if (launcherApps == null || carPackageManager == null || packageManager == null
             || carMediaManager == null) {
         return EMPTY_APPS_INFO;
     }
// Retrieve all services matching the given intent
     List<ResolveInfo> mediaServices = packageManager.queryIntentServices(
             new Intent(MediaBrowserService. SERVICE_INTERFACE),
             PackageManager.GET_RESOLVED_FILTER);
     // Retrieve the list of Activities with the specified packageName
     List<LauncherActivityInfo> availableActivities =
             launcherApps.getActivityList(null, Process.myUserHandle());

     Map<ComponentName, AppMetaData> launchablesMap = new HashMap<>(
             mediaServices. size() + availableActivities. size());
     Map<ComponentName, ResolveInfo> mediaServicesMap = new HashMap<>(mediaServices. size());

     // Process media services
     if ((appTypes & APP_TYPE_MEDIA_SERVICES) != 0) {
         for (ResolveInfo info : mediaServices) {
             String packageName = info.serviceInfo.packageName;
             String className = info.serviceInfo.name;
             ComponentName componentName = new ComponentName(packageName, className);
             mediaServicesMap. put(componentName, info);
             if (shouldAddToLaunchables(componentName, blackList, customMediaComponents,
                     appTypes, APP_TYPE_MEDIA_SERVICES)) {
                 final boolean isDistractionOptimized = true;

                 Intent intent = new Intent(Car.CAR_INTENT_ACTION_MEDIA_TEMPLATE);
                 intent.putExtra(Car.CAR_EXTRA_MEDIA_COMPONENT, componentName.flattenToString());

                 AppMetaData appMetaData = new AppMetaData(
                     info.serviceInfo.loadLabel(packageManager),
                     componentName,
                     info.serviceInfo.loadIcon(packageManager),
                     isDistractionOptimized,
                     context -> {
                         if (openMediaCenter) {
                             AppLauncherUtils.launchApp(context, intent);
                         } else {
                             selectMediaSourceAndFinish(context, componentName, carMediaManager);
                         }
                     },
                     context -> {
                         // Return all MainActivity intents with Intent.CATEGORY_INFO and Intent.CATEGORY_LAUNCHER in the system
                         Intent packageLaunchIntent =
                                 packageManager.getLaunchIntentForPackage(packageName);
                         AppLauncherUtils.launchApp(context,
                                 packageLaunchIntent != null ? packageLaunchIntent : intent);
                     });
                 launchablesMap.put(componentName, appMetaData);
             }
         }
     }

     // Process activities
     if ((appTypes & APP_TYPE_LAUNCHABLES) != 0) {
         for (LauncherActivityInfo info : availableActivities) {
             ComponentName componentName = info. getComponentName();
             String packageName = componentName. getPackageName();
             if (shouldAddToLaunchables(componentName, blackList, customMediaComponents,
                     appTypes, APP_TYPE_LAUNCHABLES)) {
                 boolean isDistractionOptimized =
                     isActivityDistractionOptimized(carPackageManager, packageName,
                         info.getName());

                 Intent intent = new Intent(Intent. ACTION_MAIN)
                     .setComponent(componentName)
                     .addCategory(Intent.CATEGORY_LAUNCHER)
                     .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                 // Get the name of the app, and the icon of the app
                 AppMetaData appMetaData = new AppMetaData(
                     info. getLabel(),
                     componentName,
                     info.getBadgedIcon(0),
                     isDistractionOptimized,
                     context -> AppLauncherUtils.launchApp(context, intent),
                     null);
                 launchablesMap.put(componentName, appMetaData);
             }
         }
     }

     return new LauncherAppsInfo(launchablesMap, mediaServicesMap);
}
```
The above code shows us that the **List<LauncherActivityInfo>** returned by **LauncherApps.getActivityList()** contains all the Activity information configured with `Intent#ACTION_MAIN` and `Intent#CATEGORY_LAUNCHER` in the system.
**String LauncherActivityInfogetLabel()** : Get the name of the app
**String LauncherActivityInfo.getComponentName()** : Get the Mainactivity information of the app
**Drawable LauncherActivityInfo.getBadgedIcon(0)** : Get the icon of the App
Finally, when the user clicks the icon, although the app is also started through startActivity, ActivityOptions allows us to determine which screen the target app will start on, which is very important for the current multi-screen system in the car.

```
static void launchApp(Context context, Intent intent) {
     ActivityOptions options = ActivityOptions. makeBasic();
     // Start the Activity of the target App on the current screen
     options.setLaunchDisplayId(context.getDisplayId());
     context.startActivity(intent, options.toBundle());
}
```

#### Display recently used APP (Recent APP)
`UsageStatusManager` is provided in the Android system to provide access to device usage history and statistics. `UsageStatusManager` does not need to add additional permissions when using the following methods.
`android.provider.Settings#ACTION_USAGE_ACCESS_SETTINGS`
`getAppStandbyBucket()`
`queryEventsForSelf(long,long)`
But other methods require `android.permission.PACKAGE_USAGE_STATS` permission.
```
/**
  * Note that in order to get usage statistics from the last boot, the device must go through a clean shutdown process.
  */

private List<AppMetaData> getMostRecentApps(LauncherAppsInfo appsInfo) {
    ArrayList<AppMetaData> apps = new ArrayList<>();
    if (appsInfo.isEmpty()) {
        return apps;
    }

    // Get the usage statistics from 1 year ago, and return the following items:
     // "During 2017 App A is last used at 2017/12/15 18:03"
     // "During 2017 App B was last used at 2017/6/15 10:00"
     // "During 2018 App A is last used at 2018/1/1 15:12"
     List<UsageStats> stats =
             mUsageStatsManager.queryUsageStats(
                     UsageStatsManager.INTERVAL_YEARLY,
                     System.currentTimeMillis() - DateUtils.YEAR_IN_MILLIS,
                     System. currentTimeMillis());

     if (stats == null || stats. size() == 0) {
         return apps; // empty list
     }

     stats.sort(new LastTimeUsedComparator());

     int currentIndex = 0;
     int itemsAdded = 0;
     int statsSize = stats. size();
     int itemCount = Math.min(mColumnNumber, statsSize);
     while (itemsAdded < itemCount && currentIndex < statsSize) {
         UsageStats usageStats = stats. get(currentIndex);
         String packageName = usageStats.mPackageName;
         currentIndex++;

         // does not include self
         if (packageName. equals(getPackageName())) {
             continue;
         }

         // TODO(b/136222320): Each package can get UsageStats, but a package may contain multiple media services. We need to find a way to get usage statistics for each service.
         ComponentName componentName = AppLauncherUtils.getMediaSource(mPackageManager,
                 packageName);
         // Exempt background and enabler checks for media services
         if (!appsInfo.isMediaService(componentName)) {
             // don't include apps that only run in the background
             if (usageStats. getTotalTimeInForeground() == 0) {
                 continue;
             }
             // don't include apps that don't support launching from the launcher
             Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
             if (intent == null || !intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
                 continue;
             }
         }

         AppMetaData app = appsInfo.getAppMetaData(componentName);
         // prevent duplicate entries
         // e.g. app is used at 2017/12/31 23:59, and 2018/01/01 00:00
         if (app != null && !apps. contains(app)) {
             apps. add(app);
             itemsAdded++;
         }
     }
     return apps;
}
```
### References
[Android Developers | <queries>](https://developer.android.google.cn/guide/topics/manifest/queries-element?hl=cn)
