(function(root) {
  root.LinkPropertiesModel = function() {
    var currentDataset = 'administrative-class';

    var getDataset = function() {
      return currentDataset;
    };

    var setDataset = function(dataset) {
      if (currentDataset !== dataset) {
        currentDataset = dataset;
        eventbus.trigger('linkProperties:dataset:changed', dataset);
      }
    };

    return {
      getDataset: getDataset,
      setDataset: setDataset
    };
  };
})(this);
