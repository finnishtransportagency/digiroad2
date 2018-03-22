(function(root) {
  root.StateRoadAuthorizationPolicy = function() {
    AuthorizationPolicy.call(this);

    var me = this;

    this.formEditModeAccess = function(selectedAsset) {
      return selectedAsset.administrativeClass === 1;
    };
  };
})(this);