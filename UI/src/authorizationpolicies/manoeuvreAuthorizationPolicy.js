(function(root) {
  root.ManoeuvreAuthorizationPolicy = function() {
    AuthorizationPolicy.call(this);

    var me = this;

    this.formEditModeAccess = function(selectedAsset) {
      return ((me.isMunicipalityMaintainer() || me.isElyMaintainer()) && me.hasRightsInMunicipality(selectedAsset.get().municipalityCode)) || me.isOperator();
    };

    this.editModeAccessByLink = function(link) {
      return ((me.isMunicipalityMaintainer() || me.isElyMaintainer()) && me.hasRightsInMunicipality(link.municipalityCode)) || me.isOperator();
    };

    this.editModeAccessByFeatures = function(features) {
      return ((me.isMunicipalityMaintainer() || me.isElyMaintainer()) && me.hasRightsInMunicipality(features.getProperties().municipalityCode)) || me.isOperator();
    };

  };
})(this);