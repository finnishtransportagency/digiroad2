(function(root) {
  root.SelectedLinearAssetFactory = {
    construct: construct
  };

  function constructValidator(layerName) {
    var validators = {
      prohibition: function() { return true; },
      hazardousMaterialTransportProhibition: function() { return true; },
      europeanRoads: euroAndExitValidator,
      exitNumbers: euroAndExitValidator,
      maintenanceRoad: function() { return true; },
      roadDamagedByThaw: function() { return true; },
      massTransitLanes: function() { return true; },
      carryingCapacity: function() { return true; },
      pavedRoad: function() { return true; },
      careClass: function() {return true; },
      default: function(val) {
        if(_.isUndefined(val)) { return true; }
        else if(val > 0) { return true; }
      }
    };
    return validators[layerName] || validators.default;
  }

  function euroAndExitValidator(val) {
    if(!_.isUndefined(val)){
      var values = val.replace(/[ \t\f\v]/g,'').split(/[\n,]+/);
      return _.every(values, function(value){
        return value.match(/^[0-9|Ee][0-9|a-zA-Z]{0,2}$/);
      });
    }
    return true;
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