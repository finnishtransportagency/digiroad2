(function(root) {
  root.AuthorizationPolicy = function() {
    var me = this;
    this.userRoles = [];

    eventbus.on('roles:fetched', function(roles) {
      me.userRoles = roles;
    });

    this.editModeAccess = function() {
      return (_.contains(me.userRoles, 'operator') || _.contains(me.userRoles, 'premium'));
    };

    this.editModeTool = function(toolType, asset, roadLink) {};

    this.formEditModeAccess = function() {
      return false;
    };

    this.workListAccess = function(){
      return (_.contains(me.userRoles, 'operator') || _.contains(me.userRoles, 'premium'));
    };

  };
})(this);