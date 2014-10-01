(function(root) {
  root.ConfigurationTestData = {
    generate: function(zoomLevel) {
      return {
      "mapfull": {
        "state": {
          "selectedLayers": [{
            "id": "base_35"
          }],
            "srs": "EPSG:3067",
            "zoom": zoomLevel || 10,
          "east": "374750.0",
          "north": "6677409.0"
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
    };
    }
  };
}(this));
