# Actron Connect Integration

This is an app and associated device drivers written to allow Hubitat Elevation to integrate with an Actron Air Conditioner which uses the Actron Connect Module.

As you can see, not all functionality has been implemented, as I was only looking for basic on/off control of the system and zones from Hubitat along with the ability to read/graph the individual zone temperatures so I could then control everything from Rule Machine and graph the zone temps using HubiGraphs.

As I get more time, I may look to implement some of the other capabilities around setting temperatures and fan control etc.

## Current Versions

| File                      | Version | Description                                                                        |
| :------------------------ | :------ | :--------------------------------------------------------------------------------- |
| ACSM-app-parent.groovy    | 1.0.0   | Hubitat parent app used to install the Actron Connect Service Manager application. |
| ACSM-app-child.groovy     | 1.0.2   | Hubitat child app running the Actron Connect Service Manager.                      |
| ACSM-driver-aircon.groovy | 1.0.9   | Hubitat driver for interacting with an Actron air conditioner via Actron Connect.  |
| ACSM-driver-zone.groovy   | 1.0.0   | Hubitat driver for interacting with a Actron zone via Actron Connect.              |

---

## Features

The following features are currently available:

- Able to turn main unit on/off
- Able to turn individual zones on/off
- Read various parameters of main air conditioner unit that are made available by the Actron Connect cloud service
- Able to read individual temperatures from all zones
- Added ability to set Thermostat Modes ( `Auto`, `Heat`, `Cool`, `Fan Only`, `Off`). Selecting any option except `Off` will switch to the selected mode AND turn the unit on.
- Added ability to set Thermostat Fan Modes ( `Low`, `Med`, `High`). Setting a fan mode via Hubitat will disable ESP mode. See known issues below.

## Known Issues

- After upgrading from earlier versions (either manually or via HPM), some errors in the logs may appear. The workaround to these errors is to trigger the `updated` method, which can be done by enabling debug logs. This also forces a reconnect of the websocket connection. If the errors still persist, please log an issue.
- Sometimes when turning zones on/off via a button/dashboard, the switches toggle on/off. This is caused by state updates being received through the websocket connection that don't represent the state of the recently sent commands. To `delay` the processing of any received websocket updates for a period of time after a command has been processed (so hopefully the system converges on the desired state and only the last websocket command is processed), modify the preferences of the main air conditioner and set the `Set Update Delay Time` setting to a higher time. The default is 2 seconds as a base starting point. Increase as necessary.
- Selecting any `Fan Mode` disables ESP and/or Continuous modes on the air conditioner unit. This is the same behavior as the Actron Connect application and cannot be avoided. There is no workaround as there is no API exposed to enable ESP mode or to choose continuous fan mode. To re-enable either of these modes, you must change the settings on your wall unit.
- When using the thermostat dashboard tile, the up and down arrow for temperature do not work. This is the same for the device commands `setCoolingSetpoint` and `setHeatingSetpoint`.
- When using the thermostat dashboard tile, it displays unknown between the two temperature controls (Once I figure out where this actually reads from, I will fix it).

## Installation

> Before proceeding, please make sure you have already created and Actron Connect account and are able to log into the app on your phone or website.

### HPM

- Choose `install`
- Choose `Browse by tags`
- Select the tag `Temperature & Humidity`
- Select `Actron Connect Integration by Dale Coghlan`
- Select the option to configure the application once installed.
- Click `Done` to install the parent app

### Manual

- Install the ACSM (Actron Connect Service Manager) parent and child apps
- Install the ACSM drivers for the aircon and zone
- In the Hubitat UI, click the button to `Add User App` and select `Actron Connect Integration`
- Click `Done` to install the parent app

### Setup Actron Connect Service Manager

- Open the app `Actron Connect Integration` and select `Add a new Actron 'Connect Service Manager' child`
- Enter your credentials used to sign into the the Actron Connect App
- Leave the `Poll Every X Minutes` set to Never (this is not currently implemented yet)
- Click `Next`
- Select your air conditioner unit from the dropdown
- Click `Done`

A new parent device will be created for the main air conditioner unit, and child devices for each discovered zone will be created.
