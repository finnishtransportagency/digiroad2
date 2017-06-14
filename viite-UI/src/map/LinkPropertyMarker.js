(function(root) {
  root.LinkPropertyMarker = function(data) {
    var me = this;

    var createMarker = function(roadlink) {
      var middlePoint = calculateMiddlePoint(roadlink);
      var bounds = getBounds(middlePoint.x, middlePoint.y);
      var box = new ol.Feature({
        geometry: new ol.geom.Point([middlePoint.x, middlePoint.y])
      });

      var boxStyleFloat = new ol.style.Style({
        image: new ol.style.Icon({
          src: 'images/link-properties/flag-floating-plus-stick.png',
          anchor: [0, 1]
        }),
        zIndex: 10
      });

      var boxStyleUnknown = new ol.style.Style({
        image: new ol.style.Icon({
          src: "images/speed-limits/unknown.svg"
        }),
        zIndex: 10
      });

      // var boxStyleTest = new ol.style.Style({
      //   image: new ol.style.Icon({
      //     src: "images/link-properties/arrow-drop-red.svg"
      //     // rotation: angle * Math.PI / 180
      //   }),
      //   zIndex: 10
      // });

      var boxStyleDirectional = function(rl) {
        if(rl.roadClass == 1){
          return new ol.style.Style({
            image: new ol.style.Icon({
              src: "images/link-properties/arrow-drop-red.svg"
            }),
            zIndex: 10
          });
        } else if (rl.roadClass == 2){
          return new ol.style.Style({
            image: new ol.style.Icon({
              src: "images/link-properties/arrow-drop-pink.svg"
            }),
            zIndex: 10
          });
        } else if (rl.roadClass == 3){
          return new ol.style.Style({
            image: new ol.style.Icon({
              src: "images/link-properties/arrow-drop-pink.svg"
            }),
            zIndex: 10
          });
        } else if (rl.roadClass == 4){
          return new ol.style.Style({
            image: new ol.style.Icon({
              src: "images/link-properties/arrow-drop-blue.svg"
            }),
            zIndex: 10
          });
        } else if (rl.roadClass == 5){
          return new ol.style.Style({
            image: new ol.style.Icon({
              src: "images/link-properties/arrow-drop-cyan.svg"
            }),
            zIndex: 10
          });
        } else if (rl.roadClass == 6){
          return new ol.style.Style({
            image: new ol.style.Icon({
              src: "images/link-properties/arrow-drop-pink.svg"
            }),
            zIndex: 10
          });
        } else if (rl.roadClass == 7){
          return new ol.style.Style({
            image: new ol.style.Icon({
              src: "images/link-properties/arrow-drop-grey.svg"
            }),
            zIndex: 10
          });
        } else if (rl.roadClass == 8){
          return new ol.style.Style({
            image: new ol.style.Icon({
              src: "images/link-properties/arrow-drop-pink.svg"
            }),
            zIndex: 10
          });
        } else if (rl.roadClass == 9){
          return new ol.style.Style({
            image: new ol.style.Icon({
              src: "images/link-properties/arrow-drop-pink.svg"
            }),
            zIndex: 10
          });
        } else if (rl.roadClass == 10){
          return new ol.style.Style({
            image: new ol.style.Icon({
              src: "images/link-properties/arrow-drop-pink.svg"
            }),
            zIndex: 10
          });
        } else if (rl.roadClass == 11){
          return new ol.style.Style({
            image: new ol.style.Icon({
              src: "images/link-properties/arrow-drop-grey.svg"
            }),
            zIndex: 10
          });
        } else {
          return new ol.style.Style({
            image: new ol.style.Icon({
              src: "images/link-properties/arrow-drop-grey.svg"
            }),
            zIndex: 10
          });
        }
      };

      if(roadlink.roadLinkType==-1){
        box.setStyle(boxStyleFloat);
      } else if(roadlink.id===0 && roadlink.roadLinkType === 0){
        box.setStyle(boxStyleUnknown);
      } else {
        box.setStyle(boxStyleDirectional(roadlink));
      }

      box.id = roadlink.linkId;
      box.roadLinkData = roadlink;
      return box;
    };

    var getBounds = function(lon, lat) {
      return [lon, lat, lat, lon];
    };

    var calculateMiddlePoint = function(link){
      var points = _.map(link.points, function(point) {
        return [point.x, point.y];
      });
      var lineString = new ol.geom.LineString(points);
      var middlePoint = GeometryUtils.calculateMidpointOfLineString(lineString);
      return middlePoint;
    };

    return {
      createMarker: createMarker
    };
  };
}(this));
