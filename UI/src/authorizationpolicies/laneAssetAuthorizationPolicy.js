(function(root) {
  root.LaneAssetAuthorizationPolicy = function() {
    AuthorizationPolicy.call(this);

    var me = this;

    this.formEditModeAccess = function(selectedAsset) {
      
      return  me.isStateExclusions(selectedAsset) ||  ( !me.isState(selectedAsset) && me.isLaneMaintainer() || me.isOperator() );
    };

    this.editModeAccess = function() {
      return (!me.isUser('viewer') && me.isLaneMaintainer() || me.isOperator());
    };
    
    this.handleSuggestedAsset = function(selectedAsset) {
      return (_.isNull(selectedAsset.getId()) && me.isOperator()) || (selectedAsset.isSuggested() && (me.isOperator() || me.isMunicipalityMaintainer()));
    };
  };
})(this);