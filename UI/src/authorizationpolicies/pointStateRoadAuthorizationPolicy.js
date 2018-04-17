(function(root) {
  root.PointStateRoadAuthorizationPolicy = function() {
    AuthorizationPolicy.call(this);

    var me = this;

    this.formEditModeAccess = function(selectedAsset) {
      return selectedAsset.administrativeClass != "State" && ((me.isMunicipalityMaintainer() || me.isElyMaintainer() && me.hasRightsInMunicipality(selectedAsset.municipalityCode)) || me.isOperator());
    };

  };
})(this);