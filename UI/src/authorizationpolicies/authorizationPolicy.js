(function(root) {
  root.AuthorizationPolicy = function() {
    var me = this;
    this.userRoles = [];
    this.municipalities = [];

    eventbus.on('roles:fetched', function(userInfo) {
      me.userRoles = userInfo.roles;
      me.municipalities = userInfo.municipalities;

    });

    this.isUser = function(role) {
      return _.contains(me.userRoles, role);
    };

    this.isMunicipalityMaintainer = function(){
      return _.isEmpty(me.userRoles);
    };

    this.isElyMaintainer = function(){
      return me.isUser('busStopMaintainer');
    };

    this.isOperator = function(){
      return me.isUser('operator');
    };

    this.hasRightsInMunicipality = function(municipalityCode){
      return _.contains(me.municipalities, municipalityCode);
    };

    this.editModeAccess = function() {
      return !me.isUser('viewer');
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