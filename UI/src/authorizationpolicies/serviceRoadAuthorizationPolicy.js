(function(root) {
  root.ServiceRoadAuthorizationPolicy = function() {
    AuthorizationPolicy.call(this);

    var me = this;

    this.editModeAccess = function() {
      return me.isUser('serviceRoadMaintainer') || me.isUser('operator');
    };

    this.formEditModeAccess = function(selectedAsset) {
      return selectedAsset.administrativeClass !== 1;
    };

  };
})(this);