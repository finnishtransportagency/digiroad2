package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.user.Configuration

object MapConfigJson {
  def mapConfig(userConfig: Configuration, eastOpt: Option[Double], northOpt: Option[Double], zoomOpt: Option[Int]): String = {
    val zoom = zoomOpt.getOrElse(2)
    val east = eastOpt.getOrElse(390000)
    val north = northOpt.getOrElse(6900000)

    s"""{
      "mapfull": {
        "state": {
          "selectedLayers": [{
            "id": "base_35"
          }],
          "srs": "EPSG:3067",
          "zoom": ${zoom},
          "east": "${east}",
          "north": "${north}"
        },
        "conf": {
          "mapOptions": {
            "srsName": "EPSG:3067",
            "maxExtent": {
              "bottom": 6291456,
              "left": -548576,
              "right": 1548576,
              "top": 8388608
            },
            "resolutions": [
              2048,
              1024,
              512,
              256,
              128,
              64,
              32,
              16,
              8,
              4,
              2,
              1,
              0.5
            ]
          },
          "size": {
            "width": "100%",
            "height": "100%"
          },
          "globalMapAjaxUrl": "api/layers?",
          "plugins": [{
            "id": "Oskari.mapframework.bundle.mapmodule.plugin.LayersPlugin"
          }, {
            "id": "Oskari.mapframework.mapmodule.ControlsPlugin",
              "config" : {
                "keyboardControls" : false
              }
          }, {
            "id": "Oskari.mapframework.bundle.mapmodule.plugin.ScaleBarPlugin"
          }],
          "layers": [],
          "imageLocation": "./bower_components/oskari.org/resources",
          "user": {
            "lastName": "",
            "nickName": "",
            "userUUID": "",
            "firstName": "",
            "loginName": ""
          }
        }
      },
      "openlayers-default-theme": {
        "state": {

        },
        "conf": {

        }
      },
      "divmanazer": {
        "state": {

        },
        "conf": {

        }
      },
      "statehandler": {
        "state": {

        },
        "conf": {

        }
      },
      "coordinatedisplay": {
        "state": {

        },
        "conf" : {
          "location" : {
            "classes" : "left bottom"
          }
        }
      }
    }"""
  }
}