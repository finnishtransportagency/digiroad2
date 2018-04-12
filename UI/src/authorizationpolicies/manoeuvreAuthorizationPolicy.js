(function(root) {
  root.ManoeuvreAuthorizationPolicy = function() {
    AuthorizationPolicy.call(this);

    var me = this;

    this.formEditModeAccess = function(selectedAsset) {
      return (me.isMunicipalityMaintainer() && selectedAsset.get().administrativeClass != "State" && me.hasRightsInMunicipality(selectedAsset.get().municipalityCode)) || (me.isElyMaintainer() && me.hasRightsInMunicipality(selectedAsset.get().municipalityCode)) || me.isOperator();
    };

    this.editModeAccessByLink = function(link) {
      return (me.isMunicipalityMaintainer() && link.administrativeClass != "State" && me.hasRightsInMunicipality(link.municipalityCode)) || (me.isElyMaintainer() && me.hasRightsInMunicipality(link.municipalityCode)) || me.isOperator();
    };


  };
})(this);