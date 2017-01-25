(function(root) {
  root.LinkPropertyMarker = function(data) {
    var me = this;

    var createMarker = function(roadlink) {
      var middlePoint = calculateMiddlePoint(roadlink);
      var bounds = getBounds(middlePoint.x, middlePoint.y);
      //TODO - Review the "box" initialization in order to create a box in OL3 (previously OpenLayers.Marker.Box)
      var box = new ol.Feature(bounds, "ffffff00", 0);
      box.id = roadlink.linkId;
      configureMarkerDiv(box, roadlink.linkId);
      renderDefaultState(box, roadlink);
      return box;
    };

    var getBounds = function(lon, lat) {
      return ol.extent.boundingExtent(ol.proj.toLonLat([lon, lat, lon, lat]));
    };

    var configureMarkerDiv = function(box, id){
      $(box.div).css('overflow', 'visible !important')
      .attr('roadlink-marker-id', id);
    };

    var calculateMiddlePoint = function(link){
      var points = _.map(link.points, function(point) {
        return [point.x, point.y];
      });
      var lineString = new ol.geom.LineString(points);
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
        $(box.div).removeClass('selected-asset');
        $(box.div).css("-webkit-transform", "translate(0px,0px)")
          .css("transform", "translate(0px,0px)");
      } else {
        marker = $('<div class="unknown-basic-marker" />')
        .append(appendImage(type));
        $(box.div).html(marker);
        $(box.div).removeClass('selected-asset');
        $(box.div).css("-webkit-transform", "translate(-15px,-10px)");
      }
    };

    var appendImage = function(floatingType) {
      return (floatingType) ? '<img src="images/link-properties/flag-floating.svg">' : '<img src="images/speed-limits/unknown.svg">';
    };

    return {
      createMarker: createMarker
    };
  };
}(this));
