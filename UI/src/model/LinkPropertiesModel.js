(function(root) {
  root.LinkPropertiesModel = function() {
    var currentDataset;

    var setDataset = function(dataset) {
      currentDataset = dataset;
      eventbus.trigger('linkProperty:dataset:changed', dataset);
    };

    return {
      setDataset: setDataset
    };
  };
})(this);
