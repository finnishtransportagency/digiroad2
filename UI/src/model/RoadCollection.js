(function(root) {
  root.RoadCollection = function(backend) {
    var roadLinks = [];

    this.fetch = function(boundingBox, zoom) {
      backend.getRoadLinks(boundingBox, function(data) {
        roadLinks = data;
        eventbus.trigger('roadLinks:fetched', roadLinks, zoom);
      });
    };

    this.getAll = function() {
      return roadLinks;
    };

    this.get = function(id) {
      return _.find(roadLinks, function(road) {
        return road.roadLinkId === id;
      });
    };

    this.activate = function(road) {
      eventbus.trigger('road:active', road.roadLinkId);
    };

    this.getPointsOfRoadLink = function(id) {
      var road = _.find(roadLinks, function(road) {
        return road.roadLinkId === id;
      });
      return _.cloneDeep(road.points);
    };
  };
})(this);