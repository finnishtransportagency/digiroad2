(function(root) {
  root.PointAssetAuthorizationPolicy = function() {
    AuthorizationPolicy.call(this);

    var me = this;

    this.formEditModeAccess = function(selectedAsset) {
      var municipalityCode = selectedAsset.getMunicipalityCode();

      var isMunicipalityMaintainerAndHaveRights = me.isMunicipalityMaintainer() && !me.isState(selectedAsset.get()) && me.hasRightsInMunicipality(municipalityCode);
      var isElyMaintainerAndHasRights = me.isElyMaintainer() && me.hasRightsInMunicipality(municipalityCode);

      return  me.isStateExclusions(selectedAsset) || isMunicipalityMaintainerAndHaveRights || isElyMaintainerAndHasRights || me.isOperator();
    };


  };
})(this);