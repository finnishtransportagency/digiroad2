(function(root) {
  root.SelectedManoeuvreSource = function(manoeuvresCollection) {
    var current = null;
    var targetRoadLinkSelected = null;

    //----------------------------------
    // Public methods
    //----------------------------------

    /**
     * Closes selected manoeuvre source and triggers event that empties the manoeuvre form.
     * Used by SelectedManoeuvreSource.open and ManoeuvreLayer.unselectManoeuvre
     */
    var close = function() {
      if (current) {
        current.unselect();
        current = null;
        eventbus.trigger('manoeuvres:unselected');
      }
    };

    /**
     * Fetches road link by link id from collection and sets it as current manoeuvre source.
     *
     * @param linkId
       */
    var open = function(linkId) {
      if (!current || current.linkId !== linkId) {
        close();
        manoeuvresCollection.get(linkId, function(roadLink){
          current = roadLink;
          current.select();
          eventbus.trigger('manoeuvres:selected', roadLink);
        });
      }
    };

    /**
     * Refreshes current source link data when new target is selected in the manoeuvre form.
     * ManoeuvresCollection.get fetches adjacent links of new target and attaches them to source road link.
     */
    var updateAdjacents = function() {
      console.log("updateAdjacents");
      console.log(current);
      if (current) {
        var linkId = current.linkId;
        current = null;
        manoeuvresCollection.get(linkId, function(roadLink){
          current = roadLink;
          current.select();
          console.log("updated");
          console.log(current);
          eventbus.trigger('adjacents:updated', roadLink);
        });

      }
    };

    /**
     * Returns current source link. Used by ManoeuvreLayer to visualize road links on map.
     */
    var get = function() {
      return current;
    };

    /**
     * Returns link id of current source. Used by ManoeuvreLayer to visualize road links on map.
     */
    var getLinkId = function() {
      return current.linkId;
    };

    /**
     * Returns true if source link exists. Used by ManoeuvreLayer to visualize road links on map.
     */
    var exists = function() {
      return current !== null;
    };

    /**
     * Enrich manoeuvre and pass it to ManoeuvresCollection to update the model.
     *
     * @param manoeuvre
       */
    var addManoeuvre = function(manoeuvre) {
      var sourceLinkId = current.linkId;
      // Add sourceLinkId as first element in linkIds list if it's not there already
      var linkIds = (manoeuvre.linkIds[0] != sourceLinkId) ? manoeuvre.linkIds.unshift(sourceLinkId) : manoeuvre.linkIds;
      var newManoeuvre = _.merge({}, { sourceLinkId: sourceLinkId, linkIds: linkIds }, manoeuvre);
      manoeuvresCollection.addManoeuvre(newManoeuvre);
    };

    /**
     * TODO:
     *
     * @param manoeuvre
       */
    var updateManoeuvre = function(manoeuvre) {
      var sourceLinkId = current.linkId;
      // Add sourceLinkId as first element in linkIds list if it's not there already
      var linkIds = (manoeuvre.linkIds[0] != sourceLinkId) ? manoeuvre.linkIds.unshift(sourceLinkId) : manoeuvre.linkIds;
      var newManoeuvre = _.merge({}, { sourceLinkId: sourceLinkId, linkIds: linkIds }, manoeuvre);
      manoeuvresCollection.updateManoeuvre(newManoeuvre);
    };

    /**
     * Enrich manoeuvre and pass it to ManoeuvresCollection to update the model.
     *
     * @param manoeuvre
       */
    var removeManoeuvre = function(manoeuvre) {
      var sourceLinkId = current.linkId;
      var linkIds = (manoeuvre.linkIds[0] != sourceLinkId) ? manoeuvre.linkIds.unshift(sourceLinkId) : manoeuvre.linkIds;
      var manoeuvreToBeRemoved = _.merge({}, { sourceLinkId: current.linkId, linkIds: linkIds }, manoeuvre);
      manoeuvresCollection.removeManoeuvre(manoeuvreToBeRemoved);
    };

    /**
     * Add link id to manoeuvre link chain.
     *
     * @param manoeuvre
     * @param linkId
       */
    var addLink = function(manoeuvre, linkId) {
      console.log("AddLink " + manoeuvre.linkIds + " ++ " + linkId);
      var sourceLinkId = current.linkId;
      var linkIds = (manoeuvre.linkIds[0] != sourceLinkId) ? manoeuvre.linkIds.unshift(sourceLinkId) : manoeuvre.linkIds;
      var manoeuvreWithSourceLink = _.merge({}, { sourceLinkId: current.linkId, linkIds: linkIds }, manoeuvre);
      manoeuvresCollection.addLink(manoeuvreWithSourceLink, linkId);
    };

    /**
     * Remove link id from manoeuvre link chain.
     *
     * @param manoeuvre
     * @param linkId
       */
    var removeLink = function(manoeuvre, linkId) {
      manoeuvresCollection.removeLink(manoeuvre, linkId);
    };

    /**
     * Update exception modifications.
     *
     * @param manoeuvreId
     * @param exceptions
       */
    var setExceptions = function(manoeuvreId, exceptions) {
      manoeuvresCollection.setExceptions(manoeuvreId, exceptions);
    };

    /**
     * Update validity period modifications.
     *
     * @param manoeuvreId
     * @param exceptions
       */
    var setValidityPeriods = function(manoeuvreId, exceptions) {
      manoeuvresCollection.setValidityPeriods(manoeuvreId, exceptions);
    };

    /**
     * Update additional info modifications.
     *
     * @param manoeuvreId
     * @param additionalInfo
       */
    var setAdditionalInfo = function(manoeuvreId, additionalInfo) {
      manoeuvresCollection.setAdditionalInfo(manoeuvreId, additionalInfo);
    };

    /**
     * Save model and trigger event.
     */
    var save = function() {
      eventbus.trigger('manoeuvres:saving');
      manoeuvresCollection.save(function() {
        eventbus.trigger('manoeuvres:saved', current);
      });
    };

    /**
     * Revert model after cancellation and trigger event.
     */
    var cancel = function() {
      manoeuvresCollection.cancelModifications();
      eventbus.trigger('manoeuvres:cancelled', current);
    };

    /**
     * Check if model has been modified.
     * @returns {*|boolean}
     */
    var isDirty = function() {
      return manoeuvresCollection.isDirty();
    };

    /**
     * Refresh source link after form save.
     */
    var refresh = function() {
      if (current) {
        var linkId = current.linkId;
        current = null;
        open(linkId);
      }
    };

    /**
     * Save the target selected in the map to show radio buttons options
     *
     * @param targetRoadLink
     */
    var setTargetRoadLink = function(targetRoadLink) {
      targetRoadLinkSelected = targetRoadLink;
    };

    /**
     * Return the target selected in the map to show radio buttons options
     */
    var getTargetRoadLink = function() {
      return targetRoadLinkSelected;
    };

    /**
     * Returns true if Radio Buttons was showed. Used by ManoeuvreLayer to visualize if radio buttons options was showed before the redraw of the map.
     */
    var existTargetRoadLink = function() {
      return targetRoadLinkSelected !== null;
    };

    return {
      close: close,
      open: open,
      updateAdjacents: updateAdjacents,
      get: get,
      getLinkId: getLinkId,
      exists: exists,
      addManoeuvre: addManoeuvre,
      updateManoeuvre: updateManoeuvre,
      removeManoeuvre: removeManoeuvre,
      addLink: addLink,
      removeLink: removeLink,
      setExceptions: setExceptions,
      setValidityPeriods: setValidityPeriods,
      setAdditionalInfo: setAdditionalInfo,
      save: save,
      cancel: cancel,
      isDirty: isDirty,
      refresh: refresh,
      setTargetRoadLink: setTargetRoadLink,
      getTargetRoadLink: getTargetRoadLink,
      existTargetRoadLink: existTargetRoadLink
    };
  };
})(this);
