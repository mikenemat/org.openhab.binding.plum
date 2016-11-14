This is an OpenHAB binding for the Plum LightPad. It has been developed and tested for OpenHAB 1.8, as well as OpenHAB 2.0 with the 1.x compatibility layer.
You will need to use it in combination with my plum-probe.py script to retrieve certain tokens and IDs: https://github.com/mikenemat/plum-probe
This binding interacts with the LightPads locally over HTTP, and thus may be broken by firmware updates, IP address reallocation, or configuration changes. It is strongly recommended that you make static DHCP allocations for all of your lightpads to avoid IP address changes.

Binding JAR:
FOR OPENHAB 1.8.X: target/org.openhab.binding.plum-1.8.2-OPENHAB_1_8_X.jar
FOR OPENHAB 2.0: target/org.openhab.binding.plum-1.9.0-OPENHAB_2_0.jar

Binding DEB package:
Currently broken, use the JAR file

Changelog:
Mar 21/2016 6PM - Fixed an issue with dimming
Aug 1/2016 2PM - Added support for motion sensor items (Contact) for #motion items. 
Aug 1/2016 3PM - Added extra logging around motion sensors and added a 5 second cool-down period for motion events.
Aug 1/2016 3:10PM - Fixed a bug where motion sensors wouldn't work on 1st try.
Nov 13/2016 9:23PM - OpenHAB 2.0 Compatibility: Converted from Apache Commons HTTP to Jetty HttpClient
Nov 14/2016 12:10AM - Added various HTTP timeout parameters to improve reliability
Nov 14/2016 1:11AM - Create separate builds for OpenHAB 1.8X and 2.0 to address outstanding issues.


KNOWN ISSUES:
- Phantom motion events. There is a numeric value attached to pirSignal events which I was ignoring. This proved to be a bad idea. These values likely indicate some sort of quality/threshold. I will log and audit these values to determine what numeric value indicates a "true" pirSignal event. I had assumed that pirSignal events are fired only when the motion meets the same threshold used to light up the LightPad. I'm not so sure about that any more. I think a superset of motion events are broadcast and not all of them meet the threshold to be considered valid. Please check back in a few days - I'm confident I can fix this issue in the near future.

Currently working:
-Plum LightPads can be controlled via OpenHAB as switches 
-Plum LightPads can be controlled via OpenHAB as dimmers
-Plum LightPads receive status / event updates in OpenHAB in real-time based on physical interaction from the streaming API service running on TCP port 2708 on the LightPads.
-Plum LightPads receive status / event updates on OpenHAB startup starting at 60s and repeating every 60s forward
-Plum LightPads report the power consumption of the load (in watts). Use the same llid and IP address but with the Number item type and #powermeter feature as per example below
-Plum Lightpads report PIR/Motion sensor events. These events will set an OpenHab CONTACT item type to OPEN for 5 seconds, and automatically close it 5 seconds later. Make sure to use the Contact item type and the #motion configuration feature as per examples below.

General issues
-Dimmers when configured as Sliders in the site map cannot be controlled until the first refresh (60s after OpenHAB start)
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

-plum.rules example ***NOTE*** If you configured your lights as a Switch instead of a Dimmer, you should use == ON/OFF instead of >0/==0:

rule "officeLightOn"
when
        Item officeMotion changed from CLOSED to OPEN
then
        if (officeLights.state > 0) {
                sendCommand(officeLights, OFF)
        } else if (officeLights.state == 0){
                sendCommand(officeLights, ON)
        }
end
