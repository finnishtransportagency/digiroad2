(function(root) {
  root.TrafficSignForm = function() {
    PointAssetForm.call(this);
    var me = this;
    var defaultAdditionalPanelValue = null;

    this.initialize = function(parameters) {
      me.pointAsset = parameters.pointAsset;
      me.roadCollection = parameters.roadCollection;
      me.applicationModel = parameters.applicationModel;
      me.backend = parameters.backend;
      me.saveCondition = parameters.saveCondition;
      me.feedbackCollection = parameters.feedbackCollection;
      defaultAdditionalPanelValue = _.find(parameters.pointAsset.newAsset.propertyData, function(obj){return obj.publicId === 'additional_panel';}).defaultValue;
      me.bindEvents(parameters);
      me.selectedAsset = parameters.pointAsset.selectedPointAsset;
    };

    var getProperties = function(properties, publicId) {
      return _.find(properties, function(feature){
        return feature.publicId === publicId;
      });
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

      var additionalPanels = getProperties(asset.propertyData, "additional_panel");
      var checked = _.isEmpty(additionalPanels.values) ? '' : 'checked';
      var renderedPanels = checked ? renderAdditionalPanels(additionalPanels, collection) : '';

      var panelCheckbox =
      '    <div class="form-group editable edit-only form-traffic-sign-panel additional-panel-checkbox">' +
      '      <div class="checkbox" >' +
      '        <input id="additional-panel-checkbox" type="checkbox" ' + checked + '>' +
      '      </div>' +
      '        <label class="traffic-panel-checkbox-label">Linkitä lisäkilpiä</label>' +
      '    </div>';


      if(asset.validityDirection)
        return components +
          '    <div class="form-group editable form-directional-traffic-sign edit-only">' +
          '      <label class="control-label">Vaikutussuunta</label>' +
          '      <button id="change-validity-direction" class="form-control btn btn-secondary btn-block">Vaihda suuntaa</button>' +
          '    </div>' + panelCheckbox + renderedPanels;

      return components + panelCheckbox + renderedPanels;
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

      rootElement.find('#additional-panel-checkbox').on('change', function (event) {
        if(!$(event.currentTarget).prop('checked')) {
          $('.panel-group-container').remove();
          selectedAsset.setAdditionalPanels();
        }
        else {
          $('.additional-panel-checkbox').after(renderAdditionalPanels({values:[defaultAdditionalPanelValue]}, collection));
          setAllPanels();
        }
        bindPanelEvents();
      });

      bindPanelEvents();

      function toggleButtonVisibility() {
        var cont = rootElement.find('.panel-group-container');
        var panels = cont.children().size();

        cont.find('.remove-panel').toggle(panels !== 1);
        cont.find('.add-panel').prop("disabled", panels === 3);
      }

      function bindPanelEvents(){
        rootElement.find('.remove-panel').on('click', function (event) {
          removeSingle(event);
          $('.panel-group-container').replaceWith(renderAdditionalPanels({values:setAllPanels()}, collection));
          bindPanelEvents();
        });

        rootElement.find('input[type=text]#panelValue, input[type=text]#panelInfo, .form-traffic-sign-panel select').on('change input', function (event) {
          setSinglePanel(event);
        });

        rootElement.find('.add-panel').on('click', function (event) {
          $(event.currentTarget).parent().after(renderAdditionalPanels({values:[defaultAdditionalPanelValue]}, collection));
          $('.panel-group-container').replaceWith(renderAdditionalPanels({values:setAllPanels()}, collection));
          bindPanelEvents();
        });

        me.toggleMode(rootElement, !authorizationPolicy.formEditModeAccess(selectedAsset, me.roadCollection) || me.applicationModel.isReadOnly());

        toggleButtonVisibility();
      }

      var setAllPanels = function() {
          var allPanels = $('.single-panel-container').map(function(index){
            var self = this;
            return {
              formPosition: (index + 1),
              panelType: parseInt($(self).find('#panelType').val()),
              panelValue: $(self).find('#panelValue').val(),
              panelInfo:  $(self).find('#panelInfo').val()
            };
          });
          selectedAsset.setAdditionalPanels(allPanels.toArray());
          return allPanels;
      };

      var setSinglePanel = function(event) {
        var eventTarget = $(event.currentTarget);
        var panelId = eventTarget.parent().parent().attr('id');
        var container = $('.single-panel-container#'+panelId);
        var panel = {
          formPosition: parseInt(panelId),
          panelType: parseInt(container.find('#panelType').val()),
          panelValue: container.find('#panelValue').val(),
          panelInfo:  container.find('#panelInfo').val()
        };
        selectedAsset.setAdditionalPanel(panel);
      };

      var removeSingle = function(event) {
        $('.single-panel-container#'+ $(event.currentTarget).prop('id')).remove();
      };
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

    var sortPanelKeys = function(properties) {

      var propertyOrdering = [
        'formPosition',
        'panelType',
        'panelValue',
        'panelInfo'];

      var sorted = {};

      _.forEach(propertyOrdering, function(key){
        sorted[key] = properties[key];
      });
      return sorted;
    };

    var renderAdditionalPanels = function (property, collection) {
      var sortedByProperties = _.map(property.values, function(prop) {
        return sortPanelKeys(prop);
      });

      var sortedByFormPosition = _.sortBy(sortedByProperties, function(o){
        return o.formPosition;
      });

      var panelContainer = $('<div class="panel-group-container"></div>');

      var components = _.flatMap(sortedByFormPosition, function(panel, index) {
        var body =
          $('<div class="single-panel-container" id='+ (index + 1)+'>' +
          Object.entries(panel).map(function (feature) {
            if(_.head(feature) === "formPosition")
              return panelLabel(index+1);

            if (_.head(feature) === "panelValue")
              return panelTextHandler(feature);

            if (_.head(feature) === "panelType")
              return singleChoiceForPanels(feature, collection);

            if (_.head(feature) === "panelInfo")
              return panelTextHandler(feature);
          }).join(''));

        var buttonDiv = $('<div class="form-group editable form-traffic-sign-panel traffic-panel-buttons">' + (sortedByFormPosition.length === 1 ? '' : removeButton(index+1)) + addButton(index+1) + '</div>');

        body.filter('.single-panel-container').append(buttonDiv);

        return body;
      });
      return panelContainer.append(components)[0].outerHTML;
    };

    var removeButton = function(id) {
      return '<button class="btn edit-only btn-secondary remove-panel" id="'+id+'" >Poista lisäkilpi</button>';
    };

    var addButton = function(id) {
      return '<button class="btn edit-only editable btn-secondary add-panel" id="'+id+'" >Uusi lisäkilpi</button>';
    };

    var singleChoiceForPanels = function (property, collection) {
      var propertyValue = _.isUndefined(_.last(property))  ? '' : _.last(property);
      var signTypes = _.map(_.filter(me.enumeratedPropertyValues, function(enumerated) { return enumerated.publicId == 'trafficSigns_type' ; }), function(val) {return val.values; });
      var panels = _.find(collection.getAdditionalPanels(signTypes));
      var propertyDisplayValue = _.find(panels, function(panel){return panel.propertyValue == propertyValue.toString();}).propertyDisplayValue;

      var subTypesTrafficSigns = _.map(_.map(panels, function (group) {
        return $('<option>',
          {
            value: group.propertyValue,
            selected: propertyValue == group.propertyValue,
            text: group.propertyDisplayValue
          }
        )[0].outerHTML;
      })).join('');

      return '<div class="form-group editable form-traffic-sign-panel">' +
        '      <label class="control-label"> ALITYYPPI</label>' +
        '      <p class="form-control-static">' + (propertyDisplayValue || '-') + '</p>' +
        '      <select class="form-control" style="display:none" id="panelType">  ' +
        subTypesTrafficSigns +
        '      </select></div>';
    };

    var panelTextHandler = function (property) {
      var publicId = _.first(property);
      var propertyValue = _.isUndefined(_.last(property)) ? '' : _.last(property);
      var label = publicId == 'panelInfo' ? 'LISÄTIETO' : 'ARVO';
      return '' +
        '    <div class="form-group editable form-traffic-sign-panel">' +
        '        <label class="control-label">' + label + '</label>' +
        '        <p class="form-control-static">' + (propertyValue || '–') + '</p>' +
        '        <input type="text" class="form-control" id="' + publicId + '" value="' + propertyValue + '">' +
        '    </div>';
    };

    var panelLabel = function(index) {
      return '' +
        '    <div class="form-group editable form-traffic-sign-panel">' +
        '        <label class="traffic-panel-label">Lisäkilpi ' + index + '</label>' +
        '    </div>';
    };

  };
})(this);