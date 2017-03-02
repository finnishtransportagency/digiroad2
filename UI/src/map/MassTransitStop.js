(function(root) {
  //TODO this can be removed there is no need anymore
  root.MassTransitStop = function(data, map) {
    var me = this;
    var cachedMassTransitMarker = null;
    var cachedFeature = null;

    var getMarker = function() {
      if (!cachedFeature)
        return createNewMarker();

      return {  feature: cachedFeature };
    };

    var getMarkerSelectionStyles = function(asset, zoom){
      if (!cachedFeature)
        createNewMarker();

      return cachedMassTransitMarker.createSelectionMarkerStyles(asset, zoom);
    };

    var getMarkerDefaultStyles = function(asset, zoom){
      if (!cachedFeature)
        createNewMarker();

      return cachedMassTransitMarker.createDefaultMarkerStyles(asset, zoom);
    };

    var getMarkerFeature = function(){
      if (!cachedFeature)
        createNewMarker();

      return cachedFeature;
    };

    var createNewMarker = function() {
      cachedMassTransitMarker = new MassTransitMarkerStyle(data, map);
      /*
      cachedMarkerStyles = {
        default: cachedMassTransitMarker.createDefaultMarkerStyles(),
        selection: cachedMassTransitMarker.createSelectionMarkerStyles()
      };
      */
      cachedFeature = cachedMassTransitMarker.createFeature();
      return { feature: cachedFeature };
    };
    //TODO maybe there is no need for that
    var getMassTransitMarker = function() {
      return cachedMassTransitMarker;
    };

    //var getDirectionArrow = function(shouldCreate) {
    //  if (shouldCreate || !cachedDirectionArrow) {
    //    cachedDirectionArrow = createDirectionArrow();
    //  }
    //  return cachedDirectionArrow;
    //};

    //var moveTo = function(lonlat) {
    //  getDirectionArrow().move(lonlat);
    //  getMassTransitMarker().moveTo(lonlat);
    //};

    //var select = function() { getMassTransitMarker().select(); };

    //var deselect = function() { getMassTransitMarker().deselect(); };

    //var finalizeMove = function() {
    //  getMassTransitMarker().finalizeMove();
    //};

    //var rePlaceInGroup = function() { getMassTransitMarker().rePlaceInGroup(); };

    return {
      getMarker: getMarker,
      createNewMarker: createNewMarker,
      getMarkerSelectionStyles: getMarkerSelectionStyles,
      getMarkerDefaultStyles: getMarkerDefaultStyles,
      getMarkerFeature: getMarkerFeature
      //getDirectionArrow: getDirectionArrow,
      //moveTo: moveTo,
      //select: select,
      //deselect: deselect,
      //finalizeMove: finalizeMove,
      //rePlaceInGroup: rePlaceInGroup
    };
  };
}(this));
