(function(root) {
  root.AssetFormElementsFactory = {
    construct: construct
  };

  function assetFormElementConstructor(className) {
    var assetFormElementConstructors = {
      prohibition: ProhibitionFormElements
    };
    return assetFormElementConstructors[className] || PiecewiseLinearAssetFormElements;
  }

  function construct(unit, editControlLabels, className, defaultValue, elementType, possibleValues) {
    return assetFormElementConstructor(className)(unit, editControlLabels, className, defaultValue, elementType, possibleValues);
  }
})(this);
