(function(root) {
  root.ManoeuvresCollection = function(backend, roadCollection) {
    var manoeuvres = [];
    var roadLinksWithManoeuvres = [];
    var addedManoeuvres = [];
    var removedManoeuvres = [];

    var combineRoadLinksWithManoeuvres = function(roadLinks, manoeuvres) {
      return _.map(roadLinks, function(roadLink) {
        var filteredManoeuvres = _.filter(manoeuvres, function(manoeuvre) {
          return manoeuvre.sourceRoadLinkId === roadLink.roadLinkId;
        });
        var destinationOfManoeuvres = _.chain(manoeuvres)
          .filter(function(manoeuvre) {
            return manoeuvre.destRoadLinkId === roadLink.roadLinkId;
          })
          .pluck('id')
          .value();

        return _.merge({}, roadLink, {
          manoeuvreSource: _.isEmpty(filteredManoeuvres) ? 0 : 1,
          destinationOfManoeuvres: destinationOfManoeuvres,
          manoeuvres: filteredManoeuvres,
          type: 'normal'
        });
      });
    };

    var fetchManoeuvres = function(extent, callback) {
      backend.getManoeuvres(extent, callback);
    };

    var fetch = function(extent, zoom, callback) {
      eventbus.once('roadLinks:fetched', function() {
        fetchManoeuvres(extent, function(ms) {
          manoeuvres = addedManoeuvres.concat(ms);
          _.remove(manoeuvres, function(manoeuvre) {
            return _.some(removedManoeuvres, function(x) {
              return (manoeuvresEqual(x, manoeuvre));
            });
          });
          roadLinksWithManoeuvres = combineRoadLinksWithManoeuvres(roadCollection.getAll(), manoeuvres);
          callback();
        });
      });
      roadCollection.fetch(extent, zoom);
    };

    var getAll = function() {
      return roadLinksWithManoeuvres;
    };

    var getDestinationRoadLinksBySourceRoadLink = function(roadLinkId) {
      return _.chain(manoeuvres)
        .filter(function(manoeuvre) {
          return manoeuvre.sourceRoadLinkId === roadLinkId;
        })
        .pluck('destRoadLinkId')
        .value();
    };

    var get = function(roadLinkId, callback) {
      var roadLink = _.find(roadLinksWithManoeuvres, function(manoeuvre) {
        return manoeuvre.roadLinkId === roadLinkId;
      });
      backend.getAdjacent(roadLink.roadLinkId, function(adjacent) {
        callback(_.merge({}, roadLink, { adjacent: adjacent }));
      });
    };

    var addManoeuvre = function(newManoeuvre) {
      manoeuvres.push(newManoeuvre);
      addedManoeuvres.push(newManoeuvre);
      _.remove(removedManoeuvres, function(x) {
        return manoeuvresEqual(x, newManoeuvre);
      });
      roadLinksWithManoeuvres = combineRoadLinksWithManoeuvres(roadCollection.getAll(), manoeuvres);
      eventbus.trigger('manoeuvre:changed');
    };

    var removeManoeuvre = function(sourceRoadLinkId, destRoadLinkId) {
      var removedManoeuvre = { sourceRoadLinkId: sourceRoadLinkId, destRoadLinkId: destRoadLinkId};
      removedManoeuvres.push(removedManoeuvre);
      _.remove(manoeuvres, function(manoeuvre) {
        return manoeuvresEqual(manoeuvre, removedManoeuvre);
      });
      _.remove(addedManoeuvres, function(x) {
        return manoeuvresEqual(x, removedManoeuvre);
      });
      roadLinksWithManoeuvres = combineRoadLinksWithManoeuvres(roadCollection.getAll(), manoeuvres);
      eventbus.trigger('manoeuvre:changed');
    };

    var manoeuvresEqual = function(x, y) {
      return (x.sourceRoadLinkId === y.sourceRoadLinkId && x.destRoadLinkId === y.destRoadLinkId);
    };

    return {
      fetch: fetch,
      getAll: getAll,
      getDestinationRoadLinksBySourceRoadLink: getDestinationRoadLinksBySourceRoadLink,
      get: get,
      addManoeuvre: addManoeuvre,
      removeManoeuvre: removeManoeuvre
    };
  };
})(this);
