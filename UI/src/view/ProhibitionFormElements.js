(function(root) {
  root.ProhibitionFormElements = function(unit, editControlLabels, className, defaultValue, elementType, possibleValues) {
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
    var dayLabels = {
      Weekday: "Ma–Pe",
      Saturday: "La",
      Sunday: "Su"
    };
    var exceptionLabel = '<label>Rajoitus ei koske seuraavia ajoneuvoja:</label>';

    return {
      singleValueElement: singleValueElement,
      bindEvents: bindEvents
    };

    function singleValueElement(asset, sideCode) {
      return '' +
        '<div class="form-group editable ' + className + '">' +
          assetDisplayElement(asset) +
          assetEditElement(asset, sideCode) +
        '</div>';
    }

    function assetDisplayElement(prohibitions) {
      var items = _.map(prohibitions, function(prohibition) {
        return '<li>' + prohibitionDisplayElement(prohibition) + '</li>';
      }).join('');
      return '<ul>' + items + '</ul>';
    }

    function assetEditElement(prohibitions, sideCode) {
      var items = _.map(prohibitions, function(prohibition) {
        return '<li>' + prohibitionEditElement(prohibition) + '</li>';
      }).join('');
      return '' +
        '<ul class="' + generateClassName(sideCode) + '">' +
        items +
        '<li>' +
        newProhibitionElement() +
        '</li>' +
        '</ul>';
    }

    function generateClassName(sideCode) {
      return sideCode ? 'prohibition' + '-' + sideCode : 'prohibition';
    }

    function prohibitionDisplayElement(prohibition) {
      var typeElement = '<span>' + prohibitionValues[prohibition.typeId] + '</span>';

      function exceptionElement() {
        var exceptionElements = _.map(prohibition.exceptions, function (exceptionId) {
          return '<li>' + exceptionValues[exceptionId] + '</li>';
        }).join('');
        var element = '' +
          '<div>' +
          exceptionLabel +
          '  <ul>' + exceptionElements + '</ul>' +
          '</div>';
        return _.isEmpty(prohibition.exceptions) ?  '' : element;
      }

      function validityPeriodElement() {
        var validityPeriodItems = _.map(prohibition.validityPeriods, function (period) {
          var dayName = dayLabels[period.days];
          return '<li>' + dayName + ' ' + period.startHour + '–' + period.endHour + '</li>';
        }).join('');
        return '<ul>' + validityPeriodItems + '</ul>';
      }

      return '' +
        '<div class="form-control-static">' +
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
        var optionTags = _.map(prohibitionValues, function(name, key) {
          var selected = prohibition.typeId.toString() === key ? 'selected' : '';
          return '<option value="' + key + '"' + ' ' + selected + '>' + name + '</option>';
        });
        return '' +
          '<div class="form-group existing-prohibition">' +
          '<select class="form-control select">' +
          optionTags +
          '</select>' +
          '</div>';
      }

      function exceptionsElement() {
        function existingExceptionElements() {
          var items = _(prohibition.exceptions).map(function(exception) {
            return '' +
              '<div class="form-group existing-exception">' +
              deleteButton() +
              '  <select class="form-control select">' +
              exceptionOptions(exception) +
              '  </select>' +
              '</div>';
          });
          return items.join('');
        }

        return '' +
          '<div class="exception-group">' +
          exceptionLabel +
          existingExceptionElements() +
          newExceptionElement() +
          '</div>';
      }

      function validityPeriodsElement() {
        var existingValidityPeriodElements = _(prohibition.validityPeriods).map(validityPeriodElement).join('');

        return '' +
          '<div class="validity-period-group">' +
          existingValidityPeriodElements +
          newValidityPeriodElement() +
          '</div>';
      }

      return '' +
        '<div class="form-group edit-control-group">' +
        deleteButton() +
        typeElement() +
        validityPeriodsElement() +
        exceptionsElement() +
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
      });
    }

    function hourElement(selectedHour, type) {
      var className = type + '-hour';
      return '' +
        '<select class="form-control select ' + className + '">' +
        hourOptions(selectedHour, type) +
        '</select>';
    }

    function validityPeriodElement(period) {
      return '' +
        '<div class="form-group existing-validity-period" data-days="' + period.days + '">' +
        deleteButton() +
        validityPeriodLabel(period) +
        hourElement(period.startHour, 'start') +
        hourElement(period.endHour, 'end') +
        '</div>';
    }

    function exceptionOptions(exception) {
      var elements = _.map(exceptionValues, function(name, key) {
        var selected = exception && (exception.toString() === key) ? 'selected' : '';
        return '' +
          '<option value="' + key + '" ' + selected + '>' +
          name +
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

    function newExceptionElement() {
      return '' +
        '<div class="form-group edit-control-group new-exception">' +
        '  <select class="form-control select">' +
        '    <option class="empty" disabled selected>Lisää poikkeus</option>' +
        exceptionOptions() +
        '  </select>' +
        '</div>';
    }

    function newValidityPeriodElement() {
      return '' +
        '<div class="form-group edit-control-group new-validity-period">' +
        '  <select class="form-control select">' +
        '    <option class="empty" disabled selected>Lisää aikarajoitus</option>' +
        validityPeriodOptions() +
        '  </select>' +
        '</div>';
    }

    function newProhibitionElement() {
      var optionTags = _.map(prohibitionValues, function(name, key) {
        return '<option value="' + key + '">' + name + '</option>';
      });
      return '' +
        '<div class="form-group edit-control-group new-prohibition">' +
        '  <select class="form-control select">' +
        '    <option class="empty" disabled selected>Lisää kielto</option>' +
        optionTags +
        '  </select>' +
        '</div>';
    }

    function bindEvents(rootElement, selectedLinearAsset, sideCode) {
      $(rootElement).on(
        'change',
        '.existing-exception select, .existing-validity-period select, .existing-prohibition select',
        function() {
          selectedLinearAsset.setValue(extractValue(rootElement));
        }
      );

      $(rootElement).on('change', '.new-exception select', function(evt) {
        $(evt.target).parent().removeClass('new-exception').addClass('existing-exception');
        $(evt.target).before(deleteButton());
        $(evt.target).closest('.exception-group').append(newExceptionElement());
        selectedLinearAsset.setValue(extractValue(rootElement));
      });

      $(rootElement).on('change', '.new-validity-period select', function(evt) {
        $(evt.target).closest('.validity-period-group').append(newValidityPeriodElement());
        $(evt.target).parent().replaceWith(validityPeriodElement({
          days: $(evt.target).val(),
          startHour: 0,
          endHour: 24
        }));
        selectedLinearAsset.setValue(extractValue(rootElement));
      });

      $(rootElement).on('change', '.new-prohibition select', function(evt) {
        $(evt.target).parent().removeClass('new-prohibition').addClass('existing-prohibition');
        $(evt.target).before(deleteButton());
        $(rootElement).find('.form-group.' + className).append(newProhibitionElement());
        selectedLinearAsset.setValue(extractValue(rootElement));
      });

      $(rootElement).on('click', 'button.delete', function(evt) {
        $(evt.target).parent().remove();
        selectedLinearAsset.setValue(extractValue(rootElement));
      });
    }

    function extractValue(rootElement) {
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

      var prohibitionElements = $(rootElement).find('.prohibition');

      return _.map(prohibitionElements, function(element) {
        var $element = $(element);
        return {
          typeId: parseInt($element.find('.existing-prohibition select').val(), 10),
          exceptions: extractExceptions($element),
          validityPeriods: extractValidityPeriods($element)
        };
      });
    }
  };
})(this);
