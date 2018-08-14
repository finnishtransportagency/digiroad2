(function(root) {
  root.PointAssetAuthorizationPolicy = function() {
    AuthorizationPolicy.call(this);

    var me = this;

    this.formEditModeAccess = function(selectedAsset) {
      var municipalityCode = selectedAsset.getMunicipalityCode();
      return (me.isMunicipalityMaintainer() && !me.isState(selectedAsset.get()) && me.hasRightsInMunicipality(municipalityCode)) ||(me.isElyMaintainer() && me.hasRightsInMunicipality(municipalityCode)) || me.isOperator();
    };


  };
})(this);