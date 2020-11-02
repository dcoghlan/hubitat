/**
 *  ****************  Actron Connect Zone  ****************
 *
 *  Design Usage:
 *
 *  Hubitat driver to enable control of an Actron Zone when used with the Actron
 *  Connect module (ACM-1)
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
 *
 */

metadata {
    definition(name: "Actron Connect Zone", namespace: "dcoghlan", author: "Dale Coghlan") {
	    capability "Polling"
	    capability "Switch"
        capability "TemperatureMeasurement"
		
        // store the index number of the zone
        attribute "index", "number"

        command "on"
        command "off"
    }
}

preferences {
    section("URIs") {
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "logEnableTime", type: "enum", description: getPrefDesc("logEnableTime"), title: "<b>Disable debug logging after</b>", options: [[900:"15min"],[1800:"30min"],[3600:"60min"]], defaultValue: 1800
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: settings?.txtEnable
    }
}

def getPrefDesc(type) {
    if(type == "logEnableTime") return "<i>Time after which debug logging will be turned off.</i>"
}

def logsOff() {
    if (logEnable) {
        logIt("logsOff", "debug logging disabled - ${device.getLabel()}", "warn")
        device.updateSetting("logEnable", [value: "false", type: "bool"])
    }
    
}

def logsOn() {
    if (!settings?.logEnable) {
        logIt("logsOn", "debug logging enabled - ${device.getLabel()}", "warn")
        device.updateSetting("logEnable", [value: "true", type: "bool"])
    }
    if (settings?.logEnable) runIn(Long.valueOf(settings?.logEnableTime), logsOff)
}

def txtOff() {
    logIt("txtOff", "descriptionText logging disabled - ${device.getLabel()}", "debug")
    device.updateSetting("txtEnable", [value: "false", type: "bool"])
}

def txtOn() {
    logIt("txtOn", "descriptionText logging enabled - ${device.getLabel()}", "debug")
    device.updateSetting("txtEnable", [value: "true", type: "bool"])
}

def updated() {
    logIt("updated", "updated - ${device.getLabel()}", "info")
    logIt("updated", "debug logging is: ${logEnable == true}", "warn")
    logIt("updated", "description logging is: ${txtEnable == true}", "warn")
    if (logEnable) runIn(Long.valueOf(settings?.logEnableTime), logsOff)
    if (txtEnable) {txtOn()} else {txtOff()}
}

void uninstalled() {
    logIt("uninstalled", "uninstalling ${device.getName()}", "info")
	unschedule()
}

private logIt(method, msg, level="info") {
    def String prefix = "ACZ" // Actron Connect Zone
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

void parse(String description) {
    logIt("parseString", "string parse() not implemented. Msg: ${description}", "error") 
}

void parse(List<Map> description) {
    description.each {
        if (it.name in ["switch","index","temperature"]) {
            if (txtEnable) {
                if (device.currentValue(it.name) != it.value) {
                    logIt("parseList", it.descriptionText, "info")
                }
            }
            sendEvent(it)
        }
        else {
            logIt("parseList", "Unhandled parse msg: ${it}", "error")
        }
    }
}

void on() {
    parent?.componentZoneOn(this.device)
}

void off() {
    parent?.componentZoneOff(this.device)
}

void poll() {
    parent?.componentPoll(this.device)
}