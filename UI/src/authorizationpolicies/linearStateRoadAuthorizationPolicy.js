(function(root) {
  root.LinearStateRoadAuthorizationPolicy = function() {
    AuthorizationPolicy.call(this);

    var me = this;

    this.formEditModeAccess = function(selectedAsset) {
      return !me.isState(selectedAsset) && (((me.isMunicipalityMaintainer() || me.isElyMaintainer()) && me.hasRightsInMunicipality(selectedAsset.municipalityCode)) || me.isOperator());
    };

    this.handleSuggestedAsset = function(selectedAsset, value, layerMode) {
      if (layerMode === 'readOnly')
        return !_.isUndefined(value);
      else
        return selectedAsset.sideCode && ((_.isUndefined(_.head(selectedAsset.get()).id) && me.isOperator()) || (!!parseInt(value) && (me.isOperator() || me.isMunicipalityMaintainer())));
    };

  };
})(this);