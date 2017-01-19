(function(root) {
  root.PiecewiseLinearAssetFormElements = {
    WinterSpeedLimitsFormElements: WinterSpeedLimitsFormElements,
    EuropeanRoadsFormElements: TextualValueFormElements,
    ExitNumbersFormElements: TextualValueFormElements,
    CreateMaintenanceFormElements: CreateMaintenanceFormElements,
    DefaultFormElements: DefaultFormElements
  };

  function DefaultFormElements(unit, editControlLabels, className, defaultValue, possibleValues) {
    var formElem = inputFormElement(unit);
    return formElementFunctions(unit, editControlLabels, className, defaultValue, possibleValues, formElem);
  }

  function WinterSpeedLimitsFormElements(unit, editControlLabels, className, defaultValue, possibleValues) {
    var formElem = dropDownFormElement(unit);
    return formElementFunctions(unit, editControlLabels, className, defaultValue, possibleValues, formElem);
  }

  function TextualValueFormElements(unit, editControlLabels, className, defaultValue, possibleValues) {
    var formElem = textAreaFormElement();
    return formElementFunctions(unit, editControlLabels, className, defaultValue, possibleValues, formElem);
  }

  function CreateMaintenanceFormElements(unit, editControlLabels, className, defaultValue, possibleValues, accessRightsValues) {
   var formElem = maintenanceFormElement();
    return formElementFunctions(unit, editControlLabels, className, defaultValue, possibleValues, formElem, accessRightsValues);
  }

  function formElementFunctions(unit, editControlLabels, className, defaultValue, possibleValues, formElem, accessRightsValues) {
    return {
      singleValueElement:  _.partial(singleValueElement, formElem.measureInput, formElem.valueString),
      bindEvents: _.partial(bindEvents, formElem.inputElementValue)
    };

    function generateClassName(sideCode) {
      return sideCode ? className + '-' + sideCode : className;
    }

    function sideCodeMarker(sideCode) {
      if (_.isUndefined(sideCode)) {
        return '';
      } else {
        return '<span class="marker">' + sideCode + '</span>';
      }
    }

    function singleValueEditElement(currentValue, sideCode, input) {
      var withoutValue = _.isUndefined(currentValue) ? 'checked' : '';
      var withValue = _.isUndefined(currentValue) ? '' : 'checked';
      return '' +
        sideCodeMarker(sideCode) +
        '<div class="edit-control-group choice-group">' +
        '  <div class="radio">' +
        '    <label>' + editControlLabels.disabled +
        '      <input ' +
        '      class="' + generateClassName(sideCode) + '" ' +
        '      type="radio" name="' + generateClassName(sideCode) + '" ' +
        '      value="disabled" ' + withoutValue + '/>' +
        '    </label>' +
        '  </div>' +
        '  <div class="radio">' +
        '    <label>' + editControlLabels.enabled +
        '      <input ' +
        '      class="' + generateClassName(sideCode) + '" ' +
        '      type="radio" name="' + generateClassName(sideCode) + '" ' +
        '      value="enabled" ' + withValue + '/>' +
        '    </label>' +
        '  </div>' +
        input +
        '</div>';
    }

    function bindEvents(inputElementValue, rootElement, selectedLinearAsset, sideCode) {
      var inputElement = rootElement.find('.input-unit-combination .' + generateClassName(sideCode));
      var toggleElement = rootElement.find('.radio input.' + generateClassName(sideCode));
      var valueSetters = {
        a: selectedLinearAsset.setAValue,
        b: selectedLinearAsset.setBValue
      };
      var setValue = valueSetters[sideCode] || selectedLinearAsset.setValue;
      var valueRemovers = {
        a: selectedLinearAsset.removeAValue,
        b: selectedLinearAsset.removeBValue
      };
      var removeValue = valueRemovers[sideCode] || selectedLinearAsset.removeValue;

      inputElement.on('input change', function() {
        setValue(inputElementValue(inputElement));
      });

      toggleElement.on('change', function(event) {
        var disabled = $(event.currentTarget).val() === 'disabled';
        inputElement.prop('disabled', disabled);
        if (disabled) {
          removeValue();
        } else {
          var value = _.isUndefined(unit) ? defaultValue : inputElementValue(inputElement);
          setValue(value);
        }
      });
    }

    function singleValueElement(measureInput, valueString, currentValue, sideCode) {
      return '' +
        '<div class="form-group editable">' +
        '  <label class="control-label">' + editControlLabels.title + '</label>' +
        '  <p class="form-control-static ' + className + '" style="display:none;">' + valueString(currentValue).replace(/[\n\r]+/g, '<br>') + '</p>' +
        singleValueEditElement(currentValue, sideCode, measureInput(currentValue, generateClassName(sideCode), possibleValues, accessRightsValues)) +
        '</div>';
    }
  }

  function inputFormElement(unit) {
    return {
      inputElementValue: inputElementValue,
      valueString: valueString,
      measureInput: measureInput
    };

    function inputElementValue(input) {
      var removeWhitespace = function(s) {
        return s.replace(/\s/g, '');
      };
      var value = parseInt(removeWhitespace(input.val()), 10);
      return _.isFinite(value) ? value : 0;
    }

    function valueString(currentValue) {
      if (unit) {
        return currentValue ? currentValue + ' ' + unit : '-';
      } else {
        return currentValue ? 'on' : 'ei ole';
      }
    }

    function measureInput(currentValue, className) {
      if (unit) {
        var value = currentValue ? currentValue : '';
        var disabled = _.isUndefined(currentValue) ? 'disabled' : '';
        return '' +
          '<div class="input-unit-combination input-group">' +
          '  <input ' +
          '    type="text" ' +
          '    class="form-control ' + className + '" ' +
          '    value="' + value  + '" ' + disabled + ' >' +
          '  <span class="input-group-addon ' + className + '">' + unit + '</span>' +
          '</div>';
      } else {
        return '';
      }
    }
  }

  function textAreaFormElement() {
    return {
      inputElementValue: inputElementValue,
      valueString: valueString,
      measureInput: measureInput
    };

    function inputElementValue(input) {
      return _.trim(input.val());
    }

    function valueString(currentValue) {
      return currentValue || '';
    }

    function measureInput(currentValue, className) {
      var value = currentValue ? currentValue : '';
      var disabled = _.isUndefined(currentValue) ? 'disabled' : '';
      return '' +
        '<div class="input-unit-combination input-group">' +
        '  <textarea class="form-control large-input ' + className + '" ' +
          disabled + '>' +
        value  + '</textarea>' +
          '</div>';
    }
  }

  function dropDownFormElement(unit) {
    var template =  _.template(
     '<div class="input-unit-combination">' +
      '  <select <%- disabled %> class="form-control <%- className %>" ><%= optionTags %></select>' +
      '</div>');

    return {
      inputElementValue: inputElementValue,
      valueString: valueString,
      measureInput: measureInput
    };

    function valueString(currentValue) {
      return currentValue ? currentValue + ' ' + unit : '-';
    }

    function measureInput(currentValue, className, possibleValues) {
      var optionTags = _.map(possibleValues, function(value) {
        var selected = value === currentValue ? " selected" : "";
        return '<option value="' + value + '"' + selected + '>' + value + ' ' + unit + '</option>';
      }).join('');
      var value = currentValue ? currentValue : '';
      var disabled = _.isUndefined(currentValue) ? 'disabled' : '';


      return template({className: className, optionTags: optionTags, disabled: disabled});
    }

    function inputElementValue(input) {
      return parseInt(input.val(), 10);
    }
  }

  function maintenanceFormElement() {
    var template =  _.template(
        '<li>' +
        '<label><%= label %> </label> ' +
        '  <select <%- disabled %> class="form-control <%- className %>" id="<%= id %>"><%= optionTags %></select>' +
        '</li>');

    return {
      inputElementValue: inputElementValue,
      valueString: valueString,
      measureInput: measureInput
    };

    function valueString(currentValue) {
      return currentValue ? currentValue : '-';
    }

    function measureInput(currentValue, className, possibleValues, accessRightsValues) {
      var value = currentValue ? currentValue : '';
      var disabled = _.isUndefined(currentValue) ? 'disabled' : '';

      var accessRightsTag = _.map(accessRightsValues, function (value) {
        var selected = value.typeId === currentValue ? " selected" : "";
        return '<option value="' + value.typeId + '"' + selected + '>' + value.title + '</option>';
      }).join('');

      var maintenanceResponsibilityTag = _.map(possibleValues, function (value) {
        var selected = value.typeId === currentValue ? " selected" : "";
        return '<option value="' + value.typeId + '"' + selected + '>' + value.title + '</option>';
      }).join('');

      var textValuePropertyNames = [{'name': "Tiehoitokunta", 'id': "huoltotie_tiehoitokunta" },
                                    {'name': "Nimi", 'id': "huoltotie_nimi" },
                                    {'name': "Osoite" , 'id': "huoltotie_osoite"},
                                    {'name': "Postinumero", 'id': "huoltotie_postinumero"},
                                    {'name': "Postitoimipaikka", 'id': "huoltotie_Postitoimipaikka"},
                                    {'name': "Puhelin 1", 'id': "huoltotie_Puhelin1"},
                                    {'name': "Puhelin 2", 'id': "huoltotie_Puhelin2"},
                                    {'name': "Lisätietoa", 'id': "huoltotie_Lisätietoa"}];

        var textBoxValues = _.map(textValuePropertyNames, function (prop) {
       // return  '<div class=" form-group input-group">' +
          return '<label>' + prop.name + '</label>' +
                 '<div class="form-group">' +
                 '<input ' +
                 '    type="text" ' +
                 '    class="form-control ' + className + '" id="'+prop.id+'"' +
                 '    value="' + value + '" ' + disabled + ' >' +
                 '</div>';//</div>';
      }).join('');

      var template1 = template({className: className, optionTags: accessRightsTag, disabled: disabled, label: 'Käyttöoikeus', id: 'huoltotie_kayttooikeus'});
      var template2 = template({className: className, optionTags: maintenanceResponsibilityTag, disabled: disabled, label: 'Huoltovastuu', id: 'huoltotie_huoltovastuu'});

      return '<form class="input-unit-combination"><ul>'+template1.concat(template2)+'</ul>'+textBoxValues+'</form>';
    }

      function inputElementValue(input) {
          var things = _.map(input, function (ele) {
              var mapping = {"SELECT" : "single_choice", "INPUT": "text"};
              var obj = {
                  'publicId': ele.id,
                  'value': ele.value,
                  'required': true,
                  'propertyType': mapping[String(ele.tagName)]
              };
              return obj;
          });

          return things;
      }
  }
})(this);
