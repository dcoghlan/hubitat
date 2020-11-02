/**
 *  ****************  Actron Connect Integration Parent****************
 *
 *  Design Usage:
 *  Parent app to facilitate the installation of the Actron Connect Service Manager child app.
 *
 *-------------------------------------------------------------------------------------------------------------------
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

def setVersion(){
    state.name = "Actron Connect Integration"
	state.version = "1.0.0"
}

definition(
    name:"Actron Connect Integration",
    namespace: "dcoghlan",
    author: "Dale Coghlan",
    description: "Connect to the Actron Connect cloud service to control your Actron Air Conditioner",
    category: "Integrations",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
     page name: "mainPage", title: "", install: true, uninstall: true
} 

def installed() {
    log.debug "Installed with settings: ${settings}"
    initialize()
}

def updated() {
    log.debug "Updated with settings: ${settings}"
    unsubscribe()
    initialize()
}

def initialize() {
    log.info "There are ${childApps.size()} child apps"
    childApps.each {child ->
    log.info "Child app: ${child.label}"
    }
}

def mainPage() {
    dynamicPage(name: "mainPage") {
    	installCheck()
		if(state.appInstalled == 'COMPLETE'){
			section("Instructions:", hideable: true, hidden: true) {
				paragraph "To connect to the Actron Connect cloud service, install the Actron Connect Service Manager child app."
			}
  			section(getFormat("header-blue", " Child Apps")) {
				app(name: "anyOpenApp", appName: "Actron Connect Service Manager", namespace: "dcoghlan", title: "<b>Add a new 'Actron Connect Service Manager' child</b>", multiple: true)
			}
            
			section(getFormat("header-blue", " General")) {
       			label title: "Enter a name for parent app (optional)", required: false
 			}
		}
		display2()
	}
}

def installCheck(){   
    display()
	state.appInstalled = app.getInstallationState() 
	if(state.appInstalled != 'COMPLETE'){
		section{paragraph "Please hit <b>'Done'</b> to install '${app.label}' parent app "}
  	}
  	else{
    	log.info "Parent Installed OK"
  	}
}

def getImage(type) {					// Modified from @Stephack & @bptworld code
    def loc = "<img src=https://github.com/dcoghlan/hubitat/raw/main/Actron_Connect_Integration/resources/images/"
    if(type == "logo") return "${loc}header-logo.png height=60>"
}

def getFormat(type, myText="") {			// Modified from @Stephack & @bptworld code  
	if(type == "header-blue") return "<div style='color:#ffffff;font-weight: bold;background-color:#123262;border: 1px solid;box-shadow: 2px 3px #A9A9A9'>${myText}</div>"
    if(type == "line") return "<hr style='background-color:#123262; height: 1px; border: 0;'>"
    if(type == "title") return "<h2 style='color:#123262;font-weight: bold'>${myText}</h2>"
}

def display() {
    setVersion()
    theName = app.label
    if(theName == null || theName == "") theName = "New Child App"
    section (getFormat("title", "${getImage("logo")}" + "<br>" + "${state.name} - v${state.version}")) {
		paragraph getFormat("line")
	}
}

def display2() {
	section() {
		paragraph getFormat("line")
	}       
}