(function(root) {
  root.ManoeuvresCollection = function(backend, roadCollection) {
    var fetch = function(extent, zoom, callback) {
      eventbus.once('roadLinks:fetched', function() { callback(); });
      roadCollection.fetch(extent, zoom);
    };

    var getManoeuvres = function(extent, callback) {
      backend.getManoeuvres(extent, function(manoeuvres) {
        var roadLinksWithManoeuvres = _.map(roadCollection.getAll(), function(roadLink) {
          var manoeuvreSourceLink = _.find(manoeuvres, function(manoeuvre) {
            return manoeuvre.sourceRoadLinkId === roadLink.roadLinkId;
          });
          var manoeuvreDestinationLink = _.find(manoeuvres, function(manoeuvre) {
            return manoeuvre.destRoadLinkId === roadLink.roadLinkId;
          });
          return _.merge({}, roadLink, {
            manoeuvreSource: manoeuvreSourceLink ? 1 : 0,
            manoeuvreDestination: manoeuvreDestinationLink ? 1 : 0,
            type: 'normal'
          });
        });
        callback(roadLinksWithManoeuvres);
      });
    };

    return {
      fetch: fetch,
      getManoeuvres: getManoeuvres
    };
  };
})(this);
