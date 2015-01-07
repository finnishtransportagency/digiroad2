(function(root) {
  var RoadLinkModel = function(data) {
    var dirty = false;

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

    var isDirty = function() {
      return dirty;
    };

    return {
      getId: getId,
      getData: getData,
      getPoints: getPoints,
      setTrafficDirection: setTrafficDirection,
      isDirty: isDirty
    };
  };

  root.RoadCollection = function(backend) {
    var roadLinks = [];

    this.fetch = function(boundingBox, zoom) {
      backend.getRoadLinks(boundingBox, function(data) {
        roadLinks = _.map(data, function(roadLink) {
          return new RoadLinkModel(roadLink);
        });
        eventbus.trigger('roadLinks:fetched', data, zoom);
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
