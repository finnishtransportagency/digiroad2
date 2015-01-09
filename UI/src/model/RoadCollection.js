(function(root) {
  var RoadLinkModel = function(data) {
    var dirty = false;
    var selected = false;
    var original = _.clone(data);

    var getId = function() {
      return data.roadLinkId;
    };

    var getData = function() {
      return data;
    };

    var getPoints = function() {
      return _.cloneDeep(data.points);
    };

    var setTrafficDirection = function(trafficDirection) {
      if (trafficDirection != data.trafficDirection) {
        data.trafficDirection = trafficDirection;
        dirty = true;
        eventbus.trigger('linkProperties:changed');
      }
    };

    var setFunctionalClass = function(functionalClass) {
      if (functionalClass != data.functionalClass) {
        data.functionalClass = functionalClass;
        dirty = true;
        eventbus.trigger('linkProperties:changed');
      }
    };

    var isDirty = function() {
      return dirty;
    };

    var select = function() {
      selected = true;
      eventbus.trigger('linkProperties:selected', data);
    };

    var unselect = function() {
      selected = false;
      eventbus.trigger('linkProperties:unselected');
    };

    var isSelected = function() {
      return selected;
    };

    var cancel = function() {
      data.trafficDirection = original.trafficDirection;
      dirty = false;
      eventbus.trigger('linkProperties:cancelled', data);
    };

    var save = function(backend) {
      backend.updateLinkProperties(data.roadLinkId, data, function(linkProperties) {
        dirty = false;
        data = _.merge({}, data, linkProperties);
        eventbus.trigger('linkProperties:saved', data);
      }, function() {
        eventbus.trigger('linkProperties:updateFailed');
      });
    };

    return {
      getId: getId,
      getData: getData,
      getPoints: getPoints,
      setTrafficDirection: setTrafficDirection,
      setFunctionalClass: setFunctionalClass,
      isDirty: isDirty,
      isSelected: isSelected,
      select: select,
      unselect: unselect,
      cancel: cancel,
      save: save
    };
  };

  root.RoadCollection = function(backend) {
    var roadLinks = [];

    var self = this;

    var getSelectedRoadLinks = function() {
      return _.filter(roadLinks, function(roadLink) {
        return roadLink.isSelected();
      });
    };

    this.fetch = function(boundingBox, zoom) {
      backend.getRoadLinks(boundingBox, function(fetchedRoadLinks) {
        var selectedIds = _.map(getSelectedRoadLinks(), function(roadLink) {
          return roadLink.getId();
        });
        var fetchedRoadLinkModels = _.map(fetchedRoadLinks, function(roadLink) {
          return new RoadLinkModel(roadLink);
        });
        roadLinks = _.reject(fetchedRoadLinkModels, function(roadLink) {
          return _.contains(selectedIds, roadLink.getId());
        }).concat(getSelectedRoadLinks());
        eventbus.trigger('roadLinks:fetched', self.getAll(), zoom);
      });
    };

    this.getAll = function() {
      return _.map(roadLinks, function(roadLink) {
        return roadLink.getData();
      });
    };

    this.get = function(id) {
      return _.find(roadLinks, function(road) {
        return road.getId() === id;
      });
    };

    this.activate = function(road) {
      eventbus.trigger('road:active', road.roadLinkId);
    };
  };
})(this);
