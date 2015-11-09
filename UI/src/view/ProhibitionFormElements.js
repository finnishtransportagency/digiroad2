(function(root) {
  root.ProhibitionFormElements = function(unit, editControlLabels, className) {
    var prohibitionValues = [
      { typeId: 3, title: 'Ajoneuvo' },
      { typeId: 2, title: 'Moottoriajoneuvo' },
      { typeId: 23, title: 'Läpiajo' },
      { typeId: 12, title: 'Jalankulku' },
      { typeId: 11, title: 'Polkupyörä' },
      { typeId: 26, title: 'Ratsastus' },
      { typeId: 10, title: 'Mopo' },
      { typeId: 9, title: 'Moottoripyörä' },
      { typeId: 27, title: 'Moottorikelkka' },
      { typeId: 5, title: 'Linja-auto' },
      { typeId: 8, title: 'Taksi' },
      { typeId: 7, title: 'Henkilöauto' },
      { typeId: 6, title: 'Pakettiauto' },
      { typeId: 4, title: 'Kuorma-auto' },
      { typeId: 15, title: 'Matkailuajoneuvo' },
      { typeId: 19, title: 'Sotilasajoneuvo' },
      { typeId: 13, title: 'Ajoneuvoyhdistelmä' },
      { typeId: 14, title: 'Traktori tai maatalousajoneuvo' },
      { typeId: 24, title: 'Ryhmän A vaarallisten aineiden kuljetus' },
      { typeId: 25, title: 'Ryhmän B vaarallisten aineiden kuljetus' }
    ];
    var exceptionValues = [
      { typeId: 21, title: 'Huoltoajo' },
      { typeId: 22, title: 'Tontille ajo' },
      { typeId: 10, title: 'Mopo' },
      { typeId: 9, title: 'Moottoripyörä' },
      { typeId: 27, title: 'Moottorikelkka' },
      { typeId: 5, title: 'Linja-auto' },
      { typeId: 8, title: 'Taksi' },
      { typeId: 7, title: 'Henkilöauto' },
      { typeId: 6, title: 'Pakettiauto' },
      { typeId: 4, title: 'Kuorma-auto' },
      { typeId: 15, title: 'Matkailuajoneuvo' },
      { typeId: 19, title: 'Sotilasajoneuvo' },
      { typeId: 13, title: 'Ajoneuvoyhdistelmä' },
      { typeId: 14, title: 'Traktori tai maatalousajoneuvo' },
      { typeId: 24, title: 'Ryhmän A vaarallisten aineiden kuljetus' },
      { typeId: 25, title: 'Ryhmän B vaarallisten aineiden kuljetus' }
    ];
    var dayLabels = {
      Weekday: "Ma–Pe",
      Saturday: "La",
      Sunday: "Su"
    };

    return {
      singleValueElement: singleValueElement,
      bindEvents: bindEvents
    };

    function exceptionLabel(prohibition) {
      var style = hasOrSupportsExceptions(prohibition) ? '' : ' style="display:none"';
      return '<label' + style + '>Rajoitus ei koske seuraavia ajoneuvoja:</label>';
    }

    function singleValueElement(asset, sideCode) {
      return '' +
        '<div class="form-group editable ' + generateClassName(sideCode) + '">' +
          '<label class="asset-label">Ajoneuvokohtaiset rajoitukset</label>' +
          sideCodeMarker(sideCode) +
          assetDisplayElement(asset) +
          assetEditElement(asset) +
        '</div>';
    }

    function assetDisplayElement(prohibitions) {
      var items = _.map(prohibitions, function(prohibition) {
        return '<li>' + prohibitionDisplayElement(prohibition) + '</li>';
      }).join('');
      return '<ul>' + items + '</ul>';
    }

    function assetEditElement(prohibitions) {
      var items = _.map(prohibitions, function(prohibition) {
        return '<li>' + prohibitionEditElement(prohibition) + '</li>';
      }).join('');
      return '' +
        '<ul class="edit-control-group">' +
        items +
        newProhibitionElement() +
        '</ul>';
    }

    function sideCodeMarker(sideCode) {
      if (_.isUndefined(sideCode)) {
        return '';
      } else {
        return '<span class="marker">' + sideCode + '</span>';
      }
    }

    function generateClassName(sideCode) {
      return sideCode ? className + '-' + sideCode : className;
    }

    function prohibitionDisplayElement(prohibition) {
      var typeElement = '<h4>Rajoitus: ' + _.find(prohibitionValues, { typeId: prohibition.typeId }).title + '</h4>';

      function exceptionElement() {
        return _.isEmpty(prohibition.exceptions) ?  '' : createExceptionElement();

        function createExceptionElement(){
          var exceptionElements = _.map(prohibition.exceptions, function (exceptionId) {
            return '<li>' + _.find(exceptionValues, { typeId: exceptionId }).title + '</li>';
          }).join('');
          return '' +
            '<div class="exception-group">' +
            exceptionLabel(prohibition) +
            '  <ul>' + exceptionElements + '</ul>' +
            '</div>';
        }
      }

      function validityPeriodElement() {
        return _.isEmpty(prohibition.validityPeriods) ? '' : createValidityPeriodElement();

        function createValidityPeriodElement() {
          var validityPeriodItems = _.map(prohibition.validityPeriods, function (period) {
            var dayName = dayLabels[period.days];
            return '<li>' + dayName + ' ' + period.startHour + '–' + period.endHour + '</li>';
          }).join('');
          return '' +
            '<div class="validity-period-group">' +
            '<label>Rajoituksen voimassaoloaika (lisäkilvessä):</label>' +
            '<ul>' + validityPeriodItems + '</ul>' +
            '</div>';
        }
      }

      return '' +
        '<div class="form-control-static existing-prohibition">' +
        typeElement +
        validityPeriodElement() +
        exceptionElement() +
        '</div>';
    }

    function deleteButton() {
      return '<button class="delete btn-delete">x</button>';
    }

    function prohibitionEditElement(prohibition) {
      function typeElement() {
        var optionTags = _.map(prohibitionValues, function(prohibitionValue) {
          var selected = prohibition.typeId === prohibitionValue.typeId ? 'selected' : '';
          return '<option value="' + prohibitionValue.typeId + '"' + ' ' + selected + '>' + prohibitionValue.title + '</option>';
        }).join('');
        return '' +
          '<div class="form-group prohibition-type">' +
          '<select class="form-control select">' +
          optionTags +
          '</select>' +
          '</div>';
      }

      function exceptionsElement(prohibition) {
        function existingExceptionElements() {
          var items = _(prohibition.exceptions).map(function(exception) {
            return '' +
              '<li><div class="form-group existing-exception">' +
              deleteButton() +
              '  <select class="form-control select">' +
              exceptionOptions(exception) +
              '  </select>' +
              '</div></li>';
          });
          return items.join('');
        }

        return '' +
          '<div class="exception-group">' +
          exceptionLabel(prohibition) +
          '<ul>' +
          existingExceptionElements() +
          newExceptionElement(prohibition.typeId) +
          '</ul>' +
          '</div>';
      }

      function validityPeriodsElement() {
        var existingValidityPeriodElements = _(prohibition.validityPeriods).map(validityPeriodElement).join('');

        return '' +
          '<div class="validity-period-group">' +
          '<label>Rajoituksen voimassaoloaika (lisäkilvessä):</label>' +
          '<ul>' +
          existingValidityPeriodElements +
          newValidityPeriodElement() +
          '</ul>' +
          '</div>';
      }

      return '' +
        '<div class="form-group existing-prohibition">' +
        deleteButton() +
        typeElement() +
        validityPeriodsElement() +
        exceptionsElement(prohibition) +
        '</div>';
    }

    function validityPeriodLabel(period) {
      return '' +
        '<label class="control-label">' +
        dayLabels[period.days] +
        '</label>';
    }

    function hourOptions(selectedOption, type) {
      var range = type === 'start' ? _.range(0, 24) : _.range(1, 25);
      return _.map(range, function(hour) {
        var selected = hour === selectedOption ? 'selected' : '';
        return '<option value="' + hour + '" ' + selected + '>' + hour + '</option>';
      }).join('');
    }

    function hourElement(selectedHour, type) {
      var className = type + '-hour';
      return '' +
        '<select class="form-control sub-control select ' + className + '">' +
        hourOptions(selectedHour, type) +
        '</select>';
    }

    function validityPeriodElement(period) {
      return '' +
        '<li><div class="form-group existing-validity-period" data-days="' + period.days + '">' +
        deleteButton() +
        validityPeriodLabel(period) +
        hourElement(period.startHour, 'start') +
        '<label class="hour-separator"> - </label>' +
        hourElement(period.endHour, 'end') +
        '</div></li>';
    }

    function exceptionOptions(exception) {
      var elements = _.map(exceptionValues, function(exceptionValue) {
        var selected = exception && (exception === exceptionValue.typeId) ? 'selected' : '';
        return '' +
          '<option value="' + exceptionValue.typeId + '" ' + selected + '>' +
          exceptionValue.title +
          '</option>';
      });
      return elements.join('');
    }

    function validityPeriodOptions() {
      return '' +
          '<option value="Weekday">Ma–Pe</option>' +
          '<option value="Saturday">La</option>' +
          '<option value="Sunday">Su</option>';
    }

    function newExceptionElement(prohibitionType) {
      var style = supportsExceptions(prohibitionType) ? '' : ' style="display: none;"';
      return '' +
        '<li><div class="form-group new-exception"' + style + '>' +
        '  <select class="form-control select">' +
        '    <option class="empty" disabled selected>Lisää poikkeus</option>' +
        exceptionOptions() +
        '  </select>' +
        '</div></li>';
    }

    function supportsExceptions(prohibitionType) {
      return _.contains([2,3,23], prohibitionType);
    }

    function hasOrSupportsExceptions(prohibition) {
      return supportsExceptions(prohibition.typeId) || !_.isEmpty(prohibition.exceptions);
    }

    function newValidityPeriodElement() {
      return '' +
        '<li><div class="form-group new-validity-period">' +
        '  <select class="form-control select">' +
        '    <option class="empty" disabled selected>Lisää voimassaoloaika</option>' +
        validityPeriodOptions() +
        '  </select>' +
        '</div></li>';
    }

    function newProhibitionElement() {
      var optionTags = _.map(prohibitionValues, function(prohibitionValue) {
        return '<option value="' + prohibitionValue.typeId + '">' + prohibitionValue.title + '</option>';
      }).join('');
      return '' +
        '<li><div class="form-group new-prohibition">' +
        '  <select class="form-control select">' +
        '    <option class="empty" disabled selected>Lisää uusi rajoitus</option>' +
        optionTags +
        '  </select>' +
        '</div></li>';
    }

    function bindEvents(rootElement, selectedLinearAsset, sideCode) {
      var className = '.' + generateClassName(sideCode);
      var inputElements = [
        className + ' .existing-exception select',
        className + ' .existing-validity-period select'
      ].join(', ');
      var prohibitionTypeElements = className + ' .edit-control-group .existing-prohibition .prohibition-type select';
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

      $(rootElement).on('change', inputElements, function() {
        setValue(extractValue(rootElement, className));
      });

      $(rootElement).on('change', prohibitionTypeElements, function(evt) {
        var existingProhibitionElement = $(evt.target).closest('.existing-prohibition');
        toggleExceptionElements(existingProhibitionElement);
        setValue(extractValue(rootElement, className));
      });

      $(rootElement).on('change', className + ' .new-exception select', function(evt) {
        var prohibitionTypeElement = $(evt.target).closest('.existing-prohibition').find('.prohibition-type select');
        var prohibitionType = parseInt(prohibitionTypeElement.val(), 10);
        $(evt.target).parent().removeClass('new-exception').addClass('existing-exception');
        $(evt.target).before(deleteButton());
        $(evt.target).closest('.exception-group ul').append(newExceptionElement(prohibitionType));
        setValue(extractValue(rootElement, className));
      });

      $(rootElement).on('change', className + ' .new-validity-period select', function(evt) {
        $(evt.target).closest('.validity-period-group ul').append(newValidityPeriodElement());
        $(evt.target).parent().parent().replaceWith(validityPeriodElement({
          days: $(evt.target).val(),
          startHour: 0,
          endHour: 24
        }));
        setValue(extractValue(rootElement, className));
      });

      $(rootElement).on('change', className + ' .new-prohibition select', function(evt) {
        $(evt.target).parent().replaceWith(prohibitionEditElement({
          typeId: parseInt($(evt.target).val(), 10),
          exceptions: [],
          validityPeriods: []
        }));
        $(rootElement).find('.form-group' + className + ' .edit-control-group').append(newProhibitionElement());
        setValue(extractValue(rootElement, className));
      });

      $(rootElement).on('click', className + ' button.delete', function(evt) {
        var existingProhibitionElement = $(evt.target).closest('.existing-prohibition');
        $(evt.target).parent().parent().remove();
        toggleExceptionElements(existingProhibitionElement);
        var value = extractValue(rootElement, className);
        if (_.isEmpty(value)) {
          removeValue();
        } else {
          setValue(value);
        }
      });
    }

    function toggleExceptionElements(prohibitionElement) {
      var newExceptionElement = prohibitionElement.find('.new-exception');
      var exceptionGroupLabel = prohibitionElement.find('.exception-group label');
      var prohibition = extractExistingProhibition(prohibitionElement);
      newExceptionElement.toggle(supportsExceptions(prohibition.typeId));
      exceptionGroupLabel.toggle(hasOrSupportsExceptions(prohibition));
    }

    function extractValue(rootElement, className) {
      var prohibitionElements = $(rootElement).find(className).find('.edit-control-group .existing-prohibition');
      return _.map(prohibitionElements, extractExistingProhibition);
    }

    function extractExistingProhibition(element) {
      var $element = $(element);
      return {
        typeId: parseInt($element.find('.prohibition-type select').val(), 10),
        exceptions: extractExceptions($element),
        validityPeriods: extractValidityPeriods($element)
      };
    }

    function extractExceptions(element) {
      var exceptionElements = element.find('.existing-exception select');
      return _.map(exceptionElements, function(exception) {
        return parseInt($(exception).val(), 10);
      });
    }

    function extractValidityPeriods(element) {
      var periodElements = element.find('.existing-validity-period');
      return _.map(periodElements, function(element) {
        return {
          startHour: parseInt($(element).find('.start-hour').val(), 10),
          endHour: parseInt($(element).find('.end-hour').val(), 10),
          days: $(element).data('days')
        };
      });
    }
  };
})(this);
