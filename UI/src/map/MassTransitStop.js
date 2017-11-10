(function(root) {

  root.MassTransitStop = function(data, collection, map) {
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
      cachedMassTransitMarker = new MassTransitMarkerStyle(data, collection, map);
      cachedFeature = cachedMassTransitMarker.createFeature();
      return { feature: cachedFeature };
    };

    var getMassTransitMarker = function() {
      return cachedMassTransitMarker;
    };

    return {
      getMarker: getMarker,
      createNewMarker: createNewMarker,
      getMarkerSelectionStyles: getMarkerSelectionStyles,
      getMarkerDefaultStyles: getMarkerDefaultStyles,
      getMarkerFeature: getMarkerFeature
    };
  };
}(this));
