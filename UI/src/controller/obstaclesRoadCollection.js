(function(root) {
  root.ObstaclesRoadCollection = function(backend) {
    RoadCollection.call(this, backend);
    var me = this;

    this.getRoadsForPointAssets = function() {
      return _.chain(me.roadLinks())
        .filter(function(roadLink) {
          return roadLink.getData().administrativeClass !== "Unknown";
        })
        .map(function(roadLink) {
          return roadLink.getData();
        })
        .value();
    };
  };
})(this);