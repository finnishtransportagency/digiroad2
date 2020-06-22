(function(root) {
  root.TrafficSignsRoadCollection = function(backend) {
    RoadCollection.call(this, backend);
    var me = this;

    this.getRoadsForPointAssets = function() {
      return _.chain(me.roadLinks())
        .filter(function(roadLink) {
          return roadLink.isCarTrafficRoad();
        })
        .map(function(roadLink) {
          return roadLink.getData();
        })
        .value();
    };
  };
})(this);