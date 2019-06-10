*** Settings ***
Documentation           Suite description
Library                 SeleniumLibrary

*** Test Cases ***
Validate if exist checkbox to show related traffic signs on the left box
   [Setup]
        Open Browser        url=https://devtest.liikennevirasto.fi/digiroad/   browser=chrome
   [Teardown]  Close Browser



