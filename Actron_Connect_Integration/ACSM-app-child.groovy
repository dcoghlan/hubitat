/**
 *  ****************  Actron Connect Service Manager  ****************
 *
 *  Design Usage:
 *
 *  Hubitat driver to enable integration with an Actron Air Conditioner via the 
 *  Actron Connect module (ACM-1)
 * 
 * -------------------------------------------------------------------------------------------------------------------
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * ------------------------------------------------------------------------------------------------------------------------------
 *
 *  The code in this app is based code provided by https://github.com/bptworld/Hubitat
 *
 * ------------------------------------------------------------------------------------------------------------------------------
 *
 *  Changes:
 *
 *  1.0.0 - 03/11/20 - Initial release.
 *
 */

import groovy.transform.Field

definition(
    name: "Actron Connect Service Manager",
    namespace: "dcoghlan",
    author: "Dale Coghlan",
    description: "Service Manager for Actron Connect",
    category: "Integrations",
    parent: "dcoghlan:Actron Connect Integration",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
	documentationLink: "https://github.com/dcoghlan/hubitat/blob/main/Actron_Connect_Integration/README.md")

preferences {
    page(name: "prefStart", title: "Setup")
	page(name: "prefPage2", title: "Setup")
}

def prefStart() {
    return dynamicPage(name: "prefStart", title: "", nextPage:"prefPage2", uninstall:true, install: false) {
        section (getFormat("title", "Actron Connect Service Manager")) {
	    }
        section("App Name") {
            label title: "Enter a name for this app (optional)", required: false
        }
        section(getFormat("header-blue", "Authentication")) {
            paragraph "Enter your Actron Connect credentials. These are the credentials you use to login to the Actron Connect mobile app."
            input(name: "username", type: "text", title: "Actron Connect Username", description: "Actron Connect Username", required: true)
            input(name: "AC_password", type: "password", title: "Actron Connect Password", description: "Actron Connect Password", required: true)
        }
        section(getFormat("header-blue", "Polling Settings")) {
            paragraph "By default, a websocket connection will be used as the primary method to receive near real-time updates from the Actron Connect service. Polling can be enabled to be used as a backup means for receiving updates. The recommendation is to only enable this if you are having issues with the websocket connection."
            input(name: "pollEvery", type: "enum", title: "Poll Every X Minutes", options: ["1", "5", "10", "15", "30", "Never"], defaultValue: "Never", required: true)
        }
        section(getFormat("header-blue", "Logging")) {
            input(name: "logEnable", type: "bool", title: "Enable Debug Logging?", defaultValue: settings?.logEnable, displayDuringSetup: true, required: false)
            input(name: "logEnableTime", type: "enum", description: getPrefDesc("logEnableTime"), title: "<b>Disable debug logging after</b>", options: [[900:"15min"],[1800:"30min"],[3600:"60min"]], defaultValue: 1800, required: false)
        }
    }
}

def prefPage2() {
    logIt("prefPage2", "Installation status = ${app.getInstallationState()}", "debug")

    // login to the cloud just to make sure the credentials are correct.
    def loginCheck = getAuth()
    logIt("prefPage2", "loginCheck = ${loginCheck}", "debug")

    if (loginCheck) {
        logIt("prefPage2", "Successfully connected to Actron Connect cloud service.", "debug")
        logIt("prefPage2", "Removing user supplied password as it is no longer needed now.", "debug")
        // There is no need to save the password in the settings, as once
        // connected to the cloud, a token is returned which is used for
        // all API authetication after login.
        settings.AC_password = ""
        
        // This clears the setting in the app so if there is an issue with the 
        // authentication and the user goes back to the previous screen, the 
        // password field will be cleared and the user is forced to re-enter 
        // their password. You know, security and all....
        app.clearSetting("AC_password")
        
        return dynamicPage(name: "prefListDevice",  title: "", install:true, uninstall:false) {
            section(getFormat("header-blue", "Authentication Success")) {
                paragraph "Select the discovered air conditioner below"
                input(name: "node", type: "enum", required:true, multiple:false, options:["${state.BlockId}(ZoneCount=${state.zoneCount})"])
            }
        }
    } 
    else {
        return dynamicPage(name: "Error",  title: "", install:false, uninstall:false) {
            section(getFormat("header-red", "Authentication Error")) {
                paragraph "An error occurred whilst trying to authenticate. Please check the logs for more details. <br>Click done to return to the previous page."
            }
        }
        log.error("prefPage2(): Unable to login. App not installed")
        return
    }
}

def getPrefDesc(type) {
    if(type == "logEnableTime") return "<i>Time after which debug logging will be turned off.</i>"
}

// Modified from: @Stephack & https://github.com/bptworld
def getImage(type) {
    def loc = "<img src=https://raw.githubusercontent.com/bptworld/Hubitat/master/resources/images/"
    if(type == "Blank") return "${loc}blank.png height=40 width=5}>"
    if(type == "checkMarkGreen") return "${loc}checkMarkGreen2.png height=30 width=30>"
    if(type == "optionsGreen") return "${loc}options-green.png height=30 width=30>"
    if(type == "optionsRed") return "${loc}options-red.png height=30 width=30>"
    if(type == "instructions") return "${loc}instructions.png height=30 width=30>"
    if(type == "logo") return "${loc}logo.png height=60>"
}

// Modified from: @Stephack & https://github.com/bptworld
def getFormat(type, myText="") {
	if(type == "header-blue") return "<div style='color:#ffffff;font-weight: bold;background-color:#123262;border: 1px solid;box-shadow: 2px 3px #A9A9A9'>${myText}</div>"
	if(type == "header-red") return "<div style='color:#ffffff;font-weight: bold;background-color:#BB3C40;border: 1px solid;box-shadow: 2px 3px #A9A9A9'>${myText}</div>"
    if(type == "line") return "<hr style='background-color:#1A77C9; height: 1px; border: 0;'>"
    if(type == "title") return "<h2 style='color:#123262;font-weight: bold'>${myText}</h2>"
}

def getDataKey(type, blockId){
    if(type == "Info") return "${blockId}_0_2_1"
    if(type == "Network") return "${blockId}_0_2_2"
    if(type == "WifiScan") return "${blockId}_0_2_3"
    if(type == "Settings") return "${blockId}_0_2_4"
    if(type == "ZoneSettings") return "${blockId}_0_2_5"
    if(type == "CurrentState") return "${blockId}_0_2_6"
}

def logsOff() {
    logIt("logsOff", "debug logging disabled - ${device.getLabel()}", "info")
    device.updateSetting("logEnable", [value: "false", type: "bool"])
    logIt("logsOff", "Settings = ${settings}", "warn")
}

def logsOn() {
    logIt("logsOn", "debug logging enabled - ${device.getLabel()}", "warn")
    device.updateSetting("logEnable", [value: "true", type: "bool"])
    runIn(Long.valueOf(settings?.logEnableTime), logsOff)
}

// After the preferences are saved, the installed() method is called.
void installed() {
    logIt("installed", "Installation status = ${app.getInstallationState()}", "info")
    initialize()
    logIt("installed", "Installed with settings: ${settings}", "debug")
}

void updated() {
    logIt("updated", "Installation status = ${app.getInstallationState()}", "info")
    unsubscribe()
    initialize()
    logIt("updated", "Updated with settings: ${settings}", "debug")
}

void uninstalled() {
	log.debug("uninstalled(): Uninstalling with settings: ${settings}")
    unsubscribe()
	unschedule()
	removeChildDevices()
    removeParentDevice()
}

def initialize() {
    
    logIt("initialize", "Running app Initiliazation", "info")
    if (settings?.logEnable) {
        runIn(Long.valueOf(settings?.logEnableTime), logsOff)
    }
    
    // Create the parent component (main air conditioner device)
    // The reason for creating this as a parent component is due to the 
    // fact that apparently a app cannot create a websocket connect, only 
    // devices can, so we create a parent component so we can create a 
    // websocket connect for real-time updates which we can then pass to the 
    // child components (zones).
    createParentDevice()

    // configure the polling interval for the Actron Connect API. If "never"
    // is selected, the only method for getting updates is via the websocket
    // implemented in the parent driver.
    
    // Code adapted from https://github.com/pierceography/hubitatADC/
	if("${pollEvery}" == "1") {
        logIt("initialize", "API polling set for every 1 minute", "debug")
		runEvery1Minute(pollSystemStatus)
	} else if("${pollEvery}" == "5") {
        logIt("initialize", "API polling set for every 5 minutes", "debug")
		runEvery5Minutes(pollSystemStatus)
	} else if("${pollEvery}" == "10") {
        logIt("initialize", "API polling set for every 10 minutes", "debug")
		runEvery10Minutes(pollSystemStatus)
	} else if("${pollEvery}" == "15") {
        logIt("initialize", "API polling set for every 15 minutes", "debug")
		runEvery15Minutes(pollSystemStatus)
	} else if("${pollEvery}" == "30") {
        logIt("initialize", "API polling set for every 30 minutes", "debug")
		runEvery30Minutes(pollSystemStatus)
	} else {
        logIt("initialize", "API polling disabled", "debug")
        logIt("initialize", "API polling disabled -- updates will be received via websocket", "warning")
	}
    
}

private logIt(method, msg, level="info") {
    def String prefix = "ACSM" // Actron Connect Service Manager
    def String logMsg = "${prefix}-${method}(): ${msg}"
    switch(level.toLowerCase()) {
        case "error":
            log.error(logMsg)
            break
        case "warn":
            log.warn(logMsg)
            break
        case "trace":
            log.trace(logMsg)
            break
        case "debug":
            if (settings?.logEnable) {
                log.debug(logMsg)
            }
            break
        default:
            log.info(logMsg)
            break
    }
}

private getEncodedCreds() {
    def encoded = "${settings.username}:${settings.AC_password}".bytes.encodeBase64().toString()
    return encoded
}

private getAuthHeader() {
	def headers = [
        "Authorization" : "Basic ${getEncodedCreds()}",
        "ContentType" : "application/json"
    ]
	return headers
}

def getAuth() {
    logIt("getAuth", "Attempting to login", "debug")
    
    params = [
        uri : "https://que.actronair.com.au/api/v0/bc/signin",
        headers : getAuthHeader()
        ]
    
    try {
        logIt("getAuth", "Sending Authentication request", "debug")
		httpPost(params) { resp -> 
            logIt("getAuth", "HTTP response received", "debug")
            data = resp.data
            state.BlockId = data.value.airconBlockId
            state.userAccessToken = data.value.userAccessToken
            state.zoneCount = data.value.airconZoneNumber
            state.zones = data.value.zones
            state.version = data.value.version
            state.airconType = data.value.airconType
        }
        return true
	} catch(e) {
        logIt("getAuth", "Error signing in to Actron Connect: ${e}", "error")
        return false
	}    
}

def getHttpParams(String type) {
    def baseUri = "https://que.actronair.com.au"
    switch (type) {
        case "All":
            params = [
                uri : "${baseUri}/rest/v0/devices?user_access_token=${state.userAccessToken}",
                requestContentType : "application/json",
                contentType: 'application/json'
	        ]
            return params
            break
        case "ping":
            params = [
                uri : "${baseUri}/api/v0/messaging/aconnect/ping?user_access_token=${state.userAccessToken}",
                requestContentType : "application/json",
                contentType: 'application/json'
	        ]
            return params
            break
        case {it in ["Info","Network","WifiScan","Settings","ZoneSettings","CurrentState"]}:
            params = [
                uri : "${baseUri}/rest/v0/device/${getDataKey(type, state.BlockId)}?user_access_token=${state.userAccessToken}",
                requestContentType : "application/json",
                contentType: 'application/json'
	        ]
            return params
            break
        default:
            logIt("getHttpParams", "Type not handled", "error")
    }

}

def getDevice(String type = "All") {

    logIt("getDevice", "Using ${state.userAccessToken} to retrive devices from cloud", "debug")

    def params = getHttpParams(type)
    logIt("getDevice", "Parameters: ${params}", "debug")
	
	try {
		httpGet(params) { resp -> 
			logIt("getDevice", "HTTP request submitted", "debug")
            data = resp.data.data
            logIt("getDevice", "Response received: ${data}", "debug")
            
		}
	} catch(e) {
        logIt("getDevice", "HTTP error: ${e}", "error")
	}
    
    return data

}

private getAirConNetworkId() {
    return "${state.BlockId}_${app.id}"
}

private createParentDevice() {
    logIt("createParentDevice", "new DNI would be ${getAirConNetworkId()}", "info")

    def parentDevice = getChildDevice(getAirConNetworkId())
    if(!parentDevice) {
        logIt("createParentDevice", "Creating parent device: ${getAirConNetworkId()}", "debug")

        try {
            parentDevice = addChildDevice("dcoghlan", "Actron Connect AirConditioner", getAirConNetworkId(), null, [label : "A/C Node: ${getAirConNetworkId()}", isComponent: false, name: "A/C Node: ${getAirConNetworkId()}"])
            parentDevice.updateSetting("logEnable", [value: "true", type: "bool"])
            parentDevice.updateSetting("logEnableTime", [value: Long.valueOf(settings?.logEnableTime), type: "long"])

	    } catch(e) {
		    logIt("createParentDevice", "Failed to add parent device with error: ${e}", "error")
        }
    }
    else {
        logIt("createParentDevice", "Using existing parent device: ${parentDevice.getDeviceNetworkId()}", "debug")
    }
    
    // set some state variables on the parent device
    parentDevice.setStateZoneCount(state.zoneCount)
    parentDevice.setStateZones(state.zones)
    parentDevice.setUserAccessToken(state.userAccessToken)
    parentDevice.setRxDelay(false)
    parentDevice.updateSetting("logEnableTime", [value: Long.valueOf(settings?.logEnableTime), type: "long"])
    parentDevice.createChildDevices()
    // As something may have change (i.e. userAccessToken), we force close any existing websocket connection
    // and start a new one with the details retrieved here
    parentDevice.webSocketClose()
    parentDevice.webSocketOpen()
    
}

/******************************************************************************
# Purpose: Create the child device as specified
# 
# Details: Use the panel ID and switch type (disarm, armstay, armaway) as the
# device identification value
******************************************************************************/
private createChildDevice(deviceType, deviceNetworkId, zoneName) {

	try {
		// create the child device
		addChildDevice("dcoghlan", "Actron Connect Zone", deviceNetworkId, null, [label : "A/C Zone: ${zoneName}", isComponent: false, name: "A/C Zone: ${zoneName}"])
		createdDevice = getChildDevice(deviceNetworkId)
	} catch(e) {
		logIt("createChildDevice", "Failed to add child device with error: ${e}", "error")
	}
}

/******************************************************************************
# Purpose: Delete all child devices
# 
# Details: 
# 
******************************************************************************/
private removeChildDevices() {

	try {
        x = 0
        for ( i in state.zones ) {
            zoneFriendlyName = i.replace(' ','_')
            zoneDeviceNetworkId = "${state.BlockId}_${x}"
            def existingDevice = getChildDevice(zoneDeviceNetworkId)
        
            if(existingDevice) {
                logIt("removeChildDevices", "Removing child device: ${zoneDeviceNetworkId}", "info")
		        deleteChildDevice(zoneDeviceNetworkId)
		    }
            x += 1
        }
	} catch(e) {
		logIt("removeChildDevices", "Failed to remove child device ${zoneDeviceNetworkId} with error: ${e}", "Error")
	}

}

private removeParentDevice() {
    try {
        def existingDevice = getChildDevice(getAirConNetworkId())
        if(existingDevice) {
            logIt("removeParentDevice", "Removing parent device: ${getAirConNetworkId()}", "info")
            existingDevice.webSocketClose()
            deleteChildDevice(getAirConNetworkId())
        }
        else {
            logIt("removeParentDevice", "Device not found: ${getAirConNetworkId()}", "info")
        }    
    } catch(e) {
		logIt("removeParentDevice", "Failed to remove parent device ${getAirConNetworkId()} with error: ${e}", "error")
    
    }
}

/*********************************************************************
# pollSystemStatus is initiated by initialized() and run periodically
#
# ultimate purpose is to retrieve latest data from Actron Connect Cloud
# and populate child device information
*********************************************************************/
def pollSystemStatus() {
    logIt("pollSystemStatus" , "Getting device: CurrentState", "info")
    def currentState = getDevice("CurrentState")
    logIt("pollSystemStatus" , "Received data: ${currentState}", "debug")
    updateAirConState(currentState.last_data.DA)

}

/*********************************************************************
# Grab the latest data(Full) from the API, and return the specific 
# data key as specified by the type parameter.
#
# The types are documented as follows
#
#    String type     Device data key
#    --------------  --------------------
#    Info            BlockID_0_2_1  
#    Network         BlockID_0_2_2  
#    WifiScan        BlockID_0_2_3  
#    Settings        BlockID_0_2_4  
#    ZoneSettings    BlockID_0_2_5  
#    CurrentState    BlockID_0_2_6          
#
*********************************************************************/

def getDeviceInfo(type) {
    logIt("getDeviceInfo" , "Getting device information type: ${type}", "debug")
    data = getDevice()
    return data[getDataKey(type, state.BlockId)]
}

def getDataAll() {
    logIt("getDataAll" , "Getting device information type: ALL", "debug")
    data = getDevice()
}

def updateAirConState(data) {
    try {
        def cd = getChildDevice(getAirConNetworkId())
        if(cd) {
            logIt("updateAirConState", "Found device to update: ${getAirConNetworkId()}", "info")
            cd.updateCurrentState(["jsonState": data])
        }
        else {
            logIt("updateAirConState", "Device not found: ${getAirConNetworkId()}", "error")
        }    
    } catch(e) {
		logIt("updateAirConState", "Failed to update device ${getAirConNetworkId()} with error: ${e}", "error")
    
    }
}


/******************************************************************************
# Wrapper method for updating the API for enabled zones. This is required so 
# that its possible to unschedule the update of the enabled zones if a schedule
# already exists.
#
# Basically when a simple automation rule enables multiple zones in the one rule
# it generates individual "on/off" events, which in turn fire off multiple API
# calls, but because the whole "state" of all zones is sent in every call, To
# handle this, the "local" state of all the zones is held in state.enabledZones
# in the Air Conditioner device as a list which is locally updated and then
# used to generate the body which is sent to the API.
# 
# What ends up happening is the first zone is enabled, and then sent to the API,
# which updates zone A, which is all good, and then shortly afterwards, zone B
# is updated and then sent to the API, and again all good. And because we sent
# a hubitat on/off event before we send the API request, the zone device state
# is set correctly. Now immeditatley after the first API is sent, we also
# receive state data back from the websocket whenever there is ANY change
# (whether it is enabling of zones, or the temp in a zone is +/- 0.1 deg) which
# means that after the first update, we recieve the list of enabledZones and
# zone A will be enabled and zone B will be disabled (as its showing the zone
# status after the first API call we sent). This update gets processed and zone
# A is enabled (but because we have already set this locally, all is good and
# no hubitat event is generated), but in this stage, zone B wasn't enabled yet,
# so zone B is sent an off event, which turns the zone off. But immediatley
# following this, we receive a second update to enable zone B (as part of our
# second API call) and then zone B is enabled. This effectivley causes the zone
# to turn on > off and then on again which is really annoying.
******************************************************************************/
def updateAPIZoneWrapper(data) {
    updateAPI(data)
}

/******************************************************************************
# Generic HTTP Put method
******************************************************************************/
def updateAPI(data) {
    configBlock = getDataKey(data.type, state.BlockId)
    params = [
        uri : "https://que.actronair.com.au/rest/v0/device/${configBlock}?user_access_token=${state.userAccessToken}",
        requestContentType : "application/json",
        contentType: 'application/json',
        body: data.jsonBody
	]
    logIt("updateAPI", params, "debug")
    try {
        httpPut(params) { resp -> 
            logIt("updateAPI", "HTTP PUT request submitted", "debug")
        }
	} catch(e) {
        logIt("updateAPI", "HTTP error: ${e}", "error")
	}
}

//*****************************************************************************//
//*****************************************************************************//
//             Child Air Conditioner Component Methods                         //
//*****************************************************************************//
//*****************************************************************************//

/******************************************************************************
# Updated the enabled zones
#
# unschedules any previous update if it hasn't executed yet (within the 1 
# second window) and creates a new schedule to send the latest updated list of
# zones.
******************************************************************************/

def updateEnabledZones(data) {
    def builder = new groovy.json.JsonBuilder()
    def root = builder.DA {
        enabledZones (data)
    } 
    logIt("updateEnabledZones", "JSON Body: ${builder.toString()}", "debug")
    unschedule(updateAPIZoneWrapper)
    runInMillis(1000, "updateAPIZoneWrapper", [overwrite: true, data: ["jsonBody": builder.toString(), "type": "ZoneSettings"]])
}

/******************************************************************************
# Turn on the main air conditioner unit
******************************************************************************/
def turnOn() {
    def builder = new groovy.json.JsonBuilder()
    def root = builder.DA {
        amOn true
    }
    logIt("turnOn", "JSON Body: ${builder.toString()}", "debug") 
    updateAPI(["jsonBody": builder.toString(), "type": "Settings"])
}

/******************************************************************************
# Turn off the main air conditioner unit
******************************************************************************/
def turnOff() {
    def builder = new groovy.json.JsonBuilder()
    def root = builder.DA {
        amOn false
    }
    logIt("turnOff", "JSON Body: ${builder.toString()}", "debug") 
    updateAPI(["jsonBody": builder.toString(), "type": "Settings"])
}

/******************************************************************************
# Send ping to the cloud service to check connectivity
******************************************************************************/
def sendPing() {
    logIt("sendPing", "Sending ping", "info")

    def params = getHttpParams("ping")
    logIt("sendPing", "Parameters: ${params}", "debug")
	
	try {
		httpGet(params) { resp -> 
			logIt("sendPing", "HTTP request submitted", "debug")
            data = resp.data
            logIt("sendPing", data, "info")
            
		}
	} catch(e) {
        logIt("sendPing", "HTTP error: ${e}", "error")
	}
    
    return data
}