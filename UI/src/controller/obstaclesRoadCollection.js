(function(root) {
  root.ObstaclesAndRailwayCrossingsRoadCollection = function (backend) {
    RoadCollection.call(this, backend);
    var me = this;

    this.getRoadsForPointAssets = function() {
      return _.chain(me.roadLinks())
        .map(function(roadLink) {
          return roadLink.getData();
        })
        .value();
    };
  };
})(this);