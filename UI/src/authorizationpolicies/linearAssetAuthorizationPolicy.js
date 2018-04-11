(function(root) {
  root.LinearAssetAuthorizationPolicy = function() {
    AuthorizationPolicy.call(this);

    var me = this;

    this.formEditModeAccess = function(selectedAsset) {
      return (me.isMunicipalityMaintainer() && (selectedAsset.administrativeClass != "State" || selectedAsset.administrativeClass != 1) && me.hasRightsInMunicipality(selectedAsset.municipalityCode)) || (me.isElyMaintainer() && me.hasRightsInMunicipality(selectedAsset.municipalityCode)) || me.isOperator();
    };


  };
})(this);