(function(root) {
  root.LinearStateRoadAuthorizationPolicy = function() {
    AuthorizationPolicy.call(this);

    var me = this;

    this.formEditModeAccess = function(selectedAsset) {
      return !me.isState(selectedAsset) && (((me.isMunicipalityMaintainer() || me.isElyMaintainer()) && me.hasRightsInMunicipality(selectedAsset.municipalityCode)) || me.isOperator());
    };

  };
})(this);