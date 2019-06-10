*** Settings ***
Documentation           Suite description
Library                 SeleniumLibrary

*** Variables ***
${STAGING_URL}  https://devtest.liikennevirasto.fi/digiroad/
${BROWSER}      Chrome
${USERNAME}     k215271
${PASSWORD}     di9LNKRH996

*** Test Cases ***
Validate if exist checkbox to show related traffic signs on the left box
   [Setup]
        Open Staging webpage by Chrome
   [Teardown]  Close Browser


*** Keywords ***
Open Staging webpage by Chrome
    Open Browser        ${STAGING_URL}   ${BROWSER}
    Input Text      id=username     ${USERNAME}
    Input Password    password    ${PASSWORD}
    Click Button    class=submit
    Sleep    5s