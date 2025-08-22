(function(root) {
  root.AuthorizationPolicy = function() {
    var me = this;
    me.userRoles = [];
    me.municipalities = [];
    me.areas = [];
    me.username = {};
    me.fetched = false;

    eventbus.on('roles:fetched', function(userInfo) {
      me.username = userInfo.username;
      me.userRoles = userInfo.roles;
      me.municipalities = userInfo.municipalities;
      me.areas = userInfo.areas;
      me.fetched = true;
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
      return me.isUser('elyMaintainer');
    };

    this.isOperator = function(){
      return me.isUser('operator');
    };

    this.isLaneMaintainer = function(){
      return me.isUser('laneMaintainer');
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
      var isMunicipalityAndHaveRights = me.isMunicipalityMaintainer() && roadLink.administrativeClass !== 'State' && me.hasRightsInMunicipality(roadLink.municipalityCode);
      var isElyAndHaveRights = me.isElyMaintainer() && roadLink.administrativeClass !== 'Municipality' && me.hasRightsInMunicipality(roadLink.municipalityCode);

      return me.isStateExclusions(roadLink) || isMunicipalityAndHaveRights || isElyAndHaveRights || me.isOperator();
    };

    this.editModeAccess = function() {
      return (!me.isUser('viewer') && !me.isUser('laneMaintainer') && !me.isOnlyUser('serviceRoadMaintainer'));
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

    this.validateMultiple = function(selectedAssets) {
      return _.every(selectedAssets, function(selectedAsset){
        return me.formEditModeAccess(selectedAsset);
      });
    };

    this.handleSuggestedAsset = function(selectedAsset, suggestedBoxValue) {
      return (selectedAsset.isNew() && me.isOperator()) || (suggestedBoxValue && (me.isOperator() || me.isMunicipalityMaintainer()));
    };

    this.isStateExclusions = function (selectedAsset) {
      var statesExcluded = [35,43,60,62,65,76,170,295,318,417,438,478,736,766,771,941];
      var municipalityCode;

      if ( !_.isUndefined(selectedAsset.municipalityCode) )
        municipalityCode = selectedAsset.municipalityCode;
      else if (typeof selectedAsset.getMunicipalityCode == 'function' )
        municipalityCode = selectedAsset.getMunicipalityCode();

      var haveRights =  me.isOperator() || me.hasRightsInMunicipality(municipalityCode);

      return _.includes(statesExcluded, municipalityCode) && haveRights;
    };

  };
})(this);