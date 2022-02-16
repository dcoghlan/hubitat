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
    name:"Contact2Motion",
    namespace: "dcoghlan",
    author: "Dale Coghlan",
    description: "Mirrors a contact sensor to a virtual motion sensor.",
    category: "Convenience",

    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
     page name: "mainPage", title: "", install: true, uninstall: true
}

def mainPage() {
    dynamicPage(name: "mainPage") {
        section("") {
            paragraph "Sync contact sensor events to a virtual motion sensor. The virtual motion sensor can then be used in the motion lighting app."
            paragraph "<br><i>motion active -> contact open</i>"
            paragraph "<i>motion inactive -> contact closed</i>"
        }
        section() {
            app(name: "anyOpenApp", appName: "Contact2Motion Child", namespace: "dcoghlan", title: "Create a new '${app.label}' child", multiple: true)
        }
        displayFooter()
	}
}

def installCheck(){   
    display()
	state.appInstalled = app.getInstallationState() 
	if(state.appInstalled != 'COMPLETE'){
		section{paragraph "Please hit <b>'Done'</b> to install '${app.label}' parent app "}
  	}
  	else{
    	logIt("installCheck", "Parent Installed OK", "info")
  	}
}

def getFormat(type, myText="") {			// Modified from @Stephack & @bptworld code  
	if(type == "header-blue") return "<div style='color:#ffffff;font-weight: bold;background-color:#123262;border: 1px solid;box-shadow: 2px 3px #A9A9A9'>${myText}</div>"
    if(type == "line") return "<hr style='background-color:#123262; height: 1px; border: 0;'>"
    if(type == "title") return "<h2 style='color:#BDBDBD'>${myText}</h2>"
    if(type == "version") return "<i><style='color:#BDBDBD'>${myText}</style></i>"
}

def display() {
    setVersion()
}

def displayFooter() {
    setVersion()
	section() {
		paragraph getFormat("version", "${app.label} - v${state.version}")
	}
}

def setVersion(){
    state.name = "Contact2Motion"
	state.version = "1.0.0"
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

// checks for endless looping (max iterations are 4)
def checkReplicasExist(def replicas, def primary, def iterations, Boolean b){
    // check if no children exist for current parent app
    if(childApps.size() == 0){
        for(replica in replicas){
            if(replica == primary){
                logIt("checkReplicasExist", "replica is same as primary", "warn")
                if(iterations <= 0){
                    return true
                }
                else{
                    if(checkReplicasExist(replicas, primary, iterations-1, b)){
                        return true
                    }
                }
            }
        }
    }


    for(child in childApps){
        for(replica in replicas){
            if(child.getPrimaryId() == replica || primary == replica){
                logIt("checkReplicasExist", "primaryChild: ${child.getPrimaryId()}  primary: ${primary}   replica: ${replica}", "debug")
                if(iterations <= 0){
                    return true
                }
                else{s
                    logIt("checkReplicasExist", "Iterations: ${iterations}", "debug")
                    if(checkReplicasExist(replicas, primary, iterations-1, b)){
                        return true
                    }
                }
            }
        }
    }
    return b
}