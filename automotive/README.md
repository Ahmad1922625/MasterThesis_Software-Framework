##README##

1) System Flow Overview
Application Startup: MainActivity

The app initializes the Car API (Car.createCar()).
It establishes a connection to CarPropertyManager via IntegrationClass.
Calls integrationClass.initModules(), which:
- Registers the door_control module.
- Calls registerDoorPropertyCallback(), so door lock changes trigger real-time updates. 

Listening for Door Lock Changes (registerDoorPropertyCallback)
VehiclePropertyIds.DOOR_LOCK is used to subscribe to updates.
The system automatically detects door lock state changes via the callback.
If a door lock status update is received, the new value is logged and processed.

Getting Door State (getDoorState()):
Fetches door lock (VehiclePropertyIds.DOOR_LOCK) and door position (VehiclePropertyIds.DOOR_MOVE).
Returns an OutputObject containing:
- lockStatus (locked/unlocked)
- doorPosition (open/closed)

Unlocking a Door (unlockDoor()):
Uses carPropertyManager.setBooleanProperty(VehiclePropertyIds.DOOR_LOCK, areaId, false).
Logs success or failure.

Opening a Door (openDoor())
Uses carPropertyManager.setIntProperty(VehiclePropertyIds.DOOR_MOVE, areaId, 1).
Moves the door to the open position.

Executing a Module (executeModule())
Runs when triggered manually or via a condition (e.g., match found in another module).
Calls unlockDoor() if the input OutputObject result is "MatchFound".
Otherwise, returns "AccessDenied".
Module Completion (notifyModuleCompleted())

Logs the result of module execution.
If "MatchFound", it automatically triggers door_control.

Application Shutdown (onDestroy())
Disconnects Car service.
Unregisters door property callbacks to avoid memory leaks.

2) Installation steps: Check User Manual in the institute's database.
Some additional info: The AnimationManager class should not be relevant to this project going forward, it can safely be removed.
The facial recognition modules used here can also be replaced. They only do image recognition in practice. I would start the facial recognition process from scratch for a cleaner approach.
The door control modules can be used as a basis to write other high-level modules from Android properties.
Make sure to add relevant permissions to the Manifest for any additional functionality.
Vendor properties have one unified permission.

3) Some relevant commands:
Emulator boot (Replace path and emulator name): You need to do this to boot the emulator in writable format, otherwise system remount fails.
    cd C:\Users\UserName\AppData\Local\Android\Sdk\emulator
    ./emulator -list-avds
    ./emulator -avd Automotive_1408p_landscape_API_34-ext9 -writable-system

System app download:
    adb root
    adb remount
    adb shell su 0 mount -o rw,remount /system

To remove installed app: In case you have a new app version, you need to remove the old one to avoid conflicts.
    adb shell rm -rf /system/priv-app/MyCarApp

To install app as a system app:
    adb shell mkdir /system/priv-app/MyCarApp
    adb push C:\Users\UserName\AndroidStudioProjects\MyCarApp2\automotive\build\outputs\apk\debug\automotive-debug.apk /system/priv-app/MyCarApp/MyCarApp.apk
    adb shell chmod 644 /system/priv-app/MyCarApp/MyCarApp.apk
    adb shell chown system:system /system/priv-app/MyCarApp/MyCarApp.apk

    adb pull /etc/permissions/privapp-permissions-platform.xml
    notepad privapp-permissions-platform.xml
    
    <privapp-permissions package="com.MyCarApp">
        <permission name="android.car.permission.CONTROL_CAR_DOORS"/>
        <permission name="android.car.permission.CONTROL_CAR_FEATURES"/>
    </privapp-permissions>
    
    adb push privapp-permissions-platform.xml /etc/permissions/
    adb reboot

To check app installation and path:
    adb shell pm list packages | findstr "MyCarApp"   
    adb shell pm path com.MyCarApp
    adb shell pm list packages -s | findstr "MyCarApp"

To check app permissions:
    adb shell dumpsys package com.MyCarApp | findstr "android.car.permission"


To uninstall user app:
    adb shell pm uninstall com.MyCarApp

To verify the actual directory, run:
    adb shell ls -l /system/priv-app/ | findstr "MyCarApp"


All findstr commands are Windows-specific. When using Linux (With the NXP board), replace all findstr with grep.
Many more commands to check functionality and installation can be found online. 
Save yourself time and run the ones you have here by ChatGPT, you'll get a better explanation and more commands you can use.
Espeically relevant would be the direct system dump commands for each Android property (In the user manual).

To set ADB over Ethernet, you need to follow these steps:
adb root
adb shell
ifconfig eth0 192.168.1.100 netmask 255.255.255.0 up
ping 192.168.1.100 (Cntrl C if packages are being transmitted successfully)
adb kill-server
adb start-server
adb connect 192.168.1.100:5555

Unfortunately, my exact command notes were wiped out when the PC had to be formatted, so this is my attempt to put everything together.
It should work, but if it doesn't, there is probably just one command missing somewhere.

ADB over Ethernet is important because the NXP board only has 1 type C port, which you will need for the USB camera. 
Using a HUB did not work for me.
If you arent using a camera yet, you dont need ADB over Ethernet.

Best of luck!