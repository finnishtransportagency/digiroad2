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
      me.feedbackCollection = parameters.feedbackCollection;
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

      rootElement.find('.form-traffic-sign input[type=text],.form-traffic-sign select#trafficSigns_type').on('change input', function (event) {
        var eventTarget = $(event.currentTarget);
        var propertyPublicId = eventTarget.attr('id');
        var propertyValue = $(event.currentTarget).val();
        selectedAsset.setPropertyByPublicId(propertyPublicId, propertyValue);
      });

      rootElement.find('.form-traffic-sign select#main-trafficSigns_type').on('change', function (event) {
        var eventTarget = $(event.currentTarget);
        $('.form-traffic-sign select#trafficSigns_type').html(singleChoiceSubType(collection, $(event.currentTarget).val()));
        selectedAsset.setPropertyByPublicId('trafficSigns_type', $('.form-traffic-sign select#trafficSigns_type').val());
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

    var singleChoiceSubType = function (collection, mainType, property) {
      var propertyValue = (_.isUndefined(property) || property.values.length === 0) ? '' : _.head(property.values).propertyValue;
      var propertyDisplayValue = (_.isUndefined(property) || property.values.length === 0) ? '' : _.head(property.values).propertyDisplayValue;
      var signTypes = _.map(_.filter(me.enumeratedPropertyValues, function (enumerated) {
        return enumerated.publicId == 'trafficSigns_type';
      }), function (val) {
        return val.values;
      });
      var groups = collection.getGroup(signTypes);
      var subTypesTrafficSigns = _.map(_.map(groups)[mainType], function (group) {
        return $('<option>',
          {
            value: group.propertyValue,
            selected: propertyValue == group.propertyValue,
            text: group.propertyDisplayValue
          }
        )[0].outerHTML;
      }).join('');
      return '<div class="form-group editable form-traffic-sign">' +
        '      <label class="control-label"> ALITYYPPI</label>' +
        '      <p class="form-control-static">' + (propertyDisplayValue || '-') + '</p>' +
        '      <select class="form-control" style="display:none" id="trafficSigns_type">  ' +
        subTypesTrafficSigns +
        '      </select></div>';
    };

    var singleChoiceHandler = function (property, collection) {
      var propertyValue = (property.values.length === 0) ? '' : _.head(property.values).propertyValue;
      var signTypes = _.map(_.filter(me.enumeratedPropertyValues, function(enumerated) { return enumerated.publicId == 'trafficSigns_type' ; }), function(val) {return val.values; });
      var groups =  collection.getGroup(signTypes);
      var groupKeys = Object.keys(groups);
      var mainTypeDefaultValue = _.indexOf(_.map(groups, function (group) {return _.some(group, function(val) {return val.propertyValue == propertyValue;});}), true);
      var counter = 0;
      var mainTypesTrafficSigns = _.map(groupKeys, function (label) {
        return $('<option>',
          { selected: counter === mainTypeDefaultValue,
            value: counter++,
            text: label}
        )[0].outerHTML; }).join('');
      return '' +
        '    <div class="form-group editable form-traffic-sign">' +
        '      <label class="control-label">' + property.localizedName + '</label>' +
        '      <p class="form-control-static">' + (groupKeys[mainTypeDefaultValue] || '-') + '</p>' +
        '      <select class="form-control" style="display:none" id=main-' + property.publicId +'>' +
        mainTypesTrafficSigns +
        '      </select>' +
        '    </div>' +
        singleChoiceSubType( collection, mainTypeDefaultValue, property );
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