(function(root) {
  root.AuthorizationPolicy = function() {
    var me = this;
    this.userRoles = [];

    eventbus.on('roles:fetched', function(roles) {
      me.userRoles = roles;
    });

    this.isUser = function(role) {
      return _.contains(me.userRoles, role);
    };

    this.getRoles = function() {
      return me.userRoles;
    };

    this.editModeAccess = function() {
      return me.isUser('operator') || me.isUser('premium');
    };

    this.editModeTool = function(toolType, asset, roadLink) {};

    this.formEditModeAccess = function() {
      return me.isUser('operator');
    };

    this.workListAccess = function(){
      return me.isUser('operator') || me.isUser('premium');
    };

  };
})(this);