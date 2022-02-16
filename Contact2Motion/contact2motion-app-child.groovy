/**
 *  *****************************  Contact2Motion  *****************************
 *
 *  Design Usage:
 *
 *  A simple app that translates contact events to a virtual motion sensor.
 *
 *  When you want a contact sensor to be used in a motion lighting rule, this
 *  will mirror the contact status to a virtual motion sensor.
 * 
 * -----------------------------------------------------------------------------
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not 
 *  use this file except in compliance with the License. You may obtain a copy
 *  of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  License for the specific language governing permissions and limitations
 *  under the License.
 *
 * ------------------------------------------------------------------------------------------------------------------------------
 *
 *  The code in this app is based code provided by the following:
 *  https://github.com/bptworld/Hubitat
 *  https://gitlab.com/Phuc.tran95/device-mirror
 *  https://gitlab.com/terrelsa13/device-mirror-plus
 *
 * ------------------------------------------------------------------------------------------------------------------------------
 *
 *  Changes:
 *
 *  1.0.0 - 16th Feb 2022 - Initial release.
 */

definition(
    name: "Contact2Motion Child",
    namespace: "dcoghlan",
    author: "Dale Coghlan",
    description: "Contact2Motion child app",
    category: "Convenience",
    parent: "dcoghlan:Contact2Motion",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
	documentationLink: "https://github.com/dcoghlan/hubitat/blob/main/Contact2Motion/README.md")

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: " ", install: true, uninstall: true) {
        section("Select devices...") {

            primaryDeviceType = "Contact Sensor"
            input "primary", "capability.contactSensor", title: "Select primary ${primaryDeviceType}", submitOnChange: false, required: true, multiple: false

            replicaDeviceType = "Motion Sensor"
            input "replica", "device.VirtualMotionSensor", title: "Select virtual replica ${replicaDeviceType}(s)", submitOnChange: true, required: true, multiple: false

            if (primary && replica) {
                
                paragraph "<font color=\"green\">[contact] events from ${primary.getDisplayName()} will be translated to ${replica.getDisplayName()} as [motion] events...</font>"
                input "initializeOnUpdate", "bool", defaultValue: "false", title: "Update replica devices when clicking Done"
                input(name: "logEnable", type: "bool", title: "Enable Debug Logging?", defaultValue: settings?.logEnable, displayDuringSetup: true, required: false)
                input(name: "logEnableTime", type: "enum", description: "<i>Time after which debug logging will be turned off.</i>", title: "<b>Disable debug logging after</b>", options: [[900:"15min"],[1800:"30min"],[3600:"60min"]], defaultValue: 1800, required: false)

                paragraph "Click <b>Done</b> to finish"
            }
        }
    }
}

def installed() {
    initialize()
}

def updated() {
    unsubscribe()
    initialize()
    logIt("updated", "updated", "info")
}

def initialize() {
    logIt("initialize", "initialize", "info")
    app.updateLabel("${primary} > ${replica}")
    subscribe(primary, "contact", contactHandler) // contact sensor
    // initializes all replica devices to mirror the primary on update
    if(initializeOnUpdate) initialzeReplicaValues()
    if (settings?.logEnable) {
        runIn(Long.valueOf(settings?.logEnableTime), logsOff)
    }
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

private logIt(method, msg, level="info") {
    def String prefix = "contact2motion"
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

def initialzeReplicaValues(){
    attributeName = "contact"
    attributeValue = primary.currentValue(attributeName)
    if (attributeValue == "open") {
        logIt("initialzeReplicaValues", "contact is open > initializing motion to active", "debug")
        replica?.open()
    }
    else if (attributeValue == "closed") {
        logIt("initialzeReplicaValues", "contact is closed > initializing motion to inactive", "debug")
        replica?.close()
    }
    else{
        logIt("initialzeReplicaValues", "Could not initialize the replica Motion Sensor attribute: [${attributeName}] with value: [${attributeValue}]", "debug")
    }
}

def contactHandler(evt){
    logIt("contactHandler", "received contact event: ${evt.value}", "debug")
    try{
        if(evt.value == "open"){
            logIt("contactHandler", "contact is open > setting motion to active", "debug")
            replica?.active()
        }
        else if(evt.value == "closed"){
            logIt("contactHandler", "contact is closed > setting motion to inactive", "debug")
            replica?.inactive()
        }
    }
    catch(IllegalArgumentException ex){
        logIt("contactHandler", "Command is not supported by device", "debug")
    }
}