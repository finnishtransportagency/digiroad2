(function(root) {
  var contextFilter = function(attributeName, attributeValue, context) {
    return new OpenLayers.Filter.Function({ evaluate: function() {
      return context[attributeName] === attributeValue;
    } });
  };
  var isInContextFilter = function(attributeName, attributeValues, context) {
    var filters = _.map(attributeValues, function(value) {
      return contextFilter(attributeName, value, context);
    });
    return new OpenLayers.Filter.Logical({
      type: OpenLayers.Filter.Logical.OR,
      filters: filters
    });
  };
  var featureAttributeFilter = function(attributeName, attributeValue) {
    return new OpenLayers.Filter.Comparison({
      type: OpenLayers.Filter.Comparison.EQUAL_TO,
      property: attributeName,
      value: attributeValue
    });
  };
  var isInFeatureAttributeFilter = function(attributeName, attributeValues) {
    var filters = _.map(attributeValues, function(value) {
      return featureAttributeFilter(attributeName, value);
    });
    return new OpenLayers.Filter.Logical({
      type: OpenLayers.Filter.Logical.OR,
      filters: filters
    });
  };
  var createUseFunction = function(state) {
    return function(style) {
      return new OpenLayers.Rule({
        filter: new OpenLayers.Filter.Logical({
          type: OpenLayers.Filter.Logical.AND,
          filters: state.filters
        }),
        symbolizer: style
      });
    };
  };
  var createWhereFunction = function(state) {
    return function(attributeName, context) {
      return {
        is: function(attributeValue) {
          var filter = context ? contextFilter(attributeName, attributeValue, context) :
            featureAttributeFilter(attributeName, attributeValue);
          return newIsObject({
            filters: state.filters.concat([filter])
          });
        },
        isIn: function(attributeValues) {
          var filter = context ? isInContextFilter(attributeName, attributeValues, context) :
            isInFeatureAttributeFilter(attributeName, attributeValues);
          return newIsObject({
            filters: state.filters.concat([filter])
          });
        }
      };
    };
  };
  var newIsObject = function(state) {
    return {
      and: createWhereFunction(state),
      use: createUseFunction(state)
    };
  };
  root.OpenLayersRule = function() {
    var state = {
      filters: []
    };
    return {
      where: createWhereFunction(state)
    };
  };
})(this);
