(function(root) {
  root.ManoeuvresCollection = function(roadCollection) {
    var fetch = function(extent, zoom, callback) {
      eventbus.once('roadLinks:fetched', function() { callback(); });
      roadCollection.fetch(extent, zoom);
    };

    return {
      fetch: fetch
    };
  };
})(this);
