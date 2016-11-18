(function(root) {
  root.LinkPropertyMarker = function(data) {
    var me = this;

    this.drawMarkers = function(roadLinks) {
      var floatingRoadMarkers = _.filter(roadLinks, function(roadlink) {
          return roadlink.roadLinkType === -1;
        });
      var features = _.map(floatingRoadMarkers, function(floatlink) {
        var marker = createMarker(floatlink);
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
      // var filteredGroup = filterByValidityPeriod(data.group.assetGroup);
      // var groupIndex = findGroupIndexForAsset(filteredGroup, data);
      var defaultMarker = $('<div class="bus-basic-marker" />')
      //.addClass(data.floating ? 'floating' : '')
        .append($('<div class="images" />').append(floatingImage()));
        // .addClass(groupIndex === 0 && 'root');
      $(box.div).html(defaultMarker);
      $(box.div).removeClass('selected-asset');
      setYPositionForAssetOnGroup(box, roadlink);
    };

    var floatingImage = function() {
        // return '<img src="images/link-properties/flag-floating.svg">';
      return '<img src="images/mass-transit-stops/1.png">';
    };

    var setYPositionForAssetOnGroup = function(box, roadlink) {
      // var yPositionInGroup = -1 * calculateOffsetForMarker(roadlink);
      $(box.div).css("-webkit-transform", "translate(0px," + 20 + "px)")
        .css("transform", "translate(0px," + 20 + "px)");
    };

    var calculateOffsetForMarker = function(dataForCalculation) {
      //var filteredGroup = filterByValidityPeriod(dataForCalculation.group.assetGroup);
      var groupIndex = findGroupIndexForAsset(filteredGroup, dataForCalculation);

      return _.chain(filteredGroup)
        .take(groupIndex)
        .map(function(x) { return x.stopTypes.length * 17/*IMAGE_HEIGHT*/; })
        .map(function(x) { return 8 /*GROUP_ASSET_PADDING*/ + x; })
        .reduce(function(acc, x) { return acc + x; }, 0)
        .value();
    };

  };
}(this));
