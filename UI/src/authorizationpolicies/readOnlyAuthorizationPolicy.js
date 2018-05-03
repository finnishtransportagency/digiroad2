(function(root) {
  root.ReadOnlyAuthorizationPolicy = function() {
    AuthorizationPolicy.call(this);

    var me = this;

    this.formEditModeAccess = function() {
      return false;
    };

    this.editModeAccess = function () {
      return false;
    };

   };
})(this);