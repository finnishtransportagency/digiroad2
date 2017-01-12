(function(root) {
  root.LinkPropertyMarker = function(data) {
    var me = this;

    var createMarker = function(roadlink) {
      var middlePoint = calculateMiddlePoint(roadlink);
      var bounds = getBounds(middlePoint.x, middlePoint.y);
      var box = new OpenLayers.Marker.Box(bounds, "ffffff00", 0);
      box.id = roadlink.linkId;
      configureMarkerDiv(box, roadlink.linkId);
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
      var type = roadlink.roadLinkType === -1 ? true : false;
      var marker;
      if(type){
        marker = $('<div class="bus-basic-marker root" />')
        .append(appendImage(type));
        $(box.div).html(marker);
      } else {
        marker = $('<div class="bus-basic-marker root" />')
        .append(appendImage(type));
        $(box.div).html(appendImage(type));
      }
      $(box.div).removeClass('selected-asset');
      $(box.div).css("-webkit-transform", "translate(0px,0px)")
        .css("transform", "translate(0px,0px)");
    };

    var appendImage = function(floatingType) {
      return (floatingType) ? '<img src="images/link-properties/flag-floating.svg">' : '<img src="images/speed-limits/unknown.svg">';
    };

    return {
      createMarker: createMarker
    };
  };
}(this));
