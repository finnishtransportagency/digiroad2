(function(root) {

  root.PiecewiseLinearAssetFormElements = function PiecewiseLinearAssetFormElements(unit, editControlLabels, className, defaultValue, elementType, possibleValues) {
    var formElem;
    switch(elementType) {
      case 'dropdown':
        formElem = dropDownFormElement(unit);
        break;
      case 'prohibition':
        return prohibitionFormElements();
      default:
        formElem = inputFormElement(unit);
    }
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
        '<div class="choice-group">' +
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
          var value = unit ? inputElementValue(inputElement) : defaultValue;
          setValue(value);
        }
      });
    }

    function singleValueElement(measureInput, valueString, currentValue, sideCode) {
      return '' +
        '<div class="form-group editable">' +
        '  <label class="control-label">' + editControlLabels.title + '</label>' +
        '  <p class="form-control-static ' + className + '" style="display:none;">' + valueString(currentValue) + '</p>' +
        singleValueEditElement(currentValue, sideCode, measureInput(currentValue, generateClassName(sideCode), possibleValues)) +
        '</div>';
    }
  };

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

  function prohibitionFormElements() {
    var prohibitionValues = {
      3: 'Ajoneuvo',
      2: 'Moottoriajoneuvo',
      23: 'Läpiajo',
      12: 'Jalankulku',
      11: 'Polkupyörä',
      26: 'Ratsastus',
      10: 'Mopo',
      9: 'Moottoripyörä',
      27: 'Moottorikelkka',
      5: 'Linja-auto',
      8: 'Taksi',
      7: 'Henkilöauto',
      6: 'Pakettiauto',
      4: 'Kuorma-auto',
      15: 'Matkailuajoneuvo',
      19: 'Sotilasajoneuvo',
      13: 'Ajoneuvoyhdistelmä',
      14: 'Traktori tai maatalousajoneuvo',
      24: 'Ryhmän A vaarallisten aineiden kuljetus',
      25: 'Ryhmän B vaarallisten aineiden kuljetus'
    };
    var exceptionValues = {
      21: 'Huoltoajo',
      22: 'Tontille ajo',
      10: 'Mopo',
      9: 'Moottoripyörä',
      27: 'Moottorikelkka',
      5: 'Linja-auto',
      8: 'Taksi',
      7: 'Henkilöauto',
      6: 'Pakettiauto',
      4: 'Kuorma-auto',
      15: 'Matkailuajoneuvo',
      19: 'Sotilasajoneuvo',
      13: 'Ajoneuvoyhdistelmä',
      14: 'Traktori tai maatalousajoneuvo',
      24: 'Ryhmän A vaarallisten aineiden kuljetus',
      25: 'Ryhmän B vaarallisten aineiden kuljetus'
    };

    return {
      singleValueElement: singleValueElement,
      bindEvents: function() {}
    };

    function singleValueElement(currentValue) {
      return '' +
        '<div class="form-group editable">' +
          valueElement(currentValue) +
        '</div>';
    }

    function valueElement(currentValue) {
      var items = _.map(currentValue, function(x) {
        return '<li>' + prohibitionElement(x) + '</li>';
      });
      return currentValue ? '<ul>' + items.join('') + '</ul>' : '-';
    }

    function prohibitionElement(prohibition) {
      var exceptionElements = _.map(prohibition.exceptions, function(exceptionId) {
        return '<li>' + exceptionValues[exceptionId] + '</li>';
      }).join('');
      var exceptions = '<div>Rajoitus ei koske seuraavia ajoneuvoja: <ul>' + exceptionElements + '</ul></div>';
      var typeElement = '<span>' + prohibitionValues[prohibition.typeId] + '</span>';
      var validityPeriodItems = _.map(prohibition.validityPeriods, function(period) {
        var dayName;
        if (period.days === "Saturday") {
          dayName = "La ";
        } else if (period.days === "Sunday") {
          dayName = "Su ";
        } else {
          dayName = "Ma - Pe ";
        }
        return '<li>' + dayName + period.startHour + ' - ' + period.endHour + '</li>';
      }).join('');
      var validityPeriodElement = '<ul>' + validityPeriodItems + '</ul>';
      return typeElement + validityPeriodElement + exceptions;
    }
  }
})(this);
