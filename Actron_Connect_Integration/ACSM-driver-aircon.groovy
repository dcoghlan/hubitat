/**
 *  ****************  Actron Connect AirConditioner  ****************
 *
 *  Design Usage:
 *
 *  Hubitat driver to enable control of an Actron Air Conditioner when used with the
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
 *  Changes:
 *
 *  1.0.0 - 03/11/20 - Initial release.
 *  1.0.1 - Fixed issue with rxDelayTime not being set by default which causes
 *          issues with websocket updates being parsed.
 *  1.0.2 - Added extra options for logEnable times (90Min & 120Min) to help debugging websocket timeouts
 *  1.0.3 - Dynamically save all values (except Url) received when negotiating WebSocket connection
 *  1.0.4 - Once websocket is started, schedule it to close randomly within an hour. This will protect against a "stale" 
 *          connection where for no reason, we stop receiving websocket messages
 *        - Upon device update, remove state variables no longer used due to dynamic loading
 *        - Modified logging level of websocket logs
 *  1.0.5 - Modified webSocketConnect & webSocketAbort to use dynamic state variables
 *  1.0.6 - Refactored websocket connect/reconnect logic
 *        - Removed capability TODO items
 *        - Increase schedule websocketclose timer to "every 3 hours" to reduce load from forced websocket reconnections
 *  1.0.7 - Fixed issue where upon installation, the websocket connection wasn't automatically started.
 *  1.0.8 - Changed scheduled websocketclose to occur 55 mins after the connection is started, due to issue seen where 
 *          no updates were being received after a period of time. The exact time after which message stop being sent by 
 *          the server is unknown, but its always occurs sometime after 1 hour after the connection was started.
 */

metadata {
    definition(name: "Actron Connect AirConditioner", namespace: "dcoghlan", author: "Dale Coghlan") {

	    capability "Switch"
        capability "TemperatureMeasurement"
        capability "Thermostat"

		// Custom attributes
		attribute "lastupdate", "date"
        attribute "mode", "number"
        attribute "fanSpeed", "number"
        attribute "setPoint", "number"  // TODO: Verify if this is handled by capbility/ThermostatSetpoint and implement if needed
        attribute "compressorActivity", "number"
        attribute "isInESP_Mode", "string"
        attribute "roomTemp_oC", "number"
        attribute "errorCode", "number"
        attribute "fanIsCont", "number"
		
		command "poll"
        command "webSocketClose"
        command "webSocketOpen"
        command "webSocketPing"
    }
}

preferences {
    section("URIs") {
        input name: "logEnable", type: "bool", description: getPrefDesc("blank"), title: "<b>Enable debug logging</b>", defaultValue: true
        input name: "logEnableTime", type: "enum", description: getPrefDesc("logEnableTime"), title: "<b>Disable debug logging after</b>", options: [[900:"15min"],[1800:"30min"],[3600:"60min"], [5400:"90min"], [7200:"120min"]], defaultValue: 1800
        input name: "logEnableZones", type: "bool", description: getPrefDesc("logEnableZones"), title: "<b>Enable debug logging on zones</b>", defaultValue: settings?.logEnableZones
        input name: "txtEnable", type: "bool", description: getPrefDesc("blank"), title: "<b>Enable descriptionText logging</b>", defaultValue: settings?.txtEnable
        input name: "rxDelayTime", type: "enum", description: getPrefDesc("rxDelayTime"), title: "<b>Set Update Delay Time</b>", options: [[0:"Disabled"],[1000:"1s"],[2000:"2s"],[5000:"5s"],[10000:"10s"]], defaultValue: 2000
        input name: "simpleTempReporting", type: "bool", description: getPrefDesc("simpleTempReporting"), title: "<b>Enable simple temp reporting</b>", defaultValue: settings?.simpleTempReporting
    }
}

def getPrefDesc(type) {
    if(type == "blank") return ""
    if(type == "logEnableTime") return "<i>Time after which debug logging will be turned off.</i>"
    if(type == "logEnableZones") return "<i>Easily enable debug logging on all zone child devices. Debug logging will automatically turn off after the time specified in the settings of the individual child device.</i>"
    if(type == "rxDelayTime") return "<i>Enable this option if you find that after enabling/disabling a zone in the dashboard, the zone toggles on/off by itself. This mainly occurs when toggling multiple zones in quick succession. Exmaple: If you enable zones 1 & 2 in quick succession, both will change on the dashboard, however these are processed as 2 separate events. A websocket msg is received after zone 1 is enabled, which also contains the state for zone 2 which at the time was disabled. Its not until the update for the second event is received from the websocket that everything will converge to the desired state. This setting delays the parsing of the zone updates for the specified period of time, and only processes the last update received within the interval set here, thereby hopefully just implementing the converged state into Hubitat. </i>"
    if(type == "simpleTempReporting") return "<i>By default, the websocket connection sends an update whenever the temperature in a zone changes. This in turn generates an event for the zone. Enable this setting to only use full numbers like 19 for temperature reporting rather than detailed numbers like 19.4. This will reduce the number of events that are shown in the event logs.</i>"

}
/******************************************************************************
 * Logging control
******************************************************************************/
private logIt(method, msg, level="info") {
    def String prefix = "ACAC" // Actron Connect Air Conditioner
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
            if (logEnable) {
                log.debug(logMsg)
            }
            break
        default:
            log.info(logMsg)
            break
    }
}

def logsOff() {
    logIt("logsOff", "debug logging disabled...", "warn")
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}

def logsOn() {
    logIt("logsOn", "debug logging enabled...", "warn")
    device.updateSetting("logEnable", [value: "true", type: "bool"])
    runIn(Long.valueOf(settings?.logEnableTime), logsOff)
}

def zoneLoggingToggle(Boolean action) {
    logIt("zoneLoggingToggle", "Method invoked", "debug")
    x = 0
    while ( x  < state.zoneCount ) {
        def cd = getChildDevice("${device.getDeviceNetworkId()}_${x}")
        if(cd) {
            switch(action) {
                case true:
                    cd.logsOn()
                    break
                case false:
                    cd.logsOff()
                    break
                Default:
                    logIt("zoneLoggingToggle", "Unhandled action", "error")
            }
		}
        x += 1
    }
}

/******************************************************************************
 * Standard methods
******************************************************************************/
void installed() {
    logIt("installed", "device was installed", "info")
    if (logEnable) runIn(Long.valueOf(settings?.logEnableTime), logsOff)
    createChildDevices()
    webSocketOpen()
}

def updated() {
    logIt("updated", "updated...", "info")
    logIt("updated", "debug logging is: ${logEnable == true}", "warn")
    logIt("updated", "description logging is: ${txtEnable == true}", "warn")
    unschedule()
    if (logEnable) runIn(Long.valueOf(settings?.logEnableTime), logsOff)
    zoneLoggingToggle(settings?.logEnableZones)
    if (settings?.logEnableZones) {
        device.updateSetting("logEnableZones", [value: "false", type: "bool"])
    }
    stateCleanup()
    initialize()
    
}
    
def initialize() {
    logIt("initialize", "initialize...", "info")
    // Close any existing websocket connections
    webSocketAbort()
    webSocketClose()
    // debounce opening the websocket connection. Because we send both an abort
    // and a close, if we have an existing websocket connection, we get both a
    // failure and closing message from the websocket, which automatically
    // triggers a websocket reconnect, but we cannot be guaranteed to receive
    // those messages from the websocket, so we set a scheduled task to run in 5
    // seconds to start the connection again, and if a reconnect is happened to
    // be triggered from a received websocket failure/closing message, it will
    // debounced with this schedule, therefore only starting the connection one
    // time. Without debouncing this here, we get into an endless reconnection
    // loop.
    runIn(5, webSocketOpen)
}

private stateCleanup() {
    state.remove("protocolVersion")
    state.remove("Url")
    state.remove("connectionToken")
    state.remove("connectionId")
    state.remove("lastUpdateReceived")
}

/******************************************************************************
 * Websocket Implementation
******************************************************************************/
private getDateTimeNow() {
    return (new Date(now())).format("EEE MMM dd yyyy HH:mm:ss.S z", location.timeZone)
}
// wrapper method to open the websocket connection
def webSocketOpen() {
    webSocketNegotiate()
}

def webSocketNegotiate() {
    logIt("webSocketNegotiate", "method invoked", "debug")
    logIt("webSocketNegotiate", "Starting negotioation for websocket connection", "debug")
    
    params = [
        uri : "https://que.actronair.com.au/api/v0/messaging/aconnect/negotiate?clientProtocol=1.5&user_access_token=${state.userAccessToken}",
        requestContentType : "application/json",
        contentType: 'application/json',
        body: jsonBody
	]

    logIt("webSocketNegotiate", "Parameters: ${params}", "debug")
    
    try {
        httpGet(params) { resp -> 
            logIt("webSocketNegotiate", "Response received", "debug")
            def response = resp.data
            logIt("webSocketNegotiate", "Data: ${response}", "debug")
            data = resp.data
            data.each {
                if (it.key != "Url") {
                    logIt("webSocketNegotiate", "Updating state: ${it.key} = ${it.value}", "debug")
                    state."${it.key}" = it.value
                }
            }
        }
               
	} catch(e) {
        logIt("webSocketNegotiate", "HTTP error: ${e}", "error")
	}
    webSocketConnect()
}

def webSocketConnect() {
    logIt("webSocketConnect", "method invoked", "debug")
    logIt("webSocketConnect", "Opening websocket connection", "debug")
    
    def encodedConnectionToken = java.net.URLEncoder.encode(state.ConnectionToken, "UTF-8")
    try {
        interfaces.webSocket.connect("wss://que.actronair.com.au/api/v0/messaging/aconnect/connect?transport=webSockets&clientProtocol=${state.ProtocolVersion}&user_access_token=${state.userAccessToken}&connectionToken=${encodedConnectionToken}", pingInterval:300)
    } 
    catch(e) {
        logIt("webSocketConnect", "initialize error: ${e.message}", "debug")
        logIt("webSocketConnect", "WebSocket connect failed", "error")
    }
    webSocketStart()
}

def webSocketStart() {
    logIt("webSocketStart", "method invoked", "debug")
    pauseExecution(1000)
    logIt("webSocketStart", "Sending start request", "debug")
    def encodedConnectionToken = java.net.URLEncoder.encode(state.ConnectionToken, "UTF-8")

    params = [
        uri : "https://que.actronair.com.au/api/v0/messaging/aconnect/start?transport=webSockets&clientProtocol=${state.ProtocolVersion}&user_access_token=${state.userAccessToken}&connectionToken=${encodedConnectionToken}",
	]
    logIt("webSocketStart", "Parameters: ${params}", "debug")
    
    try {
        httpGet(params) { resp -> 
            logIt("webSocketStart", "Response received", "debug")
            def response = resp.data
            logIt("webSocketStart", response, "debug")
            if (response.Response == "started") {
                logIt("webSocketStart", "WebSocket session started successfully", "debug")
                runIn(3300, webSocketClose)
                // Reset the reconnectDelay to 1 sec since we have a successful connection started.
                state.wsReconnectDelay = 1
            }
        }
        runEvery10Minutes(webSocketPing)
        state.wsStartTime = getDateTimeNow()
        
	} catch(e) {
        logIt("webSocketStart", "HTTP error: ${e}", "error")
	}
}

def webSocketAbort() {
    logIt("webSocketAbort", "method invoked", "debug")
    logIt("webSocketAbort", "Sending websocket abort request", "debug")
    
    def encodedConnectionToken = java.net.URLEncoder.encode(state.ConnectionToken, "UTF-8")

    params = [
        uri : "https://que.actronair.com.au/api/v0/messaging/aconnect/abort?transport=webSockets&clientProtocol=${state.ProtocolVersion}&user_access_token=${state.userAccessToken}&connectionToken=${encodedConnectionToken}"
	]

    logIt("webSocketAbort", "params - ${params}", "debug")
    
    try {
        httpPost(params) { resp -> 
            logIt("webSocketAbort", "HTTP Status = ${resp.status}", "debug")
            status = resp.status
        }
        if (status == 200) {
            logIt("webSocketAbort", "Successfully sent websocket abort request.", "info")
            
        }
	} catch(e) {
        logIt("webSocketAbort", "Unable to abort websocket. Error: ${e}", "error")
	}
}

def webSocketClose() {
    logIt("webSocketClose", "method invoked", "debug")

    // Close the connection from the hubitat side
    try {
        interfaces.webSocket.close()
        logIt("webSocketClose", "Gracefully closing Hubitat websocket connection", "debug")
    }
    catch(e) {
        logIt("webSocketClose", "close websocket error: ${e.message}", "error")
        logIt("webSocketClose", "WebSocket close failed", "error")
    }
}

// Not really used
def webSocketSendMsg(String data) {
    logIt("webSocketSendMsg", "method started: data received: ${data}", "debug")
    // Send string message
    interfaces.webSocket.sendMessage(data)
}

// rather than sending a ping via the websocket, seems they do it via a REST API. So we need to call the service manager to do it for us.
def webSocketPing() {
    logIt("sendPing", "Send ping method started", "debug")
    parent.sendPing()
}
    
/* 
/******************************************************************************
 * User defined method to receive incoming messages from the websocket connection.
 * This method is called by default by the websocket
 *
 * Example Responses:
 *
 * [C:s-0,11DB68EF4, M:[[G:0, V:2, D:2, DA:[ConnectionStatus:111, CurrentConnection:[SSID:MySSID, IP:1.1.1.108, LinkStats:[errors:[flags:[3,5,6,C], Count:279, Last:PostResponseEmpty], stats:[good:[26, 4130], bad:[0, 1, 0, 277]]]], RemoteServer:que.actronair.com.au], GUID:ACONNECT0011AABBCCDD_0_2_2, node:ACONNECT0011AABBCCDD, timestamp:1591320709400]]]
 *
 * [C:s-0,11DB36DA9, M:[[G:0, V:2, D:4, DA:[amOn:true, tempTarget:20.0, mode:1, fanSpeed:2, enabledZones:[0, 0, 0, 0, 0, 0, 0, 1]], GUID:ACONNECT0011AABBCCDD_0_2_4, node:ACONNECT0011AABBCCDD, timestamp:1591319479938]]]
 * [C:s-0,11DB36DCE, M:[[G:0, V:2, D:5, DA:[0, 0, 0, 0, 0, 0, 0, 1], GUID:ACONNECT0011AABBCCDD_0_2_5, node:ACONNECT0011AABBCCDD, timestamp:1591319480234]]]
 * [C:s-0,11DB679D3, M:[[G:0, V:2, D:6, DA:[isOn:true, mode:1, fanSpeed:2, setPoint:20.0, roomTemp_oC:19.7, isInESP_Mode:true, fanIsCont:0, compressorActivity:0, errorCode:, individualZoneTemperatures_oC:[16.8, 17.1, 17.3, 17.6, 12.7, 15.1, 16.2, 19.8], enabledZones:[0, 0, 0, 0, 0, 0, 0, 1], liveTimer:null], GUID:ACONNECT0011AABBCCDD_0_2_6, node:ACONNECT0011AABBCCDD, timestamp:1591320673555]]]
******************************************************************************/

def parse(String description) {
    try {
        jsonObject = new groovy.json.JsonSlurper().parseText(description)
        if (jsonObject == null) {
            logIt("parse", "websocket description is null", "debug")
            return
        }
    }
    catch(e) {
        logIt("parse", "websocket description failed to parse json = ${e}", "error")
        return
    }
    if (jsonObject.M) {
        logIt("parse", "Websocket message received ${jsonObject}", "debug")
        state.wsLastUpdateReceived = getDateTimeNow()
        def msg = jsonObject.M
        def msgDataType = msg[0].D
        switch(msgDataType.toInteger()) {
            case 2:
                // Wifi Connection status message
                // Will log this for now, but not sure if anyone needs this information yet.
                msgData = msg[0].DA
                logIt("parse", "Received websocket DataType: ${msgDataType}(network)", "debug")
                logIt("parse", msgData, "debug")
                break
            case 4:
                // Settings status message
                // Currently only handling enabledZones.
                // TODO: Handle all the other attributes [amOn:true, tempTarget:20.0, mode:1, fanSpeed:2]
                msgData = msg[0].DA
                logIt("parse", "Received websocket DataType: ${msgDataType}(settings)", "info")
                logIt("parse", msgData, "debug")
                state.enabledZones = msgData.enabledZones
                if ((state?.rxDelay == "True") && (settings?.rxDelayTime)) {
                    // if the rxDelay flag has been set due to an API request just being submitted, we
                    // want to delay the updating of the devices by x amount of milliseconds. This is
                    // to stop the enabled status of zones flapping from 0 > 1 > 0 etc which is really
                    // annoying when you see the tile in the dashboard flash shortly after it is
                    // pressed. It doesn't matter if we delay these updates as the zones have already
                    // been enabled/disabled by Hubitat, and we have already sent the event to the
                    // device and persitsted the state in the state variable. 
                    logIt("parse", "Delaying execution by ${Long.valueOf(settings?.rxDelayTime)} milliseconds", "debug")
                    unschedule(updateZonesEnabledWrapper)
                    runInMillis(Long.valueOf(settings?.rxDelayTime), 'updateZonesEnabledWrapper', [overwrite: true, data: ["json": msgData]])
                }
                else {
                    logIt("parse", "rxDelay flag or value not set. Instant execution", "debug")
                    updateZonesEnabled(msgData.enabledZones)
                }
                break
            case 5:
                // Zone Settings status message
                // Will log this for now and come back to it in the future.
                msgData = msg[0].DA
                logIt("parse", "Received websocket DataType: ${msgDataType}(zone settings)", "info")
                logIt("parse", msgData, "debug")
                break
            case 6:
                msgData = msg[0].DA
                // Save the temperature and enabledZones arrays into the state. This is used
                // as an up to date local variable which can then be modified locally and then
                // used to be sent back to the actron connect API.
                state.individualZoneTemperatures_oC = msgData.individualZoneTemperatures_oC
                state.enabledZones = msgData.enabledZones
    
                logIt("parse", "Processing websocket DataType: ${msgDataType}(current state)", "debug")
                if ((state?.rxDelay == "True") && (settings?.rxDelayTime)) {
                    logIt("parse", "Delaying execution by ${settings?.rxDelayTime} milliseconds", "debug")
                    unschedule(updateCurrentState)
                    runInMillis(Long.valueOf(settings?.rxDelayTime), 'updateCurrentState', [overwrite: true, data: ["jsonState": msgData]])
                }
                else {
                    logIt("parse", "rxDelay flag or value not set. Instant execution", "debug")
                    updateCurrentState(["jsonState": msgData])
                }
                break
            default:
                logIt("parse", "unhandled ${msgDataType}", "error")
                break
        }
        
    }
}

void parse(List<Map> description) {
    description.each {
        if (it.name in ["switch","level"]) {  // i've left "level" in here to remind me what this does, even though i dont use it.
            if (txtEnable) {
                if (device.currentValue(it.name) != it.value) {
                    logIt("parseList", it.descriptionText, "info")
                }
            }
            sendEvent(it)
        } 
    }
}

/* 
 * User defined method to receive incoming messages from the websocket connection.
 * This method is called by default by the websocket
 *
 * We don't seem to get many (if any) websocket status messages from the Actron connect cloud.
 * Need to see if we can capture some of these and action them accordingly. But for now lets just log them.
 *
 * ACAC-webSocketStatus(): Websocket status: status: open
 * ACAC-webSocketStatus(): Websocket status: failure: Connection reset
 * ACAC-webSocketStatus(): Websocket status: status: closing
 *
 * ACAC-webSocketStatus(): Websocket status: failure: Connection reset
 * ACAC-webSocketStatus(): Websocket status: status: closing
 *
 * Original code for webSocketStatus() and webSocketReconnect() adapted from:
 * https://github.com/ogiewon/Hubitat/blob/master/Drivers/logitech-harmony-hub-parent.src/logitech-harmony-hub-parent.groovy
 */
def webSocketStatus(String message) {
    logIt("webSocketStatus", "Status message received - ${message}", "debug")
    switch (message) {
        case "status: open":
            logIt("webSocketStatus", "Actron Connect WebSocket connection established.", "info")
            break
        case "status: closing":
            logIt("webSocketStatus", "WebSocket connection closing", "warn")
            runIn(1, webSocketClose)
            // Debounce calling webSocketReconnect, as sometimes we receive this
            // closing message and the below failure message in quick
            // succession, but only sometimes....
            runIn(3, webSocketReconnect)
            break
        case {it.startsWith("failure:")}:
            logIt("webSocketStatus", "WebSocket failure: ${message}", "warn")
            // debounce calling webSocketReconnect. See comments about ^^^
            runIn(3, webSocketReconnect)
            break
        default:
            logIt("webSocketStatus", "Unhandled status message received: ${message}", "error")
            runIn(1, webSocketClose)
            runIn(3, webSocketReconnect)
            break
        
    }
}

def webSocketReconnect() {
    // don't let delay get too crazy, max it out at 10 minutes
    if(state.wsReconnectDelay > 600) state.wsReconnectDelay = 600
    
    logIt("webSocketReconnect", "Scheduling websocket reconnect in ${state.wsReconnectDelay} seconds", "info")

    //If the service is offline or not reachable, give it some time before trying to reconnect
    runIn(state.wsReconnectDelay, webSocketOpen)
    
    // first delay is 2 seconds, doubles every time
    state.wsReconnectDelay = (state.wsReconnectDelay ?: 1) * 2
}

/******************************************************************************
 * Translation Maps
******************************************************************************/
def getOnOffLabelMap() {
    return [
        0 : "off",
        1 : "on"
	]
}

def getIsOnLabelMap() {
    return [
        "false" : "off",
        "true" : "on"
	]
}

def getModeMapToHe() {
    return [
        "0" : "auto",
        "1" : "heat",
        "2" : "cool",
        "3" : "Fan Only"
	]
    //0 = Auto
    //1 = Heat
    //2 = Cool
    //3 = FanOnly
    // "auto", "off", "heat", "emergency heat", "cool"
}

def getModeMapFromHe() {
    return [
        "Auto" : 0,
        "Heat" : 1,
        "Cool" : 2,
        "Fan Only" : 3
    ]
}

def getCompressorActivityMapToHe() {
    return [
        "0" : "heating",
        "1" : "cooling",
        "2" : "idle"
    ]
}

def reportedTemp(data) {
    if (simpleTempReporting) {
        logIt("reportedTemp", "Simple reporting enabled: (${data} > ${data.toInteger()}", "debug")
        return data.toInteger()
    }
    else {
        return data
    }
}
/******************************************************************************
 * Purpose: 
 * Update state from parent app
 *
 * Details: 
 * Accepts the following as input: enabled (0 or 1) & temp
******************************************************************************/

def updateState(updatedSwitchState, updatedTemp) {
    
    def currentSwitchState = device.currentValue("switch")
    def currentTemp = device.currentValue("temperature")
    
    if (updatedSwitchState != currentSwitchState) {
        sendEvent(name: "switch", value: updatedSwitchState,  isStateChange: true, displayed: true)
        log.debug("updateState(): ${device.getDeviceNetworkId()}(${device.getDisplayName}): switch = ${currentSwitchState} > ${updatedSwitchState}")
    }
    
    if (updatedTemp != currentTemp) {
        sendEvent(name: "temperature", value: updatedTemp,  isStateChange: true, displayed: true)
        log.debug("updateState(): ${device.getDeviceNetworkId()}(${device.getDisplayName}): temperature = ${currentTemp} > ${updatedTemp}")
    }
}

//*****************************************************************************
// Capability Command Methods: switch
//*****************************************************************************
def on() {
    state.rxDelay = "True"
    logIt("on", "${device.getDeviceNetworkId()}(${device.getDisplayName()}): method invoked", "debug")
    parse([[name:"switch", value:"on", descriptionText:"${device.getDeviceNetworkId()}(${device.getDisplayName()}) was turned on"]])
    parent.turnOn()
}

def off() {
    state.rxDelay = "True"
    logIt("off", "${device.getDeviceNetworkId()}(${device.getDisplayName()}): method invoked", "debug")
    parse([[name:"switch", value:"off", descriptionText:"${device.getDeviceNetworkId()}(${device.getDisplayName()}) was turned off"]])
    parent.turnOff()
}

//*****************************************************************************
// Capability Command Methods: polling
//*****************************************************************************
def poll() {
    state.rxDelay = "False"
    logIt("poll", "${device.getDeviceNetworkId()}(${device.getDisplayName()}): method invoked", "debug")
    parent.pollSystemStatus()
}

//*****************************************************************************
// Exposed methods to update state where required
//*****************************************************************************

def setStateZoneCount(data) {
    state.zoneCount = data
}

def setStateZones(data) {
    state.zones = data
}

def setUserAccessToken(data) {
    state.userAccessToken = data
}

def setBlockId(data) {
    state.blockId = data
}

def setRxDelay(Boolean data) {
    state.rxDelay = data
}

def setSimpleTempReporting(Boolean data) {
    simpleTempReporting = data
}

def setLogEnableTime(Long data) {
    device.updateSetting("logEnableTime", [value: data, type: "long"])
    logIt("setLogEnableTime", "settings = ${settings}", "info")
}

/******************************************************************************
# Purpose: Create the appropriate number of child devices, based on the zones
# listed in states.zones
# 
# Details: Uses the network device id and the index number to create the zone
# device network id.
#
# Not sure what happens when less than 8 zones are configured. I have 8
# zones configured so this array is fully populated. Will need to test against
# a system which has less than 8 zones configured. Who wants to volunteer?
******************************************************************************/
def createChildDevices() {
    logIt("createChildDevices", "settings = ${settings}", "debug")
    x = 0
    for ( i in state.zones ) {
        zoneFriendlyName = i.replace(' ','_')
        zoneDeviceNetworkId = "${device.getDeviceNetworkId()}_${x}"
        def existingDevice = getChildDevice(zoneDeviceNetworkId)
        
        if(!existingDevice) {
		    logIt("createChildDevices", "Creating child device: ${zoneDeviceNetworkId}", "info")
		    def cd = createChildDevice("Actron Connect Zone", zoneDeviceNetworkId, zoneFriendlyName, x)
            if (!cd) {
                cd = getChildDevice(zoneDeviceNetworkId)
            }
            cd.logsOn()
		} 
        x += 1
    }
}

/******************************************************************************
# Purpose: Create the child device as specified
#
# Details: Called from createChildDevices(), which is invoked from the Actron 
# Connect Service Manager when the Air Conditioner device is created
******************************************************************************/
private createChildDevice(deviceType, deviceNetworkId, zoneName, index) {
	try {
		addChildDevice("dcoghlan", deviceType, deviceNetworkId, [label : "A/C Zone: ${zoneName}", isComponent: true, name: "A/C Zone: ${zoneName}"])
		createdDevice = getChildDevice(deviceNetworkId)
        createdDevice.sendEvent(name: "index", value: index, isStateChange: false, displayed: true, descriptionText:"${createdDevice.displayName} setting index to ${index}")
	} catch(e) {
		logIt("createChildDevice", "Failed to add child device with error: ${e}", "error")
	}
}


//*****************************************************************************
// Taken from ACSM
//*****************************************************************************

/* 
 * updateCurrentState
 *
 *  [isOn:true, mode:1, fanSpeed:2, setPoint:20.0, roomTemp_oC:20.2, isInESP_Mode:true, fanIsCont:0, compressorActivity:0, errorCode:, individualZoneTemperatures_oC:[16.9, 17.3, 17.6, 18.0, 13.1, 17.1, 20.3, 20.2], enabledZones:[0, 0, 0, 0, 0, 0, 1, 1], liveTimer:null]
*/
def updateCurrentState(data) {
    // zone on/off mapping
    def onOffMap = getOnOffLabelMap()

    // main aircon isOn mapping
    def isOnMap = getIsOnLabelMap()
    
    // mode mapping
    def modeMap = getModeMapToHe()
    
    // compressor activity mapping
    def compressorActivityMap = getCompressorActivityMapToHe()
    
    logIt("updateCurrentState", "Updating from current state data: ${data.jsonState}", "debug")

    def switchValueNew = isOnMap[String.valueOf(data.jsonState.isOn)]
    def modeValueNew = modeMap[String.valueOf(data.jsonState.mode)]
    def operatingModeValueNew = compressorActivityMap[String.valueOf(data.jsonState.compressorActivity)]
    device.sendEvent(name: "switch", value: switchValueNew, descriptionText: "${device.getName()} was turned ${switchValueNew}")
    device.sendEvent(name: "temperature", value: data.jsonState.get("roomTemp_oC"), descriptionText: "${device.getName()} temperature is now ${data.jsonState.get("roomTemp_oC")}")
    device.sendEvent(name: "thermostatSetpoint", value: data.jsonState.get("setPoint"), descriptionText: "${device.getName()} thermostatSetpoint is now ${data.jsonState.get("setPoint")}")
    device.sendEvent(name: "thermostatMode", value: modeValueNew, descriptionText: "${device.getName()} thermostatMode is now ${modeValueNew}")
    device.sendEvent(name: "thermostatOperatingState", value: operatingModeValueNew, descriptionText: "${device.getName()} thermostatOperatingState is now ${modeValueNew}")
    
    def updateItems = ["mode", "fanSpeed", "setPoint", "roomTemp_oC", "isInESP_Mode", "fanIsCont", "compressorActivity", "errorCode"]
    updateItems.each {
        device.sendEvent(name: it, value: data.jsonState.get(it), descriptionText:"${it} is now ${data.jsonState.get(it)}")
    }
    

    updateZonesEnabled(data.jsonState.enabledZones)
    updateZonesTemp(data.jsonState.individualZoneTemperatures_oC)
    state.rxDelay = "False"
}

def updateZone(index, temp, enabled) {
    def onOffMap = getOnOffLabelMap()
    def cd = getChildDevice("${device.getDeviceNetworkId()}_${index}")
    if (cd) {
        cd.parse([[name:"switch", value: onOffMap[enabled], descriptionText:"${cd.displayName} was turned ${onOffMap[enabled]}"]])
        cd.parse([[name:"temperature", value: temp, descriptionText:"${cd.displayName} temperature is ${temp}"]])
    }
    else{
        logIt("updateZone", "Unable to find zone device ${device.getDeviceNetworkId()}_${index}", "info")
    }
}

/*
 * Wrapper for updateZonesEnabled, that can be called via runIn with a Map passed as data
 */

void updateZonesEnabledWrapper(data) {
    logIt("updateZonesEnabledWrapper", "Updating enabled zones: ${data.json.enabledZones}", "debug")
    updateZonesEnabled(data.json.enabledZones)
}

/*
 * Called from updateZonesEnabledWrapper() when a websocket msgType 4 is received
 * Called from parse() when a websocket msgType 5 is received 
 * Called from updateCurrentState() when a websocket msgType 6 is received
 */
void updateZonesEnabled(data) {
    def onOffMap = getOnOffLabelMap()
    def zoneIndex = 0
    while (zoneIndex < state.zoneCount.toInteger()) {
        def cd = getChildDevice("${device.getDeviceNetworkId()}_${zoneIndex}")
        if (cd) {
            cd.parse([[name:"switch", value: onOffMap[data[zoneIndex]], descriptionText:"${cd.label} was turned ${onOffMap[data[zoneIndex]]}"]])
        }
        else{
            logIt("updateZonesEnabled", "Unable to find zone device ${device.getDeviceNetworkId()}_${zoneIndex}", "info")
        }
        zoneIndex += 1
    }
}

def updateZonesTemp(data) {
    def zoneIndex = 0
    while (zoneIndex < state.zoneCount.toInteger()) {
        def cd = getChildDevice("${device.getDeviceNetworkId()}_${zoneIndex}")
        if (cd) {
            def updatedTemp = reportedTemp(data[zoneIndex])
            cd.parse([[name:"temperature", value: updatedTemp, descriptionText:"${cd.label} temperature is ${updatedTemp}"]])
        }
        else{
            logIt("updateZonesTemp", "Unable to find zone device ${device.getDeviceNetworkId()}_${zoneIndex}", "info")
        }
        zoneIndex += 1
    }
}

def setHeatingSetpoint(num) {
    logIt("setHeatingSetpoint", "${num}", "info")
}

def setThermostatMode(String mode) {
    logIt("setThermostatMode", "Request to set thermostat mode to: ${mode}", "debug")

    device.sendEvent(name: "thermostatMode", value: mode, descriptionText: "${device.getName()} thermostatMode is now ${mode}")
    updateThermostatMode(mode)
}

def updateThermostatMode(String mode) {
    if (mode == "off") {
        off()
    } 
    else {
        state.rxDelay = "True"
        if (device.currentValue('switch') != "on") {
            on()
        }
        def modeMap = getModeMapFromHe()
        def modeValue = modeMap[String.valueOf(mode)]
        logIt("updateThermostatMode", "Updating API with mode: ${modeValue}", "debug")
        parent?.updateSettingsAPI([amOn: true, tempTarget: device.currentValue('thermostatSetpoint'), fanSpeed: device.currentValue('fanSpeed'), mode: modeValue])
    }
}

//*****************************************************************************
// Child Zone Component Methods
//
// These methods are invoked from the child components.
//*****************************************************************************

void componentZoneOn(cd) {
    state.rxDelay = "True"
    logIt("componentZoneOn", "received on request from ${cd.label}", "info")
    getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"on", descriptionText:"${cd.displayName} was turned on"]])
    index = getChildDevice(cd.deviceNetworkId).currentValue("index")
    state.enabledZones[index.toInteger()] = 1
    parent?.updateEnabledZones(state.enabledZones)
    
}

void componentZoneOff(cd) {
    state.rxDelay = "True"
    logIt("componentZoneOff", "received off request from ${cd.label}", "info")
    getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"off", descriptionText:"${cd.displayName} was turned off"]])
    index = getChildDevice(cd.deviceNetworkId).currentValue("index")
    state.enabledZones[index.toInteger()] = 0
    parent?.updateEnabledZones(state.enabledZones)
}

void componentPoll(cd) {
    state.rxDelay = "False"
    logIt("componentPoll", "received a poll request from ${cd.label}", "info")
    parent.pollSystemStatus()
}
