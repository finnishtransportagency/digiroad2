*** Settings ***
Documentation           Suite description
Library                 SeleniumLibrary

*** Test Cases ***
Validate if exist checkbox to show related traffic signs on the left box
   [Setup]
        Open Browser        url='https://devtest.liikennevirasto.fi/digiroad/'   browser=chrome
        Input Text    id=username    'k215271'
        Input Password    id=password    'di9LNKRH996'
        Click Button    class=submit
        Sleep    5s
        Click Element    //*[contains(text(),'Valitse tietolaji')]
        Click Element    id=nav-pedestrianCrossings
        Page Should Contain Checkbox    id=trafficSignsCheckbox
        Page Should Contain    Näytä liikennemerkit
   [Teardown]  Close Browser



