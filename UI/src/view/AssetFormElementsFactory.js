(function(root) {
  root.AssetFormElementsFactory = {
    construct: construct
  };

  function assetFormElementConstructor(className) {
    var assetFormElementConstructors = {
      prohibition: ProhibitionFormElements,
      winterSpeedLimits: PiecewiseLinearAssetFormElements.WinterSpeedLimitsFormElements
    };
    return assetFormElementConstructors[className] || PiecewiseLinearAssetFormElements.DefaultFormElements;
  }

  function construct(asset) {
    return assetFormElementConstructor(asset.layerName)(asset.unit, asset.editControlLabels, asset.className, asset.defaultValue, asset.possibleValues);
  }
})(this);
