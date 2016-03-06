This is an extremely primitive OpenHAB binding. 
You will need to use it in combination with my plum-probe.py script to retrieve certain tokens and IDs: https://github.com/mikenemat/plum-probe

Binding JAR:
target/org.openhab.binding.plum-1.8.2-SNAPSHOT.jar

Binding DEB package:
In the target dir...no clue if it works

Currently working:
-Plum LightPads can be controlled via OpenHAB as switches
-Plum LightPads receive status / event updates in OpenHAB in real-time based on physical interaction
-Plum LightPads receive status / event updates on OpenHAB startup (polling) starting at 60s and repeating every 60s forward.

Soon to be working:
-Plum LightPads can be dimmed / treated as dimmers instead of switches

Future:
- Power consumption events/updates from Plum load power consumption monitoring
- Motion sensor events/updates from Plum PIR sensor

General issues
-Poorly tested, lots of code duplication. Needs work.
-Defining a Plum Lightpad as a Dimmer does nothing. Dimmer items are still treated as switches. Will be fixed ASAP.

Configuration
NOTE: You may use the IP address of any Plum LightPad in the logical load if you have an n-way (more than 1 Light Pad) configuration.

-openhab.cfg:
plum:refresh=60000
plum:house_token=PLUM_HOUSE_TOKEN_FROM_PLUM_PROBE_PYTHON_SCRIPT_HERE

-*.items:
Dimmer lightName "LightLabel" {plum="IP_ADDRESS_FROM_PLUM_PROBE_PYTHON_SCRIPT:LOGICAL_LOAD_ID_FROM_PLUM_PROBE_PYTHON_SCRIPT#dimmer"}
Switch lightName "LightLabel" {plum="IP_ADDRESS_FROM_PLUM_PROBE_PYTHON_SCRIPT:IP_ADDRESS_FROM_PLUM_PROBE_PYTHON_SCRIPT:LOGICAL_LOAD_ID_FROM_PLUM_PROBE_PYTHON_SCRIPT#switch"}
