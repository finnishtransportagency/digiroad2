(function(root) {
  root.ServiceRoadAuthorizationPolicy = function() {
    AuthorizationPolicy.call(this);

    var me = this;

    this.editModeAccess = function() {
      return me.isServiceRoadMaintainer() || me.isOperator();
    };

    this.formEditModeAccess = function(selectedAsset) {
      return (me.isServiceRoadMaintainer() && me.hasRightsInArea(selectedAsset.area)) || me.isUser('operator');
    };

    this.handleSuggestedAsset = function(selectedAsset, value) {
      return (_.isUndefined(_.head(selectedAsset.get()).id) && me.isOperator()) || (!!parseInt(value) && (me.isOperator() || me.isMunicipalityMaintainer()));
    };
  };
})(this);