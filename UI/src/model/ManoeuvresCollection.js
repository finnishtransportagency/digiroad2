(function(root) {
  root.ManoeuvresCollection = function(backend, roadCollection) {
    var manoeuvres = [];
    var addedManoeuvres = [];
    var removedManoeuvres = [];
    var updatedInfo = {};
    var multipleSourceManoeuvresHMap = {};
    var multipleIntermidiateManoeuvresHMap = {};
    var multipleDestinationManoeuvresHMap = {};
    var sourceDestinationManoeuvresHMap = {};
    var dirty = false;

    //----------------------------------
    // Public methods
    //----------------------------------

    var fetch = function(extent, zoom, callback) {
      eventbus.once('roadLinks:fetched', function() {
        fetchManoeuvres(extent, function(ms) {
          manoeuvres = ms;
          manoeuvres = formatManoeuvres(manoeuvres);
          callback();
          eventbus.trigger('manoeuvres:fetched');
        });
      });
      roadCollection.fetch(extent);
    };

    var getAll = function() {
      return combineRoadLinksWithManoeuvres(roadCollection.getAll(), manoeuvresWithModifications());
    };

    var getDestinationRoadLinksBySourceLinkId = function(linkId) {
      return _.chain(manoeuvresWithModifications())
          .filter(function(manoeuvre) {
            return manoeuvre.sourceLinkId === linkId;
          })
          .pluck('firstTargetLinkId')
          .value();
    };

    var get = function(linkId, callback) {
      var roadLink = _.find(getAll(), {linkId: linkId});
      backend.getAdjacent(roadLink.linkId, function(adjacent) {
        var modificationData = getLatestModificationDataBySourceRoadLink(linkId);
        var markers = ["A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"];
        var sortedAdjacentWithMarker = _.chain(adjacent)
            .sortBy('id')
            .map(function(a, i){
              return _.merge({}, a, { marker: markers[i] });
            }).value();
        var sourceRoadLinkModel = roadCollection.get([linkId])[0];
        callback(_.merge({}, roadLink, modificationData, { adjacent: sortedAdjacentWithMarker }, { select: sourceRoadLinkModel.select, unselect: sourceRoadLinkModel.unselect } ));
      });
    };

    var addManoeuvre = function(newManoeuvre) {
      dirty = true;
      if (_.isNull(newManoeuvre.manoeuvreId)) {
        _.remove(addedManoeuvres, function(m) { return manoeuvresEqual(m, newManoeuvre); });
        addedManoeuvres.push(newManoeuvre);
      } else {
        _.remove(removedManoeuvres, function(x) {
          return manoeuvresEqual(x, newManoeuvre);
        });
      }
      eventbus.trigger('manoeuvre:changed', newManoeuvre);
    };

    var removeManoeuvre = function(manoeuvre) {
      dirty = true;
      if (_.isNull(manoeuvre.manoeuvreId)) {
        _.remove(addedManoeuvres, function(x) {
          return manoeuvresEqual(x, manoeuvre);
        });
      } else {
        removedManoeuvres.push(manoeuvre);
      }
      eventbus.trigger('manoeuvre:changed', manoeuvre);
    };

    var setExceptions = function(manoeuvreId, exceptions) {
      dirty = true;
      var info = updatedInfo[manoeuvreId] || {};
      info.exceptions = exceptions;
      updatedInfo[manoeuvreId] = info;
      eventbus.trigger('manoeuvre:changed', exceptions);
    };

    var setValidityPeriods = function(manoeuvreId, validityPeriods) {
      dirty = true;
      var info = updatedInfo[manoeuvreId] || {};
      info.validityPeriods = validityPeriods;
      updatedInfo[manoeuvreId] = info;
      eventbus.trigger('manoeuvre:changed', validityPeriods);
    };

    var setAdditionalInfo = function(manoeuvreId, additionalInfo) {
      dirty = true;
      var info = updatedInfo[manoeuvreId] || {};
      info.additionalInfo = additionalInfo;
      updatedInfo[manoeuvreId] = info;
      eventbus.trigger('manoeuvre:changed', additionalInfo);
    };

    var cancelModifications = function() {
      addedManoeuvres = [];
      removedManoeuvres = [];
      updatedInfo = {};
      multipleSourceManoeuvresHMap = {};
      multipleIntermidiateManoeuvresHMap = {};
      multipleDestinationManoeuvresHMap = {};
      sourceDestinationManoeuvresHMap = {};
      dirty = false;
    };

    var isDirty = function() {
      return dirty;
    };

    var save = function(callback) {
      var removedManoeuvreIds = _.pluck(removedManoeuvres, 'manoeuvreId');

      var failureCallback = function() {
        dirty = true;
        eventbus.trigger('asset:updateFailed');
      };
      var successCallback = function() {
        dirty = false;
        callback();
      };
      var details = _.omit(updatedInfo, function(value, key) {
        return _.some(removedManoeuvreIds, function(id) {
          return id === parseInt(key, 10);
        });
      });

      var backendCallStack = [];
      backendCallStack.push({
        data: removedManoeuvreIds,
        operation: backend.removeManoeuvres,
        resetData: function() { removedManoeuvres = []; }
      });
      backendCallStack.push({
        data: addedManoeuvres,
        operation: backend.createManoeuvres,
        resetData: function() { addedManoeuvres = []; }
      });
      backendCallStack.push({
        data: details,
        operation: backend.updateManoeuvreDetails,
        resetData: function() { updatedInfo = {}; }
      });

      unwindBackendCallStack(backendCallStack, successCallback, failureCallback);
    };

    var cleanHMapSourceManoeuvres = function() {
      multipleSourceManoeuvresHMap = {};
      dirty = false;
    };

    var cleanHMapIntermidiateManoeuvres = function() {
      multipleIntermidiateManoeuvresHMap = {};
      dirty = false;
    };

    var cleanHMapDestinationManoeuvres = function() {
      multipleDestinationManoeuvresHMap = {};
      dirty = false;
    };

    var cleanHMapSourceDestinationManoeuvres = function() {
      sourceDestinationManoeuvresHMap = {};
      dirty = false;
    };

    //---------------------------------------
    // Utility functions
    //---------------------------------------

    //Attach manoeuvre data to road link
    var combineRoadLinksWithManoeuvres = function (roadLinks, manoeuvres) {
      return _.map(roadLinks, function (roadLink) {

        // Filter manoeuvres whose sourceLinkId matches road link's id
        var filteredManoeuvres = _.filter(manoeuvres, function (manoeuvre) {
          return _.some(manoeuvre.elements, function (element) {
            return element.sourceLinkId === roadLink.linkId && element.elementType === 1;
          });
        });

        // Check if road link is intermediate link of some manoeuvre
        // Used to visualize road link on map
        var intermediateManoeuvres  = _.chain(manoeuvres)
            .filter(function(manoeuvre) {
              return _.some(manoeuvre.elements, function (element) {
                return element.sourceLinkId === roadLink.linkId && element.elementType === 2;
              });
            })
            .pluck('id')
            .value();

        // Check if road link is source link for multiple manoeuvres
        // Used to visualize road link on map
        var multipleSourceManoeuvres = _.chain(filteredManoeuvres)
            .filter(function (manoeuvre) {
              return _.some(manoeuvre.elements, function (element) {
                multipleSourceManoeuvresHMap[element.sourceLinkId] = element.sourceLinkId in multipleSourceManoeuvresHMap ? multipleSourceManoeuvresHMap[element.sourceLinkId] += 1 : 1;
                return multipleSourceManoeuvresHMap[element.sourceLinkId] >= 2 && element.elementType === 1;
              });
            })
            .pluck('id')
            .value();

        // Check if road link is intermediate link for multiple manoeuvres
        // Used to visualize road link on map
        var multipleIntermediateManoeuvres = _.chain(manoeuvres)
            .filter(function (manoeuvre) {
              return _.some(manoeuvre.elements, function (element) {
                if (element.sourceLinkId === roadLink.linkId && element.elementType === 2){
                  multipleIntermidiateManoeuvresHMap[element.sourceLinkId] = element.sourceLinkId in multipleIntermidiateManoeuvresHMap ? multipleIntermidiateManoeuvresHMap[element.sourceLinkId] += 1 : 1;
                  return multipleIntermidiateManoeuvresHMap[element.sourceLinkId] >= 2;
                }
              });
            })
            .pluck('id')
            .value();

        // Check if road link is destination link of some manoeuvre
        // Used to visualize road link on map
        var destinationOfManoeuvres = _.chain(manoeuvres)
          .filter(function(manoeuvre) {
            return _.some(manoeuvre.elements, function (element) {
              return element.sourceLinkId === roadLink.linkId && element.elementType === 3;
            });
          })
          .pluck('id')
          .value();

        // Check if road link is destination link for multiple manoeuvres
        // Used to visualize road link on map
        var multipleDestinationManoeuvres = _.chain(manoeuvres)
            .filter(function (manoeuvre) {
              return _.some(manoeuvre.elements, function (element) {
                if (element.sourceLinkId === roadLink.linkId && element.elementType === 3) {
                  multipleDestinationManoeuvresHMap[element.sourceLinkId] = element.sourceLinkId in multipleDestinationManoeuvresHMap ? multipleDestinationManoeuvresHMap[element.sourceLinkId] += 1 : 1;
                  return multipleDestinationManoeuvresHMap[element.sourceLinkId] >= 2;
                }
              });
            })
            .pluck('id')
            .value();

        // Check if road link is source and destination link for manoeuvres
        // Used to visualize road link on map
        var sourceDestinationManoeuvres = _.chain(manoeuvres)
            .filter(function (manoeuvre) {
              return _.some(manoeuvre.elements, function (element) {
                if (element.sourceLinkId === roadLink.linkId && (element.elementType === 1 || element.elementType === 3)) {
                  sourceDestinationManoeuvresHMap[element.sourceLinkId] = element.sourceLinkId in sourceDestinationManoeuvresHMap ? sourceDestinationManoeuvresHMap[element.sourceLinkId] += 1 : 1;
                  return sourceDestinationManoeuvresHMap[element.sourceLinkId] >= 2;
                }
              });
            })
            .pluck('id')
            .value();

        // Check if road link is source link of some manoeuvre
        // Used to visualize road link on map
        var manoeuvreSource = _.isEmpty(filteredManoeuvres) ? 0 : 1;

        return _.merge({}, roadLink, {
          manoeuvreSource: manoeuvreSource,
          destinationOfManoeuvres: destinationOfManoeuvres,
          intermediateManoeuvres : intermediateManoeuvres,
          multipleSourceManoeuvres: multipleSourceManoeuvres,
          multipleIntermediateManoeuvres: multipleIntermediateManoeuvres,
          multipleDestinationManoeuvres: multipleDestinationManoeuvres,
          sourceDestinationManoeuvres: sourceDestinationManoeuvres,
          manoeuvres: filteredManoeuvres,
          type: 'normal'
        });
      });
    };

    var fetchManoeuvres = function(extent, callback) {
      backend.getManoeuvres(extent, callback);
    };

    // Extract manoeuvre sourceLinkId and destLinkId from first and last element to manoeuvre level
    var formatManoeuvres = function(manoeuvres) {
      return _.map(manoeuvres, function (manoeuvre) {
        var sourceLinkId = manoeuvre.elements[0].sourceLinkId;
        var firstTargetLinkId = manoeuvre.elements[0].destLinkId;
        var lastElementIndex = manoeuvre.elements.length - 1;
        var destLinkId = manoeuvre.elements[lastElementIndex].sourceLinkId;
        var intermediateLinkIds = _.chain(manoeuvre.elements)
          .filter(function(element) {
            return element.elementType === 2;
          })
          .pluck('sourceLinkId')
          .value();
        return _.merge({}, manoeuvre, {
          sourceLinkId: sourceLinkId,
          firstTargetLinkId: firstTargetLinkId,
          intermediateLinkIds: intermediateLinkIds,
          destLinkId: destLinkId
        });
      });
    };

    var manoeuvresWithModifications = function() {
      return _.reject(manoeuvres.concat(addedManoeuvres), function(manoeuvre) {
        return _.some(removedManoeuvres, function(x) {
          return (manoeuvresEqual(x, manoeuvre));
        });
      });
    };

    var sortLinkManoeuvres = function(manoeuvres) {
      return manoeuvres.sort(function(m1, m2) {
        var date1 = moment(m1.modifiedDateTime, "DD.MM.YYYY HH:mm:ss");
        var date2 = moment(m2.modifiedDateTime, "DD.MM.YYYY HH:mm:ss");
        return date1.isBefore(date2) ? 1 : -1;
      });
    };

    var getLatestModificationDataBySourceRoadLink = function(linkId) {
      var sourceLinkManoeuvres = _.filter(manoeuvresWithModifications(), function(manoeuvre) {
        return manoeuvre.sourceLinkId === linkId;
      });
      var sortedManoeuvres = sortLinkManoeuvres(sourceLinkManoeuvres);
      var latestModification = _.first(sortedManoeuvres);
      return {
        modifiedAt: latestModification ? latestModification.modifiedDateTime : null,
        modifiedBy: latestModification ? latestModification.modifiedBy : null
      };
    };

    var manoeuvresEqual = function(x, y) {
      return (x.sourceLinkId === y.sourceLinkId && x.destLinkId === y.destLinkId);
    };

    var unwindBackendCallStack = function(stack, callback, failureCallback) {
      if(_.isEmpty(stack)) {
        callback();
      } else {
        var call = stack.pop();
        if(_.isEmpty(call.data)) {
          unwindBackendCallStack(stack, callback, failureCallback);
        } else {
          call.operation(call.data, function() {
            call.resetData();
            unwindBackendCallStack(stack, callback, failureCallback);
          }, failureCallback);
        }
      }
    };

    return {
      fetch: fetch,
      getAll: getAll,
      getDestinationRoadLinksBySourceLinkId: getDestinationRoadLinksBySourceLinkId,
      get: get,
      addManoeuvre: addManoeuvre,
      removeManoeuvre: removeManoeuvre,
      setExceptions: setExceptions,
      setValidityPeriods: setValidityPeriods,
      setAdditionalInfo: setAdditionalInfo,
      cancelModifications: cancelModifications,
      isDirty: isDirty,
      save: save,
      cleanHMapSourceManoeuvres: cleanHMapSourceManoeuvres,
      cleanHMapIntermidiateManoeuvres: cleanHMapIntermidiateManoeuvres,
      cleanHMapDestinationManoeuvres: cleanHMapDestinationManoeuvres,
      cleanHMapSourceDestinationManoeuvres: cleanHMapSourceDestinationManoeuvres
    };
  };
})(this);
