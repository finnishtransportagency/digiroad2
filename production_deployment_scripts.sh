#!/bin/bash
./fixture-reset.sh mml_masstransitstops -Ddigiroad2.env=$1
./fixture-reset.sh mml_numericallimits -Ddigiroad2.env=$1
./fixture-reset.sh mml_speedlimits -Ddigiroad2.env=$1
./fixture-reset.sh split_speedlimitchains -Ddigiroad2.env=$1
