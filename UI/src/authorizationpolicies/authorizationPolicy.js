(function(root) {
  root.AuthorizationPolicy = function() {
    var me = this;
    me.userRoles = [];
    me.municipalities = [];
    me.areas = [];

    eventbus.on('roles:fetched', function(userInfo) {
      me.userRoles = userInfo.roles;
      me.municipalities = userInfo.municipalities;
      me.areas = userInfo.areas;
    });

    this.isUser = function(role) {
      return _.contains(me.userRoles, role);
    };

    this.isOnlyUser = function(role) {
      return _.contains(me.userRoles, role) && me.userRoles.length === 1;
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

    this.isServiceRoadMaintainer = function(){
      return me.isUser('serviceRoadMaintainer');
    };

    this.hasRightsInMunicipality = function(municipalityCode){
      return _.contains(me.municipalities, municipalityCode);
    };

    this.hasRightsInArea = function(area){
      return _.contains(me.areas, area);
    };

    this.filterRoadLinks = function(roadLink){
      return (me.isMunicipalityMaintainer() && roadLink.administrativeClass != 'State' && me.hasRightsInMunicipality(roadLink.municipalityCode)) || (me.isElyMaintainer() && me.hasRightsInMunicipality(roadLink.municipalityCode)) || me.isOperator();
    };

    this.editModeAccess = function() {
      return (!me.isUser('viewer') && !me.isOnlyUser('serviceRoadMaintainer'));
    };

    this.editModeTool = function(toolType, asset, roadLink) {};

    this.formEditModeAccess = function() {
      return me.isUser('operator');
    };

    this.workListAccess = function(){
      return me.isUser('operator') || me.isUser('premium');
    };

    this.isState = function(selectedInfo){
      return selectedInfo.administrativeClass === "State" || selectedInfo.administrativeClass === 1;
    };

  };
})(this);