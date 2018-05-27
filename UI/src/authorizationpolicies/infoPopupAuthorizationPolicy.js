(function(root) {
  root.InfoPopupAuthorizationPolicy = function() {
    AuthorizationPolicy.call(this);

    var me = this;

    this.editModeAccess = function() {
      return me.isOperator() || me.isElyMaintainer();
    };
  };
})(this);