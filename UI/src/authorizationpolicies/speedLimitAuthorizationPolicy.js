(function(root) {
  root.SpeedLimitAuthorizationPolicy = function() {
    AuthorizationPolicy.call(this);

    var me = this;

    this.formEditModeAccess = function(selectedAsset) {
      return selectedAsset.administrativeClass !== 1;
    };
  };
})(this);