(function(root) {
  root.ServicePointAuthorizationPolicy = function() {
    AuthorizationPolicy.call(this);

    var me = this;

    this.formEditModeAccess = function(selectedAsset, roadCollection) {
      var nearestLine = findNearestLine(selectedAsset, roadCollection);
      var municipalityCode = selectedAsset.getMunicipalityCodeByLinkId(nearestLine.linkId);
      return (me.isMunicipalityMaintainer() && selectedAsset.getAdministrativeClass(nearestLine.linkId) != "State" && me.hasRightsInMunicipality(municipalityCode)) ||(me.isElyMaintainer() && me.hasRightsInMunicipality(municipalityCode)) || me.isOperator();
    };

    var findNearestLine = function(selectedAsset, roadCollection){

      var excludeRoadByAdminClass = function(roadCollection){
        return _.filter(roadCollection, function (roads) {
          return me.filterRoadLinks(roads);
        });
      };

      return geometrycalculator.findNearestLine(excludeRoadByAdminClass(roadCollection.getRoadsForPointAssets()), selectedAsset.getCoordinates().lon, selectedAsset.getCoordinates().lat);
    };



  };
})(this);