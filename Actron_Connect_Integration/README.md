# Actron Connect Integration

This is an app and associated device drivers written to allow Hubitat Elevation to integrate with an Actron Air Conditioner which uses the Actron Connect Module.

The following features are currently available:

- Able to turn main unit on/off
- Able to turn individual zones on/off
- Read various parameters of main air conditioner unit that are made available by the Actron Connect cloud service
- Able to read individual temperatures from all zones

As you can see, not all functionality has been implemented, as I was only looking for basic on/off control of the system and zones from Hubitat along with the ability to read/graph the individual zone temperatures so I could then control everything from Rule Machine and graph the zone temps using HubiGraphs.

As I get more time, I may look to implement some of the other capabilities around setting temperatures and fan control etc.

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
