(function(root) {
  root.ManoeuvresCollection = function(backend, roadCollection) {
    var roadLinksWithManoeuvres = [];

    var combineRoadLinksWithManoeuvres = function(roadLinks, manoeuvres) {
      return _.map(roadLinks, function(roadLink) {
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
    };

    var fetchManoeuvres = function(extent, callback) {
      backend.getManoeuvres(extent, callback);
    };

    var fetch = function(extent, zoom, callback) {
      eventbus.once('roadLinks:fetched', function() {
        fetchManoeuvres(extent, function(manoeuvres) {
          roadLinksWithManoeuvres = combineRoadLinksWithManoeuvres(roadCollection.getAll(), manoeuvres);
          callback();
        });
      });
      roadCollection.fetch(extent, zoom);
    };

    var getAll = function() {
      return roadLinksWithManoeuvres;
    };

    return {
      fetch: fetch,
      getAll: getAll
    };
  };
})(this);
