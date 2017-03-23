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
          //src: 'images/link-properties/flag-floating-plus-stick.png',
          src: 'images/link-properties/flag-floating.svg',
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

      if(roadlink.roadLinkType==-1){
        box.setStyle(boxStyleFloat);

      } else {
        box.setStyle(boxStyleUnknown);
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
