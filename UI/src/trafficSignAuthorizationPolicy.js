(function(root) {
  root.TrafficSignAuthorizationPolicy = function() {
    AuthorizationPolicy.call(this);

    var me = this;

    this.formEditModeAccess = function(selectedAsset, linkId) {
      return selectedAsset.getAdministrativeClass(linkId) === "State";
    };

  };
})(this);