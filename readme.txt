This is an OpenHAB binding for the Plum LightPad. It has been developed and tested for OpenHAB 1.8
You will need to use it in combination with my plum-probe.py script to retrieve certain tokens and IDs: https://github.com/mikenemat/plum-probe
This binding interacts with the LightPads locally, and thus may be broken by firmware updates or configuration changes. 

Binding JAR:
target/org.openhab.binding.plum-1.8.2-SNAPSHOT.jar

Binding DEB package:
In the target dir...no clue if it works

Changelog:
Mar 21 6PM - Fixed an issue with dimming
Aug 1 2PM - Added support for motion sensor items (Contact) for #motion items. 

Currently working:
-Plum LightPads can be controlled via OpenHAB as switches (Local HTTP)
-Plum LightPads can be controlled via OpenHAB as dimmers (Local HTTP)
-Plum LightPads receive status / event updates in OpenHAB in real-time based on physical interaction (Local TCP Stream)
-Plum LightPads receive status / event updates on OpenHAB startup (polling) starting at 60s and repeating every 60s forward. (Local HTTP)
-Plum LightPads report the power consumption of the load (in watts). Use the same llid and IP address but with the Number item type and #powermeter feature as per example below
-Plum Lightpads report PIR/Motion sensor events. These events will set an OpenHab CONTACT item type to OPEN for 5 seconds, and automatically close it 5 seconds later. Make sure to use the Item Type Contact and the plum config type #motion as per examples below.

General issues
-Non-real-time (polled) values and statuses are updated for the first time at the first refresh (60s default) instead of plugin startup.
	- A side effect of this is that Dimmers when configured as Sliders in the site map cannot be controlled until the first refresh.
-No test cases and minimal error handling. Make sure your configuration is perfect and keep an eye on the logs for any network issues.

Configuration
NOTE: You may use the IP address of any Plum LightPad in the logical load if you have an n-way (more than 1 Light Pad) configuration.
NOTE: The HTTPS port of the Plum LightPads is hardcoded to 8443. Please let me know if this becomes an issue.

-openhab.cfg:
plum:refresh=60000
plum:house_token=PLUM_HOUSE_TOKEN_FROM_PLUM_PROBE_PYTHON_SCRIPT_HERE

-plum.items:
Dimmer dimmerName "dimmerLabel [%d]" {plum="IP_ADDRESS_FROM_PLUM_PROBE_PYTHON_SCRIPT:LOGICAL_LOAD_ID_FROM_PLUM_PROBE_PYTHON_SCRIPT#dimmer"}
Switch switchName "switchLabel" {plum="IP_ADDRESS_FROM_PLUM_PROBE_PYTHON_SCRIPT:LOGICAL_LOAD_ID_FROM_PLUM_PROBE_PYTHON_SCRIPT#switch"}
Number powerMeterName "powerMeterLabel [%d W]" {plum="IP_ADDRESS_FROM_PLUM_PROBE_PYTHON_SCRIPT:LOGICAL_LOAD_ID_FROM_PLUM_PROBE_PYTHON_SCRIPT#powermeter"}
Contact motionName "motionLabel" {plum="IP_ADDRESS_FROM_PLUM_PROBE_PYTHON_SCRIPT:LOGICAL_LOAD_ID_FROM_PLUM_PROBE_PYTHON_SCRIPT#motion"}

Real item example:
Switch foyerLights "Foyer Pot Lights" {plum="192.168.1.81:fbb0bbxx-0747-4c14-xxxx-e4db2a1xxde4#switch"}

-plum.sitemap 

Switch item=switchName
Slider item=dimmerName
Text item=powerMeterName
Switch item=motionName
***Note*** You may also use Switch types for dimmers instead of Slider. You may also use Text type for motion sensors instead of Switch

-plum.rules example:

rule "officeLightOn"
    when   
            Item officeMotion changed from CLOSED to OPEN
    then   
            sendCommand(officeLights, ON)
    end
