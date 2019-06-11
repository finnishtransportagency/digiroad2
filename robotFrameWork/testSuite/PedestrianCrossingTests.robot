*** Settings ***
Documentation           Suite description
Library                 SeleniumLibrary

*** Variables ***
${STAGING_URL}  https://devtest.liikennevirasto.fi/digiroad/
${QA_URL}  https://testiextranet.liikennevirasto.fi/digiroad/
${BROWSER}      chrome
${USERNAME}     k215271
${PASSWORD}     di9LNKRH996

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
    Open Browser        ${STAGING_URL}   ${BROWSER}
    Input Text    username    k215271
    Input Password    id=password    di9LNKRH996
    Click Button    class=submit
    Sleep    5s

Go to a specific Asset
    [Arguments]    ${asset_id}
    Click Element    //*[contains(text(),'Valitse tietolaji')]
    Click Element    id=${asset_id}
