(function(root) {
  root.LinkPropertyMarker = function(data) {
    var me = this;

    var drawMarkers = function(roadLinks) {
      var floatingRoadMarkers = _.filter(roadLinks, function(roadlink) {
          return roadlink.roadLinkType === -1;
        });
      var features = _.map(floatingRoadMarkers, function(floatlink) {
        return createMarker(floatlink);
      });
      return features;
    };

    var createMarker = function(roadlink) {
      var middlePoint = calculateMiddlePoint(roadlink);
      var bounds = getBounds(middlePoint.x, middlePoint.y);
      var box = new OpenLayers.Marker.Box(bounds, "ffffff00", 0);
      box.id = roadlink.id;
      configureMarkerDiv(box, roadlink.id);
      renderDefaultState(box, roadlink);
      return box;
    };

    var getBounds = function(lon, lat) {
      return OpenLayers.Bounds.fromArray([lon, lat, lon, lat]);
    };

    var configureMarkerDiv = function(box, id){
      $(box.div).css('overflow', 'visible !important')
      .attr('roadlink-marker-id', id);
    };

    var calculateMiddlePoint = function(link){
      var points = _.map(link.points, function(point) {
        return new OpenLayers.Geometry.Point(point.x, point.y);
      });
      var lineString = new OpenLayers.Geometry.LineString(points);
      var middlePoint = GeometryUtils.calculateMidpointOfLineString(lineString);
      return middlePoint;
    };

    var renderDefaultState = function(box, roadlink) {
      var defaultMarker = $('<div class="bus-basic-marker root" />')
        .append($('<div class="images" />').append(floatingImage()));
      $(box.div).html(defaultMarker);
      $(box.div).removeClass('selected-asset');
      $(box.div).css("-webkit-transform", "translate(0px,0px)")
        .css("transform", "translate(0px,0px)");
    };

    var floatingImage = function() {
      return '<img src="images/link-properties/flag-floating.svg">';
    };

    return {
      drawMarkers: drawMarkers,
      createMarker: createMarker
    };
  };
}(this));
