(function(root) {
  root.LinkPropertyMarker = function(data) {
    var me = this;

    var createMarker = function(roadlink) {
      var middlePoint = calculateMiddlePoint(roadlink);
      var bounds = getBounds(middlePoint.x, middlePoint.y);
      var box = new ol.Feature({
        geometry: new ol.geom.Point([middlePoint.x, middlePoint.y]),
        linkId : roadlink.linkId,
        type : "marker"
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

      var colorMap = {1:'red', 2:'orange', 3:'orange-light', 4:'blue', 5:'cyan', 6:'purple', 7:'cyan', 8:'pink', 9:'pink', 10:'pink', 11:'grey' };

      var boxStyleDirectional = function(rl) {
        if(rl.status === 2){
          return new ol.style.Style({
            image: new ol.style.Icon({
              rotation: rl.sideCode === 3 ? middlePoint.angleFromNorth * Math.PI / 180 + Math.PI : middlePoint.angleFromNorth * Math.PI / 180,
              src: "images/link-properties/arrow-drop-pink-light.svg"
            }),
            zIndex: 10
          });
        }
        else if(rl.roadLinkSource===3){
          return new ol.style.Style({
            image: new ol.style.Icon({
              rotation: rl.sideCode === 3 ? middlePoint.angleFromNorth * Math.PI / 180 + Math.PI : middlePoint.angleFromNorth * Math.PI / 180,
              src: "images/link-properties/arrow-drop-"+colorMap[6]+".svg"
            }),
            zIndex: 15000
          });
        }
        else if(rl.roadClass in colorMap){
          return new ol.style.Style({
            image: new ol.style.Icon({
              rotation: rl.sideCode === 3 ? middlePoint.angleFromNorth * Math.PI / 180 + Math.PI : middlePoint.angleFromNorth * Math.PI / 180,
              src: "images/link-properties/arrow-drop-"+colorMap[rl.roadClass]+".svg"
            }),
            zIndex: 10
          });
        } else {
          return new ol.style.Style({
            image: new ol.style.Icon({
              rotation: rl.sideCode === 3 ? middlePoint.angleFromNorth * Math.PI / 180 + Math.PI : middlePoint.angleFromNorth * Math.PI / 180,
              src: "images/link-properties/arrow-drop-grey.svg"
            }),
            zIndex: 10
          });
        }
      };

      if(roadlink.roadLinkType==-1){
        box.setStyle(boxStyleFloat);
      } else if(roadlink.roadLinkSource===3){
        box.setStyle(boxStyleDirectional(roadlink));
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
