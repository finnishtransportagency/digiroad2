(function(root) {
  root.AuthorizationPolicy = function() {

    var userRoles;

    eventbus.on('roles:fetched', function(roles) {
      userRoles = roles;
    });

    this.editModeAccess = function() {
      return (_.contains(userRoles, 'operator') || _.contains(userRoles, 'premium'));
    };

    this.editModeAccess = function() {
      return (((_.contains(userRoles, 'busStopMaintainer')) || (_.isEmpty(userRoles)) || (_.contains(userRoles, 'serviceRoadMaintainer'))) &&
          !(_.contains(userRoles, 'operator') || _.contains(userRoles, 'premium')));
    };

    this.editModeTool = function(toolType, asset, roadLink) {};

    this.formEditModeAccess = function(selectedAsset, linkId) {
      return selectedAsset.getAdministrativeClass(linkId) === "State";
    };

    this.formEditModeAccess = function(selectedAsset) {
      return selectedAsset.administrativeClass === 1;
    };

    this.workListAccess = function(){
      return (_.contains(userRoles, 'operator') || _.contains(userRoles, 'premium'));
    };

  };
})(this);