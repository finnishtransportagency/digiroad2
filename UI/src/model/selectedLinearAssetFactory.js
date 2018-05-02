(function(root) {
  root.SelectedLinearAssetFactory = {
    construct: construct
  };

  function constructValidator(layerName) {
    var validators = {
      prohibition: function() { return true; },
      hazardousMaterialTransportProhibition: function() { return true; },
      europeanRoads: function() { return true; },
      exitNumbers: function() { return true; },
      maintenanceRoad: function() { return true; },
      default: function(val) {
        if(_.isUndefined(val)) { return true; }
        else if(val > 0) { return true; }
      }
    };
    return validators[layerName] || validators.default;
  }

  function construct(backend, collection, asset) {
    return new SelectedLinearAsset(
      backend,
      collection,
      asset.typeId,
      asset.singleElementEventCategory,
      asset.multiElementEventCategory,
      asset.isSeparable,
      constructValidator(asset.layerName));
  }
})(this);