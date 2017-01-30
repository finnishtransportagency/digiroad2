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
          src: '../images/link-properties/flag-floating-plus-stick.png',
          anchor: [0, 1]
        })
      });

      var boxStyleUnknown = new ol.style.Style({
        image: new ol.style.Icon({
          src: "images/speed-limits/unknown.svg"
        })
      });

      if(roadlink.roadLinkType==-1){
        box.setStyle(boxStyleFloat);

      } else {
        box.setStyle(boxStyleUnknown);
      }
      box.id = roadlink.linkId;
      configureMarkerDiv(box, roadlink.linkId);
      renderDefaultState(box, roadlink);
      box.roadLinkData = roadlink;
      return box;
    };

    var getBounds = function(lon, lat) {
      return [lon, lat, lat, lon];
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
