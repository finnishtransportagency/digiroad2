(function(root) {
  root.LinearStateRoadAuthorizationPolicy = function() {
    AuthorizationPolicy.call(this);

    var me = this;

    this.formEditModeAccess = function(selectedAsset) {
      var isMaintainerAndHaveRights = (me.isMunicipalityMaintainer() || me.isElyMaintainer()) && me.hasRightsInMunicipality(selectedAsset.municipalityCode);
      var isMaintainerWithRightsOrOperator = isMaintainerAndHaveRights || me.isOperator();

      return me.isStateExclusions(selectedAsset) || (!me.isState(selectedAsset) && isMaintainerWithRightsOrOperator );
    };

    this.handleSuggestedAsset = function(selectedAsset, value, layerMode) {
      if (layerMode === 'readOnly')
        return !!parseInt(value);
      else
        return !selectedAsset.isSplitOrSeparated() && (_.isNull((selectedAsset.getId()) && me.isOperator()) || (!!parseInt(value) && (me.isOperator() || me.isMunicipalityMaintainer())));
    };
  };
})(this);