(function(root) {
  root.TrafficSignForm = function() {
    PointAssetForm.call(this);
    var me = this;

    this.initialize = function(parameters) {
      me.pointAsset = parameters.pointAsset;
      me.roadCollection = parameters.roadCollection;
      me.applicationModel = parameters.applicationModel;
      me.backend = parameters.backend;
      me.saveCondition = parameters.saveCondition;
      me.bindEvents(parameters);
    };

    this.renderValueElement = function(asset, collection) {
      var allTrafficSignProperties = asset.propertyData;
      var trafficSignSortedProperties = sortAndFilterTrafficSignProperties(allTrafficSignProperties);

      var components = _.reduce(_.map(trafficSignSortedProperties, function (feature) {
        feature.localizedName = window.localizedStrings[feature.publicId];
        var propertyType = feature.propertyType;

        if (propertyType === "text")
          return textHandler(feature);

        if (propertyType === "single_choice")
          return singleChoiceHandler(feature, collection);

        if (propertyType === "read_only_number")
          return readOnlyHandler(feature);

      }), function(prev, curr) { return prev + curr; }, '');

      if(asset.validityDirection)
        return components +
          '    <div class="form-group editable form-directional-traffic-sign edit-only">' +
          '      <label class="control-label">Vaikutussuunta</label>' +
          '      <button id="change-validity-direction" class="form-control btn btn-secondary btn-block">Vaihda suuntaa</button>' +
          '    </div>';

      return components;
    };

    this.boxEvents = function(rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection) {

      rootElement.find('.form-traffic-sign input[type=text],.form-traffic-sign select').on('change input', function (event) {
        var eventTarget = $(event.currentTarget);
        var propertyPublicId = eventTarget.attr('id');
        var propertyValue = $(event.currentTarget).val();
        selectedAsset.setPropertyByPublicId(propertyPublicId, propertyValue);
      });

    };

    var sortAndFilterTrafficSignProperties = function(properties) {
      var propertyOrdering = [
        'trafficSigns_type',
        'trafficSigns_value',
        'trafficSigns_info',
        'counter'];

      return _.sortBy(properties, function(property) {
        return _.indexOf(propertyOrdering, property.publicId);
      }).filter(function(property){
        return _.indexOf(propertyOrdering, property.publicId) >= 0;
      });
    };

    var textHandler = function (property) {
      var propertyValue = (property.values.length === 0) ? '' : property.values[0].propertyValue;
      return '' +
        '    <div class="form-group editable form-traffic-sign">' +
        '        <label class="control-label">' + property.localizedName + '</label>' +
        '        <p class="form-control-static">' + (propertyValue || '–') + '</p>' +
        '        <input type="text" class="form-control" id="' + property.publicId + '" value="' + propertyValue + '">' +
        '    </div>';
    };

    var singleChoiceHandler = function (property, collection) {
      var propertyValue = (property.values.length === 0) ? '' : _.head(property.values).propertyValue;
      var propertyDisplayValue = (property.values.length === 0) ? '' : _.head(property.values).propertyDisplayValue;
      var signTypes = _.map(_.filter(me.enumeratedPropertyValues, function(enumerated) { return enumerated.publicId == 'trafficSigns_type' ; }), function(val) {return val.values; });

      var groups =  collection.getGroup(signTypes);
      var groupKeys = Object.keys(groups);
      var trafficSigns = _.map(groupKeys, function (label) {
        return $('<optgroup label =  "'+ label +'" >'.concat(

          _.map(groups[label], function(group){
            return $('<option>',
              { value: group.propertyValue,
                selected: propertyValue == group.propertyValue,
                text: group.propertyDisplayValue}
            )[0].outerHTML; }))

        )[0].outerHTML;}).join('');

      return '' +
        '    <div class="form-group editable form-traffic-sign">' +
        '      <label class="control-label">' + property.localizedName + '</label>' +
        '      <p class="form-control-static">' + (propertyDisplayValue || '-') + '</p>' +
        '      <select class="form-control" style="display:none" id="' + property.publicId + '">  ' +
        trafficSigns +
        '      </select>' +
        '    </div>';
    };

    var readOnlyHandler = function (property) {
      var propertyValue = (property.values.length === 0) ? '' : property.values[0].propertyValue;
      var displayValue = (property.localizedName) ? property.localizedName : (property.values.length === 0) ? '' : property.values[0].propertyDisplayValue;

      return '' +
        '    <div class="form-group editable form-traffic-sign">' +
        '        <label class="control-label">' + displayValue + '</label>' +
        '        <p class="form-control-static">' + propertyValue + '</p>' +
        '    </div>';
    };

  };
})(this);