(function(root) {
  root.InfoPopupAuthorizationPolicy = function() {
    AuthorizationPolicy.call(this);

    var me = this;

    this.editModeAccess = function() {
      return (_.contains(me.userRoles, 'operator') || _.contains(me.userRoles, 'busStopMaintainer'));
    };
  };
})(this);