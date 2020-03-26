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

    this.renderValueElement = function(asset, collection, authorizationPolicy) {
      var allTrafficSignProperties = asset.propertyData;
      var trafficSignSortedProperties = sortAndFilterTrafficSignProperties(allTrafficSignProperties);

      var components = _.reduce(_.map(trafficSignSortedProperties, function (feature) {
        feature.localizedName = window.localizedStrings[feature.publicId];
        var propertyType = feature.propertyType;

        switch (propertyType) {
          case "text": return textHandler(feature);
          case "number": return textHandler(feature);
          case "single_choice": return feature.publicId === 'trafficSigns_type' ? singleChoiceTrafficSignTypeHandler(feature, collection) : singleChoiceHandler(feature);
          case "read_only_number": return readOnlyHandler(feature);
          case "date": return dateHandler(feature);
          case "checkbox": return feature.publicId === 'suggest_box' ? suggestedBoxHandler (feature, authorizationPolicy) : checkboxHandler(feature);
        }

      }), function(prev, curr) { return prev + curr; }, '');

      var additionalPanels = getProperties(asset.propertyData, "additional_panel");
      var checked = _.isEmpty(additionalPanels.values) ? '' : 'checked';
      var renderedPanels = checked ? renderAdditionalPanels(additionalPanels, collection) : '';

      function getSidePlacement() {
        return _.head(getProperties(asset.propertyData, "opposite_side_sign").values);
      }

      var panelCheckbox =
        '    <div class="form-group editable edit-only form-traffic-sign-panel additional-panel-checkbox">' +
        '      <div class="checkbox" >' +
        '        <input id="additional-panel-checkbox" type="checkbox" ' + checked + '>' +
        '      </div>' +
        '        <label class="traffic-panel-checkbox-label">Linkitä lisäkilpiä</label>' +
        '    </div>';

      var wrongSideInfo = asset.id !== 0 && !_.isEmpty(getSidePlacement().propertyValue) ?
        '    <div id="wrongSideInfo" class="form-group form-directional-traffic-sign">' +
        '        <label class="control-label">' + 'Liikenteenvastainen' + '</label>' +
        '        <p class="form-control-static">' + getSidePlacement().propertyDisplayValue + '</p>' +
        '    </div>' : '';

      return components + wrongSideInfo + panelCheckbox + renderedPanels;
    };

    this.renderAssetFormElements = function(selectedAsset, localizedTexts, collection, authorizationPolicy) {

      var asset = selectedAsset.get();
      var wrapper = $('<div class="wrapper">');
      var formRootElement = $('<div class="form form-horizontal form-dark form-pointasset">');


      if (selectedAsset.isNew()) {
        formRootElement = formRootElement.append(me.renderValueElement(asset, collection, authorizationPolicy));

        return  wrapper.append(formRootElement );

      } else {
        var deleteCheckbox = $(''+
            '    <div class="form-group form-group delete">' +
            '      <div class="checkbox" >' +
            '        <input id="delete-checkbox" type="checkbox">' +
            '      </div>' +
            '      <p class="form-control-static">Poista</p>' +
            '    </div>' +
            '  </div>' );
        var logInfoGroup = $( '' +
        '    <div class="form-group">' +
        '      <p class="form-control-static asset-log-info">Lis&auml;tty j&auml;rjestelm&auml;&auml;n: ' + this.informationLog(asset.createdAt, asset.createdBy) + '</p>' +
        '    </div>' +
        '    <div class="form-group">' +
        '      <p class="form-control-static asset-log-info">Muokattu viimeksi: ' + this.informationLog(asset.modifiedAt, asset.modifiedBy) + '</p>' +
        '    </div>');

        formRootElement = formRootElement.append($(this.renderFloatingNotification(asset.floating, localizedTexts)))
                                          .append(logInfoGroup)
                                          .append( $(this.userInformationLog(authorizationPolicy, selectedAsset)))
                                          .append(me.renderValueElement(asset, collection, authorizationPolicy))
                                          .append(deleteCheckbox);

        return wrapper.append(formRootElement );
      }
    };

    var suggestedBoxHandler = function(asset, authorizationPolicy) {
      var suggestedBoxValue = getSuggestedBoxValue();
      var suggestedBoxDisabledState = getSuggestedBoxDisabledState();

      if(suggestedBoxDisabledState) {
        var disabledValue = 'disabled';
        return renderSuggestBoxElement(asset, disabledValue);
      } else if(me.pointAsset.isSuggestedAsset && authorizationPolicy.handleSuggestedAsset(me.selectedAsset, suggestedBoxValue)) {
        var checkedValue = suggestedBoxValue ? 'checked' : '';
        return renderSuggestBoxElement(asset, checkedValue);
      } else {
        return '';
      }
    };

    var getSuggestedBoxDisabledState = function() {
      return $('.suggested-checkbox').is(':disabled');
    };

    var getSuggestedBoxValue = function() {
      return !!parseInt(me.selectedAsset.getByProperty("suggest_box"));
    };

    this.switchSuggestedValue = function(disabledValue) {
      $('.suggested-checkbox').attr('disabled', disabledValue);
    };

    this.renderForm = function(rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection) {
      var id = selectedAsset.getId();

      var title = selectedAsset.isNew() ? "Uusi " + localizedTexts.newAssetLabel : 'ID: ' + id;
      var header = '<span>' + title + '</span>';
      var form = me.renderAssetFormElements(selectedAsset, localizedTexts, collection, authorizationPolicy);
      var footer = me.renderButtons();

      rootElement.find("#feature-attributes-header").html(header);
      rootElement.find("#feature-attributes-form").html(form);
      dateutil.addTwoDependentDatePickers($('#trafficSign_start_date'),  $('#trafficSign_end_date'));
      rootElement.find("#feature-attributes-form").prepend(me.renderPreview(roadCollection, selectedAsset));
      rootElement.find("#feature-attributes-footer").html(footer);

      rootElement.find('#delete-checkbox').on('change', function (event) {
        var eventTarget = $(event.currentTarget);
        selectedAsset.set({toBeDeleted: eventTarget.prop('checked')});
      });

      rootElement.find('input[type=checkbox]').not('.suggested-checkbox').on('change', function (event) {
        var eventTarget = $(event.currentTarget);
        var propertyPublicId = eventTarget.attr('id');
        var propertyValue = +eventTarget.prop('checked');
        selectedAsset.setPropertyByPublicId(propertyPublicId, propertyValue);
      });

      rootElement.find('.suggested-checkbox').on('change', function (event) {
        var eventTarget = $(event.currentTarget);
        selectedAsset.setPropertyByPublicId(eventTarget.attr('id'), +eventTarget.prop('checked'));

        if(id) {
          me.switchSuggestedValue(true);
          rootElement.find('.suggested-checkbox').prop('checked', false);
        }
      });

      rootElement.find('.editable').not('.suggestion-box').on('change', function() {
        if(id) {
          me.switchSuggestedValue(true);
          rootElement.find('.suggested-checkbox').prop('checked', false);
          selectedAsset.setPropertyByPublicId($('.suggested-checkbox').attr('id'), 0);
        }
      });

      rootElement.find('.pointasset button.save').on('click', function() {
        selectedAsset.save();
      });

      rootElement.find('.pointasset button.cancel').on('click', function() {
        me.switchSuggestedValue(false);
        selectedAsset.cancel();
      });

      this.boxEvents(rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection);
    };

    this.boxEvents = function(rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection) {
      rootElement.find('.form-traffic-sign').on('change', function() {
        selectedAsset.setPropertyByPublicId('opposite_side_sign', '0');  // force the field to be filled
      });

      rootElement.find('.form-traffic-sign input[type=text],.form-traffic-sign select#trafficSigns_type').on('change input, datechange', function (event) {
        var eventTarget = $(event.currentTarget);
        var propertyPublicId = eventTarget.attr('id');
        var propertyValue = eventTarget.val();
        selectedAsset.setPropertyByPublicId(propertyPublicId, propertyValue);
      });

      rootElement.find('.form-traffic-sign select#main-trafficSigns_type').on('change', function (event) {
        var eventTarget = $(event.currentTarget);
        $('.form-traffic-sign select#trafficSigns_type').html(singleChoiceSubType(collection, $(event.currentTarget).val()));
        selectedAsset.setPropertyByPublicId('trafficSigns_type', $('.form-traffic-sign select#trafficSigns_type').val());
      });

      var singleChoiceIds = ['location_specifier', 'structure', 'condition', 'size', 'life_cycle', 'coating_type', 'sign_material', 'lane_type', 'type_of_damage', 'urgency_of_repair'];
      _.forEach(singleChoiceIds, function (publicId) {
        bindSingleChoiceElement(publicId);
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

      function bindSingleChoiceElement (publicId) {
        rootElement.find('.form-traffic-sign select#main-' + publicId).on('change', function (event) {
          selectedAsset.setPropertyByPublicId(publicId, $('.form-traffic-sign select#main-' + publicId).val());
        });
      }

      function toggleButtonVisibility() {
        var cont = rootElement.find('.panel-group-container');
        var panels = cont.children().size();

        cont.find('.remove-panel').toggle(panels !== 1);
        cont.find('.add-panel').prop("disabled", panels === 3);
      }

      rootElement.find('button#change-validity-direction').on('click', function() {
        var previousValidityDirection = selectedAsset.get().validityDirection;
        selectedAsset.set({ validityDirection: validitydirections.switchDirection(previousValidityDirection) });
        $('.preview-div').replaceWith(me.renderPreview(roadCollection, selectedAsset));
      });

      function bindPanelEvents(){
        rootElement.find('.remove-panel').on('click', function (event) {
          removeSingle(event);
          $('.panel-group-container').replaceWith(renderAdditionalPanels({values:setAllPanels()}, collection));
          bindPanelEvents();
        });

        rootElement.find('input[type=text]#panelValue, input[type=text]#panelInfo, input[type=text]#text, .form-traffic-sign-panel select').on('change input', function (event) {
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
              panelInfo:  $(self).find('#panelInfo').val(),
              text:  $(self).find('#text').val(),
              size:  parseInt($(self).find('#size').val()),
              coating_type:  parseInt($(self).find('#coating_type').val()),
              additional_panel_color:  parseInt($(self).find('#additional_panel_color').val())
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
          panelInfo:  container.find('#panelInfo').val(),
          text:  container.find('#text').val(),
          size:  parseInt(container.find('#size').val()),
          coating_type:  parseInt(container.find('#coating_type').val()),
          additional_panel_color:  parseInt(container.find('#additional_panel_color').val())
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
        'municipality_id',
        'main_sign_text',
        'structure',
        'condition',
        'size',
        'height',
        'coating_type',
        'sign_material',
        'location_specifier',
        'terrain_coordinates_x',
        'terrain_coordinates_y',
        'lane',
        'lane_type',
        'life_cycle',
        'trafficSign_start_date',
        'trafficSign_end_date',
        'type_of_damage',
        'urgency_of_repair',
        'lifespan_left',
        'suggest_box',
        'old_traffic_code',
        'counter'
      ];

      return _.sortBy(properties, function(property) {
        return _.indexOf(propertyOrdering, property.publicId);
      }).filter(function(property){
        return _.indexOf(propertyOrdering, property.publicId) >= 0;
      });
    };

    var dateHandler = function(property) {

      var propertyValue = '';

      if ( !_.isEmpty(property.values) && !_.isEmpty(property.values[0].propertyDisplayValue) )
          propertyValue = property.values[0].propertyDisplayValue;

      return '' +
          '<div><div class="form-group editable form-traffic-sign">' +
          '        <label class="control-label">' + property.localizedName + '</label>' +
          '        <p class="form-control-static">' + (propertyValue || '–') + '</p>' +
          '        <input type="text" class="form-control" id="' + property.publicId + '" value="' + propertyValue + '">' +
          '    </div></div>';
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

    var checkboxHandler = function(property) {
      var checked = _.head(property.values).propertyValue == "0" ? '' : 'checked';
      return '' +
      '    <div id= "' + property.publicId + '-checkbox-div" class="form-group editable edit-only form-traffic-sign">' +
      '      <div class="checkbox" >' +
      '        <input id="' + property.publicId + '" type="checkbox"' + checked + '>' +
      '      </div>' +
      '        <label class="' + property.publicId + '-checkbox-label">' + property.localizedName + '</label>' +
      '    </div>';
    };

    var renderSuggestBoxElement = function(asset, state) {
      return '<div class="form-group editable form-' + me.pointAsset.layerName + ' suggestion-box">' +
          '<label class="control-label">' + asset.localizedName + '</label>' +
          '<p class="form-control-static">Kylla</p>' +
          '<input type="checkbox" class="form-control suggested-checkbox" name="' + asset.publicId + '" id="' + asset.publicId + '"' + state + '>' +
          '</div>';
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

    var singleChoiceTrafficSignTypeHandler = function (property, collection) {
      var propertyValue = (property.values.length === 0) ? '' : _.head(property.values).propertyValue;
      var signTypes = _.map(_.filter(me.enumeratedPropertyValues, { 'publicId': property.publicId}), function(val) {return val.values; });
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

    var singleChoiceHandler = function (property) {
      var propertyValue = (property.values.length === 0) ? '' : _.head(property.values).propertyValue;
      var propertyValues = _.head(_.map(_.filter(me.enumeratedPropertyValues, { 'publicId': property.publicId}), function(val) {return val.values; }));
      var propertyDefaultValue = _.indexOf(_.map(propertyValues, function (prop) {return _.some(prop, function(propValue) {return propValue == propertyValue;});}), true);
      var selectableValues = _.map(propertyValues, function (label) {
        return $('<option>',
            { selected: propertyValue == label.propertyValue,
              value: parseInt(label.propertyValue),
              text: label.propertyDisplayValue}
        )[0].outerHTML; }).join('');
      return '' +
          '    <div class="form-group editable form-traffic-sign">' +
          '      <label class="control-label">' + property.localizedName + '</label>' +
          '      <p class="form-control-static">' + (propertyValues[propertyDefaultValue].propertyDisplayValue || '-') + '</p>' +
          '      <select class="form-control" style="display:none" id=main-' + property.publicId +'>' +
          selectableValues +
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

    var sortPanelKeys = function(properties) {

      var propertyOrdering = [
        'formPosition',
        'panelType',
        'panelValue',
        'panelInfo',
        'text',
        'size',
        'coating_type',
        'additional_panel_color',
      ];

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

            switch (_.head(feature)) {
              case "formPosition": return panelLabel(index+1);
              case "panelValue":
              case "panelInfo":
              case "text": return panelTextHandler(feature);
              case "panelType": return singleChoiceForPanelTypes(feature, collection);
              case "size":
              case "coating_type":
              case "additional_panel_color": return singleChoiceForPanels(feature);
            }

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

    var singleChoiceForPanelTypes = function (property, collection) {
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

    //can't be associated with traffic signs
    var additionalPanelColorSettings = [
      { propertyValue: "1", propertyDisplayValue: "Sininen", checked: false },
      { propertyValue: "2", propertyDisplayValue: "Keltainen", checked: false },
      { propertyValue: "999", propertyDisplayValue: "Ei tietoa", checked: false }
    ];

    var singleChoiceForPanels = function (property) {
      var publicId = _.head(property);
      var propertyValue = (!_.isEmpty(_.last(property))) ? '' : _.last(property);
      var propertyValues = publicId === "additional_panel_color" ? additionalPanelColorSettings : _.head(_.map(_.filter(me.enumeratedPropertyValues, { 'publicId': publicId }), function(val) {return val.values; }));
      var propertyDefaultValue = _.indexOf(_.map(propertyValues, function (prop) {return _.some(prop, function(propValue) {return propValue == propertyValue;});}), true);
      var selectableValues = _.map(propertyValues, function (label) {
        return $('<option>',
            { selected: propertyValue == label.propertyValue,
              value: parseInt(label.propertyValue),
              text: label.propertyDisplayValue}
        )[0].outerHTML; }).join('');

      switch (publicId) {
        case "size": property.label = "KOKO"; break;
        case "coating_type": property.label = "KALVON TYYPPI"; break;
        case "additional_panel_color": property.label = "LISÄKILVEN VÄRI"; break;
      }

      return '' +
          '    <div class="form-group editable form-traffic-sign-panel">' +
          '      <label class="control-label">' + property.label + '</label>' +
          '      <p class="form-control-static">' + (propertyValues[propertyDefaultValue].propertyDisplayValue || '-') + '</p>' +
          '      <select class="form-control" style="display:none" id="' + publicId +'">' +
          selectableValues +
          '      </select>' +
          '    </div>';
    };

    var panelTextHandler = function (property) {
      var publicId = _.first(property);
      var propertyValue = _.isUndefined(_.last(property)) ? '' : _.last(property);

      switch (publicId) {
        case "panelValue": property.label = "ARVO"; break;
        case "panelInfo": property.label = "LISÄTIETO"; break;
        case "text": property.label = "TEKSTI"; break;
      }

      return '' +
        '    <div class="form-group editable form-traffic-sign-panel">' +
        '        <label class="control-label">' + property.label + '</label>' +
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

    me.renderPreview = function(roadCollection, selectedAsset) {
      var asset = selectedAsset.get();
      var lanes;
      if (!asset.floating){
        lanes = roadCollection.getRoadLinkByLinkId(asset.linkId).getData().lanes;
        lanes = validitydirections.filterLanesByDirection(lanes, asset.validityDirection);
      }
      return _.isEmpty(lanes) ? '' : createPreviewHeaderElement(_.uniq(lanes));
    };

    me.renderValidityDirection = function (selectedAsset) {
      if(selectedAsset.get().validityDirection){
        return $(
            '    <div class="form-group editable form-directional-traffic-sign edit-only">' +
            '      <label class="control-label">Vaikutussuunta</label>' +
            '      <button id="change-validity-direction" class="form-control btn btn-secondary btn-block">Vaihda suuntaa</button>' +
            '    </div>');
      }
      else return '';
    };

    var createPreviewHeaderElement = function(laneNumbers) {
      var createNumber = function (number) {
        return $('<td class="preview-lane-traffic-signs">' + number + '</td>');
      };

      var numbers = _.sortBy(laneNumbers);

      var odd = _.filter(numbers, function (number) {
        return number % 2 !== 0;
      });
      var even = _.filter(numbers, function (number) {
        return number % 2 === 0;
      });

      var preview = function () {
        var previewList = $('<table class="preview">');

        var numberHeaders = $('<tr style="font-size: 11px;">').append(_.map(_.reverse(even).concat(odd), function (number) {
          return $('<th>' + (number.toString()[1] == '1' ? 'Pääkaista' : 'Lisäkaista') + '</th>');
        }));

        var oddListElements = _.map(odd, function (number) {
          return createNumber(number);
        });

        var evenListElements = _.map(even, function (number) {
          return createNumber(number);
        });

        return $('<div class="preview-div">').append(previewList.append(numberHeaders).append($('<tr>').append(evenListElements).append(oddListElements))).append('<hr class="form-break">');
      };

      return preview();
    };
  };
})(this);