(function(root) {
  root.AuthorizationPolicy = function() {
    var me = this;
    me.userRoles = [];
    me.municipalities = [];
    me.areas = [];
    me.username = {};

    eventbus.on('roles:fetched', function(userInfo) {
      me.username = userInfo.username;
      me.userRoles = userInfo.roles;
      me.municipalities = userInfo.municipalities;
      me.areas = userInfo.areas;
    });

    this.isUser = function(role) {
      return _.includes(me.userRoles, role);
    };

    this.isOnlyUser = function(role) {
      return _.includes(me.userRoles, role) && me.userRoles.length === 1;
    };

    this.isMunicipalityMaintainer = function(){
      return _.isEmpty(me.userRoles) || me.isOnlyUser('premium');
    };

    this.isElyMaintainer = function(){
      return me.isUser('busStopMaintainer');
    };

    this.isOperator = function(){
      return me.isUser('operator');
    };

    this.isServiceRoadMaintainer = function(){
      return me.isUser('serviceRoadMaintainer');
    };

    this.hasRightsInMunicipality = function(municipalityCode){
      return _.includes(me.municipalities, municipalityCode);
    };

    this.hasRightsInArea = function(area){
      return _.includes(me.areas, area);
    };

    this.filterRoadLinks = function(roadLink){
      return (me.isMunicipalityMaintainer() && roadLink.administrativeClass != 'State' && me.hasRightsInMunicipality(roadLink.municipalityCode)) || (me.isElyMaintainer() && me.hasRightsInMunicipality(roadLink.municipalityCode)) || me.isOperator();
    };

    this.editModeAccess = function() {
      return (!me.isUser('viewer') && !me.isOnlyUser('serviceRoadMaintainer'));
    };

    this.editModeTool = function(toolType, asset, roadLink) {};

    this.formEditModeAccess = function() {
      return me.isOperator();
    };

    this.workListAccess = function(){
      return me.isOperator();
    };

    this.isState = function(selectedInfo){
      return selectedInfo.administrativeClass === "State" || selectedInfo.administrativeClass === 1;
    };

  };
})(this);