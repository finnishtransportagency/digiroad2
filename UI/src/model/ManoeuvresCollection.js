(function(root) {
  root.ManoeuvresCollection = function(backend, roadCollection) {
    var manoeuvres = [];
    var addedManoeuvres = [];
    var removedManoeuvres = [];
    var updatedInfo = {};
    var multipleSourceManoeuvresHMap = {};
    var multipleIntermediateManoeuvresHMap = {};
    var multipleDestinationManoeuvresHMap = {};
    var sourceDestinationManoeuvresHMap = {};
    var dirty = false;

    //----------------------------------
    // Public methods
    //----------------------------------

    /**
     * Fetches manoeuvres in map area and attached them to manoeuvres variable in ManoeuvresCollection. Used by ManoeuvreLayer.
     *
     * @param extent
     * @param zoom
     * @param callback
     */
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

    /**
     * Returns all road links with manoeuvres attached to their source road link. Used by ManoeuvresCollection.get and ManoeuvreLayer.
     *
     * @returns {Array}
     */
    var getAll = function() {
      return combineRoadLinksWithManoeuvres(roadCollection.getAll(), manoeuvresWithModifications());
    };

    /**
     * Returns a list of second link ids in manoeuvre link chain by source link id. Used by ManoeuvreLayer.
     *
     * @param linkId
     * @returns {*}
     */
    var getFirstTargetRoadLinksBySourceLinkId = function(linkId) {
      return _.chain(manoeuvresWithModifications())
          .filter(function(manoeuvre) {
            return manoeuvre.sourceLinkId === linkId;
          })
          .pluck('firstTargetLinkId')
          .value();
    };

    /**
     * Returns road link with adjacent links and their markers added. Used by SelectedManoeuvreSource.open.
     *
     * @param linkId    Source link id
     * @param callback  What to do after asynchronous backend call
     */
    var get = function(linkId, callback) {
      var roadLink = _.find(getAll(), {linkId: linkId});
      var linkIds = getNonAdjacentTargetRoadLinksBySourceLinkId(linkId);
      var targets = _.map(linkIds, function (t) {
        var link = roadCollection.get([t])[0];
        return link ? link.getData() : {
          linkId: t
        };
      });
      backend.getAdjacent(roadLink.linkId, function(adjacents) {
        linkIds = linkIds.concat(_.map(adjacents, function (rl) {
          return rl.linkId;
        }));
        fillTargetAdjacents(roadLink, targets, linkIds, adjacents, callback);
      });
    };

    /**
     * Updates model after form changes.
     *
     * @param newManoeuvre
     */
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

    /**
     * TODO:
     *
     * @param manoeuvre
     */
    var updateManoeuvre = function(manoeuvre) {
      dirty = true;
    };

      /**
       * Updates model after form changes.
       *
       * @param manoeuvre
       */
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

    /**
     * Add link id to manoeuvre link chain.
     *
     * @param manoeuvre
     * @param linkId
     */
    var addLink = function(manoeuvre, linkId) {
      dirty = true;
      if (_.isNull(manoeuvre.manoeuvreId)) {
        // Remove old one if manoeuvre already exists in addedManoeuvres list
        _.remove(addedManoeuvres, function(m) { return manoeuvresEqual(m, manoeuvre); });
        // Add link id to list after removal comparison and update destLinkId
        manoeuvre.linkIds.push(linkId);
        manoeuvre.destLinkId = linkId;
        // Remove source link id and destination link id to get only intermediate link ids
        var intermediateLinkIds = manoeuvre.linkIds.slice(1, manoeuvre.linkIds.length-1);
        var manoeuvreIntermediateLinks = _.merge({}, { intermediateLinkIds: intermediateLinkIds }, manoeuvre);
        // Add enriched manoeuvre to addedManoeuvres list
        addedManoeuvres.push(manoeuvreIntermediateLinks);
      } else {
        var persisted = _.find(_.chain(manoeuvres).concat(addedManoeuvres).value(), {id: manoeuvre.manoeuvreId});
        persisted.linkIds.push(linkId);
        persisted.destLinkId = linkId;
        var persistedIntermediateLinks = persisted.linkIds.slice(1, persisted.linkIds.length-1);
        var updatedPersistedManoeuvre = _.merge({}, { intermediateLinkIds: persistedIntermediateLinks }, persisted);
        addManoeuvre(updatedPersistedManoeuvre);
      }
      reload(manoeuvre, linkId, function(reloaded) {
        eventbus.trigger('manoeuvre:linkAdded', reloaded);
      });
    };

    var reload = function(manoeuvre, linkId, callback) {
      // Reload the source link and the manoeuvre data
      get(manoeuvre.sourceLinkId, function (roadLink) {
        var modified = roadLink.manoeuvres.find(function (m) {
          return manoeuvresEqual(m, manoeuvre);
        });
        var searchBase;
        if (modified.intermediateLinkIds && modified.intermediateLinkIds.length > 0) {
          searchBase = roadLink.nonAdjacentTargets;
        } else {
          searchBase = roadLink.adjacent;
        }
        var targetLink = searchBase.find(function (t) {
          return t.linkId === linkId;
        });

        if (targetLink) {

          if (modified.linkIds.length > 1) {
            var secondLast = modified.linkIds[modified.linkIds.length - 2];
            backend.getAdjacent(secondLast, function (adjacentLinksToRemove) {

              // get array of ids from backend
              var linkIdsToRemove = _.map(adjacentLinksToRemove, function(road) { return road.linkId; }).concat([secondLast]);
              var filtered = _.filter(targetLink.adjacentLinks, function (link) {
                return !_.contains(linkIdsToRemove, link.linkId);
              });
              callback(_.merge({}, modified, {"adjacentLinks": filtered}));
            });
          }
          callback(_.merge({}, modified, {"adjacentLinks": targetLink.adjacentLinks}));
        }

      });
    };

    /**
     * Remove link id from manoeuvre link chain.
     *
     * @param manoeuvre
     * @param linkId
     */
    var removeLink = function(manoeuvre, linkId) {
      dirty = true;
      var modified = manoeuvresWithModifications().find(function (m) {
        return manoeuvresEqual(m, manoeuvre) && m.destLinkId === linkId;
      });
      if (modified) {
        console.log("manoeuvre found!");
        _.remove(addedManoeuvres, function(m) { return manoeuvresEqual(m, manoeuvre); });
        var newDestLinkId = manoeuvre.linkIds.pop();
        manoeuvre.destLinkId = newDestLinkId;
        var intermediateLinkIds = manoeuvre.linkIds.slice(1, manoeuvre.linkIds.length-2);
        var manoeuvreIntermediateLinks = _.merge({}, manoeuvre);
        manoeuvreIntermediateLinks.intermediateLinkIds = intermediateLinkIds;
        manoeuvreIntermediateLinks.destLinkId = newDestLinkId;
        addedManoeuvres.push(manoeuvreIntermediateLinks);
        console.log(manoeuvreIntermediateLinks);
        eventbus.trigger('manoeuvre:changed', manoeuvreIntermediateLinks);
        reload(manoeuvreIntermediateLinks,
          manoeuvreIntermediateLinks.linkIds[manoeuvreIntermediateLinks.linkIds.length-1], function(reloaded) {
          eventbus.trigger('manoeuvre:linkDropped', reloaded);
        });
      } else {
        console.log("Warning! Attempted to remove link but manoeuvre was not found!");
      }
    };

    /**
     * Update exception modifications to model.
     *
     * @param manoeuvreId
     * @param exceptions
     */
    var setExceptions = function(manoeuvreId, exceptions) {
      dirty = true;
      var info = updatedInfo[manoeuvreId] || {};
      info.exceptions = exceptions;
      updatedInfo[manoeuvreId] = info;
      eventbus.trigger('manoeuvre:changed', exceptions);
    };

    /**
     * Update validity period modifications to model.
     *
     * @param manoeuvreId
     * @param validityPeriods
       */
    var setValidityPeriods = function(manoeuvreId, validityPeriods) {
      dirty = true;
      var info = updatedInfo[manoeuvreId] || {};
      info.validityPeriods = validityPeriods;
      updatedInfo[manoeuvreId] = info;
      eventbus.trigger('manoeuvre:changed', validityPeriods);
    };

    /**
     * Update additional info modifications to model.
     *
     * @param manoeuvreId
     * @param additionalInfo
     */
    var setAdditionalInfo = function(manoeuvreId, additionalInfo) {
      dirty = true;
      var info = updatedInfo[manoeuvreId] || {};
      info.additionalInfo = additionalInfo;
      updatedInfo[manoeuvreId] = info;
      eventbus.trigger('manoeuvre:changed', additionalInfo);
    };

    /**
     * Revert model state after cancellation.
     */
    var cancelModifications = function() {
      addedManoeuvres = [];
      removedManoeuvres = [];
      updatedInfo = {};
      multipleSourceManoeuvresHMap = {};
      multipleIntermediateManoeuvresHMap = {};
      multipleDestinationManoeuvresHMap = {};
      sourceDestinationManoeuvresHMap = {};
      dirty = false;
    };

    /**
     * Mark model state to 'dirty' after form changes.
     *
     * @returns {boolean}
       */
    var isDirty = function() {
      return dirty;
    };

    /**
     * Set model state 'dirty'
     *
     * @returns {boolean}
     */
    var setDirty = function(state) {
      dirty = state;
    };


    /**
     * Save model changes to database.
     *
     * @param callback
       */
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

    var cleanHMapIntermediateManoeuvres = function() {
      multipleIntermediateManoeuvresHMap = {};
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

    /**
     * Attaches manoeuvre data to road link.
     * - Manoeuvres with road link as source link
     * - Road link visualization information
     *
     * @param roadLinks
     * @param manoeuvres
     * @returns {Array} Roadlinks enriched with manoeuvre related data.
     */
     var combineRoadLinksWithManoeuvres = function (roadLinks, manoeuvres) {
      return _.map(roadLinks, function (roadLink) {

        // Filter manoeuvres whose sourceLinkId matches road link's id
        var filteredManoeuvres = _.filter(manoeuvres, function (manoeuvre) {
          return manoeuvre.sourceLinkId === roadLink.linkId;
        });

        // Check if road link is intermediate link of some manoeuvre
        var intermediateManoeuvres  = _.chain(manoeuvres)
          .filter(function(manoeuvre) {
            return _.some(manoeuvre.intermediateLinkIds, function (intermediateLinkId) {
              return intermediateLinkId === roadLink.linkId;
            });
          })
          .pluck('id')
          .value();

        // Check if road link is source link for multiple manoeuvres
        var multipleSourceManoeuvres = _.chain(filteredManoeuvres)
          .filter(function (manoeuvre) {
              multipleSourceManoeuvresHMap[manoeuvre.sourceLinkId] = manoeuvre.sourceLinkId in multipleSourceManoeuvresHMap ? multipleSourceManoeuvresHMap[manoeuvre.sourceLinkId] += 1 : 1;
              return multipleSourceManoeuvresHMap[manoeuvre.sourceLinkId] >= 2;
          })
          .pluck('id')
          .value();

        // Check if road link is intermediate link for multiple manoeuvres
        var multipleIntermediateManoeuvres = _.chain(manoeuvres)
          .filter(function (manoeuvre) {
            return _.some(manoeuvre.intermediateLinkIds, function (intermediateLinkId) {
              if (intermediateLinkId === roadLink.linkId){
                multipleIntermediateManoeuvresHMap[intermediateLinkId] = intermediateLinkId in multipleIntermediateManoeuvresHMap ? multipleIntermediateManoeuvresHMap[intermediateLinkId] += 1 : 1;
                return multipleIntermediateManoeuvresHMap[intermediateLinkId] >= 2;
              }
            });
          })
          .pluck('id')
          .value();

        // Check if road link is destination link of some manoeuvre
        var destinationOfManoeuvres = _.chain(manoeuvres)
          .filter(function(manoeuvre) {
            return manoeuvre.destLinkId === roadLink.linkId;
          })
          .pluck('id')
          .value();

        // Check if road link is destination link for multiple manoeuvres
        var multipleDestinationManoeuvres = _.chain(manoeuvres)
          .filter(function (manoeuvre) {
            if (manoeuvre.destLinkId === roadLink.linkId) {
              multipleDestinationManoeuvresHMap[manoeuvre.destLinkId] = manoeuvre.destLinkId in multipleDestinationManoeuvresHMap ? multipleDestinationManoeuvresHMap[manoeuvre.destLinkId] += 1 : 1;
              return multipleDestinationManoeuvresHMap[manoeuvre.destLinkId] >= 2;
            }
          })
          .pluck('id')
          .value();

        // Check if road link is source and destination link for manoeuvres
        var sourceDestinationManoeuvres = _.chain(manoeuvres)
          .filter(function (manoeuvre) {
            if (manoeuvre.sourceLinkId === roadLink.linkId || manoeuvre.destLinkId === roadLink.linkId) {
              var sourceOrDestLinkId = roadLink.linkId;
              sourceDestinationManoeuvresHMap[sourceOrDestLinkId] = sourceOrDestLinkId in sourceDestinationManoeuvresHMap ? sourceDestinationManoeuvresHMap[sourceOrDestLinkId] += 1 : 1;
              return sourceDestinationManoeuvresHMap[sourceOrDestLinkId] >= 2;
            }
          })
          .pluck('id')
          .value();

        // Check if road link is source link of some manoeuvre
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

    /**
     * Extract manoeuvre element data to manoeuvre level.
     *
     * @param manoeuvres
     * @returns {Array} Manoeuvres with data to be used in UI functionality.
    */
    var formatManoeuvres = function(manoeuvres) {
      return _.map(manoeuvres, function (manoeuvre) {
        var sourceLinkId = manoeuvre.elements[0].sourceLinkId;
        var firstTargetLinkId = manoeuvre.elements[0].destLinkId;
        var lastElementIndex = manoeuvre.elements.length - 1;
        var destLinkId = manoeuvre.elements[lastElementIndex].sourceLinkId;
        var linkIds = _.chain(manoeuvre.elements).pluck('sourceLinkId').value();
        var intermediateLinkIds = _.chain(manoeuvre.elements)
          .filter(function(element) {
            return element.elementType === 2;
          })
          .pluck('sourceLinkId')
          .value();
        return _.merge({}, manoeuvre, {
          sourceLinkId: sourceLinkId,
          firstTargetLinkId: firstTargetLinkId,
          linkIds: linkIds,
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


    /**
     * Check if manoeuvres are equal. Manoeuvres are equal if they have the same link chain.
     *
     * @param manoeuvreA
     * @param manoeuvreB
     * @returns {boolean}
       */
    var manoeuvresEqual = function(manoeuvreA, manoeuvreB) {
      var linkIdsA = manoeuvreA.linkIds;
      var linkIdsB = manoeuvreB.linkIds;
      return _.isEqual(linkIdsA, linkIdsB);
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

    var getNonAdjacentTargetRoadLinksBySourceLinkId = function(linkId) {
      return _.chain(manoeuvresWithModifications())
          .filter(function(manoeuvre) {
            return manoeuvre.sourceLinkId === linkId && manoeuvre.intermediateLinkIds &&
                manoeuvre.intermediateLinkIds.length > 0;
          })
          .pluck('destLinkId')
          .value();
    };

    var setMarkersToRoadLinks= function(roadLink, adjacentLinks, targetLinks, nextTargetLinks, callback) {
      var linkId = roadLink.linkId;
      var modificationData = getLatestModificationDataBySourceRoadLink(linkId);
      var markers = ["A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z",
        "AA", "AB", "AC", "AD", "AE", "AF", "AG", "AH", "AI", "AJ", "AK", "AL", "AM", "AN", "AO", "AP", "AQ", "AR", "AS", "AT", "AU", "AV", "AW", "AX", "AY", "AZ",
        "BA", "BB", "BC", "BD", "BE", "BF", "BG", "BH", "BI", "BJ", "BK", "BL", "BM", "BN", "BO", "BP", "BQ", "BR", "BS", "BT", "BU", "BV", "BW", "BX", "BY", "BZ",
        "CA", "CB", "CC", "CD", "CE", "CF", "CG", "CH", "CI", "CJ", "CK", "CL", "CM", "CN", "CO", "CP", "CQ", "CR", "CS", "CT", "CU", "CV", "CW", "CX", "CY", "CZ"];

      var adjacentIds = adjacentLinks.map(function(l) { return l.linkId; });
      var targetIds = targetLinks.map(function(l) { return l.linkId; });

      var sortedNextTargetLinksWithMarker = _.chain(nextTargetLinks)
          .mapValues(function(a){
            return _.merge({}, _.map(a, function (b, i) {
              return _.merge({}, b, { marker: markers[adjacentLinks.length + targetLinks.length + i] });
            }));
          }).value();

      var alteredTargets = _.map(targetLinks, function (t) {
        var targetAdjacent = _.filter(sortedNextTargetLinksWithMarker[t.linkId], function (rl) {
          return !(rl.linkId === roadLink.linkId || _.contains(targetIds, rl.linkId));
        });
        /*
         TODO: Do we need this chain and filtering? It didn't return adjacent links for nonAdjacentTargets (adjacentLinks was empty)
         Old implementation
        var targetAdjacent = _.chain(sortedNextTargetLinksWithMarker[t.linkId]).filter(function (rl) {
          console.log("" + rl.linkId + " -> " + _.contains(targetIds, rl.linkId) + " & " + _.contains(adjacentIds, rl.linkId));
          return !(rl.linkId === roadLink.linkId || _.contains(targetIds, rl.linkId) || _.contains(adjacentIds, rl.linkId));
        }).value;
        */
        return _.merge({}, t, { adjacentLinks: targetAdjacent });
      });

      var alteredAdjacents = _.map(adjacentLinks, function (t) {
        var targetAdjacent = _.filter(sortedNextTargetLinksWithMarker[t.linkId], function (rl) {
          //console.log("" + rl.linkId + " -> " + _.contains(adjacentIds, rl.linkId));
          return !(rl.linkId === roadLink.linkId || _.contains(adjacentIds, rl.linkId));
        });
        return _.merge({}, t, { adjacentLinks: targetAdjacent });
      });

      var sortedAdjacentWithMarker = _.chain(alteredAdjacents)
          .sortBy('id')
          .map(function(a, i){
            return _.merge({}, a, { marker: markers[i]} );
          }).value();

      var sortedTargetsWithMarker = _.chain(alteredTargets)
          .sortBy('id')
          .map(function(a, i){
            return _.merge({}, a, { marker: markers[i+adjacentLinks.length] } );
          }).value();

      var sourceRoadLinkModel = roadCollection.get([linkId])[0];
      callback(_.merge({}, roadLink, modificationData, { adjacent: sortedAdjacentWithMarker }, { nonAdjacentTargets: sortedTargetsWithMarker }, { select: sourceRoadLinkModel.select, unselect: sourceRoadLinkModel.unselect } ));
    };

    var fillTargetAdjacents = function(roadLink, targets, linkIds, adjacentLinks, callback) {
      backend.getAdjacents(linkIds.join(), function (nextTargets) {
        setMarkersToRoadLinks(roadLink, adjacentLinks, targets, nextTargets, callback);
      });
    };

    var getManoeuvreBySourceLinkId = function(sourceLinkId){
      for(var i = 0; i < addedManoeuvres.length; i++){
        if (addedManoeuvres[i].sourceLinkId === sourceLinkId)
          return addedManoeuvres[i];
      }
    }

    return {
      fetch: fetch,
      getAll: getAll,
      getFirstTargetRoadLinksBySourceLinkId: getFirstTargetRoadLinksBySourceLinkId,
      get: get,
      addManoeuvre: addManoeuvre,
      removeManoeuvre: removeManoeuvre,
      updateManoeuvre: updateManoeuvre,
      addLink: addLink,
      removeLink: removeLink,
      setExceptions: setExceptions,
      setValidityPeriods: setValidityPeriods,
      setAdditionalInfo: setAdditionalInfo,
      cancelModifications: cancelModifications,
      isDirty: isDirty,
      setDirty: setDirty,
      save: save,
      cleanHMapSourceManoeuvres: cleanHMapSourceManoeuvres,
      cleanHMapIntermediateManoeuvres: cleanHMapIntermediateManoeuvres,
      cleanHMapDestinationManoeuvres: cleanHMapDestinationManoeuvres,
      cleanHMapSourceDestinationManoeuvres: cleanHMapSourceDestinationManoeuvres,
      manoeuvresEqual: manoeuvresEqual,
      reload: reload,
      getManoeuvreBySourceLinkId : getManoeuvreBySourceLinkId
    };
  };
})(this);
