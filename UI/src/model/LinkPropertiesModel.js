(function(root) {
  root.LinkPropertiesModel = function() {
    var currentDataset;

    var getDataset = function() {
      return currentDataset;
    };

    var setDataset = function(dataset) {
      currentDataset = dataset;
      eventbus.trigger('linkProperties:dataset:changed', dataset);
    };

    return {
      getDataset: getDataset,
      setDataset: setDataset
    };
  };
})(this);
