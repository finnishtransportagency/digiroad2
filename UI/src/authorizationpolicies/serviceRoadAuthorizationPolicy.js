(function(root) {
  root.ServiceRoadAuthorizationPolicy = function() {
    AuthorizationPolicy.call(this);

    var me = this;

    this.editModeAccess = function() {
      return me.isServiceRoadMaintainer() || me.isOperator();
    };

    this.formEditModeAccess = function(selectedAsset) {
      return (me.isServiceRoadMaintainer() && me.hasRightsInArea(selectedAsset.area)) || me.isUser('operator');
    };

  };
})(this);