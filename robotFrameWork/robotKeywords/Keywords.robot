*** Settings ***
Variables  ../Variable/Variables.py

*** Keywords ***
Open Staging webpage by Chrome
    Open Browser        url=${STAGING_URL}   browser=chrome
    Maximize Browser Window
    Input Text    id=username    ${USERNAME}
    Input Password    id=password    ${PASSWORD}
    Click Button    class=submit
    Sleep    5s

Go to a specific Asset
    [Arguments]    ${asset_id}
    Click Element    //*[contains(text(),'Valitse tietolaji')]
    Click Element    id=${asset_id}
