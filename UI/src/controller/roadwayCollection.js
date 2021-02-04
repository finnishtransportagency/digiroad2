(function(root) {
  root.RoadwayCollection = function(backend, verificationCollection, spec) {
    LinearAssetsCollection.call(this, backend, verificationCollection, spec);
    this.linearAssets = [];
    var selection = null;
    this.setSelection = function(sel) {
      selection = sel;
    };
    this.getAll = function () {};
    this.fetch = function (boundingBox, center, zoom) {
      return true
    };
    this.fetchAssetsWithComplementary = function (boundingBox, center, zoom) {};
  };
})(this);
