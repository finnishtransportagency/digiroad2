*** Settings ***
Documentation           Suite description
Library                 SeleniumLibrary
Variables  ../Variable/Variables.py

*** Variables ***
${BROWSER}      phantomjs

*** Test Cases ***
Validate if exist checkbox to show related traffic signs on the left box
   [Setup]
        Open Staging webpage by Chrome
        Go to a specific Asset      nav-pedestrianCrossings
        Page Should Contain Checkbox    id=trafficSignsCheckbox
        Page Should Contain    Näytä liikennemerkit
   [Teardown]  Close Browser


*** Keywords ***
Open Staging webpage by Chrome
    Open Browser        ${ENV_URL}   ${BROWSER}
    Input Text    username    ${USERNAME}
    Input Password    id=password    ${PASSWORD}
    Click Button    class=submit
    Sleep    5s

Go to a specific Asset
    [Arguments]    ${asset_id}
    Click Element    //*[contains(text(),'Valitse tietolaji')]
    Click Element    id=${asset_id}
