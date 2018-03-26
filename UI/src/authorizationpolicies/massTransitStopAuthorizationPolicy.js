(function(root) {
  root.MassTransitStopAuthorizationPolicy = function() {
    AuthorizationPolicy.call(this);

    var me = this;

    this.editModeAccess = function() {
      return (_.contains(me.userRoles, 'busStopMaintainer'));
    };
  };
})(this);