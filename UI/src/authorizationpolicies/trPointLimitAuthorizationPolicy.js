(function(root) {
  root.TrPointLimitAuthorizationPolicy = function() {
    AuthorizationPolicy.call(this);

    var me = this;

    this.formEditModeAccess = function() {
      return true;
    };
  };
})(this);