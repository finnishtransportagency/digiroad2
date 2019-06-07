*** Settings ***
Documentation           Suite description
Library                 SeleniumLibrary
Resource                ../robotKeywords/Keywords.robot

*** Test Cases ***
Validate if exist checkbox to show related traffic signs on the left box
   [Setup]
        Open Staging webpage by Chrome
        Go to a specific Asset      nav-pedestrianCrossings
        Page Should Contain Checkbox    id=trafficSignsCheckbox
        Page Should Contain    Näytä liikennemerkit
   [Teardown]  Close Browser



