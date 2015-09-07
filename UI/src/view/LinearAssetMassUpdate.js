(function(root) {
  root.LinearAssetMassUpdate = function(map, layer, selectedLinearAssetModel, showUpdateDialogFunction) {
    var displayConfirmMessage = function() { new Confirm(); };

    var pixelBoundsToCoordinateBounds = function(bounds) {
      var bottomLeft = map.getLonLatFromPixel(new OpenLayers.Pixel(bounds.left, bounds.bottom));
      var topRight = map.getLonLatFromPixel(new OpenLayers.Pixel(bounds.right, bounds.top));
      return new OpenLayers.Bounds(bottomLeft.lon, bottomLeft.lat, topRight.lon, topRight.lat);
    };

    var doneFunction = function(bounds) {
      if (selectedLinearAssetModel.isDirty()) {
        displayConfirmMessage();
      } else {
        var coordinateBounds = pixelBoundsToCoordinateBounds(bounds);
        var selectedLinearAssets = _.chain(layer.features)
          .filter(function(feature) { return coordinateBounds.toGeometry().intersects(feature.geometry);})
          .map(function(feature) { return feature.attributes; })
          .value();
        if (selectedLinearAssets.length > 0) {
          selectedLinearAssetModel.close();
          showUpdateDialogFunction(selectedLinearAssets);
        }
      }
    };

    var boxHandler = new BoxSelectControl(map, doneFunction);

    var activate = function() {
      boxHandler.activate();
    };

    var deactivate = function() {
      boxHandler.deactivate();
    };

    return {
      activate: activate,
      deactivate: deactivate
    };
  };
})(this);
