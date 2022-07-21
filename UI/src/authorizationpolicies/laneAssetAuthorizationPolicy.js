(function(root) {
  root.LaneAssetAuthorizationPolicy = function() {
    AuthorizationPolicy.call(this);

    var me = this;

    this.formEditModeAccess = function(selectedAsset) {
      return  me.isStateExclusions(selectedAsset) ||  ( me.isLaneMaintainer() || me.isOperator() );
    };

    this.editModeAccess = function() {
      return (!me.isUser('viewer') && me.isLaneMaintainer() || me.isOperator());
    };
    
  };
})(this);