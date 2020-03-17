(function(root) {
  root.LinearAssetAuthorizationPolicy = function() {
    AuthorizationPolicy.call(this);

    var me = this;

    this.formEditModeAccess = function(selectedAsset) {
      var isMunicipalityMaintainerAndHaveRights = me.isMunicipalityMaintainer() && !me.isState(selectedAsset) && me.hasRightsInMunicipality(selectedAsset.municipalityCode);
      var isElyMaintainerAndHasRights = me.isElyMaintainer() && me.hasRightsInMunicipality(selectedAsset.municipalityCode);

      return  me.isMunicipalityExcluded(selectedAsset) ||  ( isMunicipalityMaintainerAndHaveRights || isElyMaintainerAndHasRights || me.isOperator() );
    };

    this.handleSuggestedAsset = function(selectedAsset) {
      return (_.isNull(selectedAsset.getId()) && me.isOperator()) || (selectedAsset.isSuggested() && (me.isOperator() || me.isMunicipalityMaintainer()));
    };
  };
})(this);