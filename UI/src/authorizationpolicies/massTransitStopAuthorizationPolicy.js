(function(root) {
  root.MassTransitStopAuthorizationPolicy = function() {
    AuthorizationPolicy.call(this);

    var me = this;

    this.editModeAccess = function() {
      return me.isUser('busStopMaintainer') || me.isUser('operator');
    };
  };
})(this);