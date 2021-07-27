(function(root) {
  root.LinearEuropeanRoadsAuthorizationPolicy = function() {
    AuthorizationPolicy.call(this);

    var me = this;

    this.formEditModeAccess = function(selectedAsset) {
      var isMaintainerAndHaveRights = (me.isMunicipalityMaintainer() || me.isElyMaintainer()) && me.hasRightsInMunicipality(selectedAsset.municipalityCode);
      var isMaintainerWithRightsOrOperator = isMaintainerAndHaveRights || me.isOperator();
      //console.log("state exclusion " +me.isStateExclusions(selectedAsset));
      //console.log("first part not isState: "+!me.isState(selectedAsset) +" second part isMaintainerWithRightsOrOperator:  "+ isMaintainerWithRightsOrOperator + " full clause  "+ (!me.isState(selectedAsset) && isMaintainerWithRightsOrOperator ));
      //console.log(me.isStateExclusions(selectedAsset) || (!me.isState(selectedAsset) && isMaintainerWithRightsOrOperator ));
      return isMaintainerWithRightsOrOperator;
      //return me.isStateExclusions(selectedAsset) || (!me.isState(selectedAsset) && isMaintainerWithRightsOrOperator );
      
      
    };

    this.handleSuggestedAsset = function(selectedAsset, value, layerMode) {
      if (layerMode === 'readOnly')
        return !!parseInt(value);
      else
        return !selectedAsset.isSplitOrSeparated() && (_.isNull((selectedAsset.getId()) && me.isOperator()) || (!!parseInt(value) && (me.isOperator() || me.isMunicipalityMaintainer())));
    };
  };
})(this);